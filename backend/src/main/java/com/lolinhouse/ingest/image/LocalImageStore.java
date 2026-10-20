package com.lolinhouse.ingest.image;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 역할: 이미지를 로컬 디스크(app.image-store.local-dir)에 해시 이름으로 저장하는 {@link ImageStore} 구현(M4). M7에서 S3 구현으로 바꾼다.
 * 키는 "{해시 앞 2자}/{해시}.{png|jpg}"라 같은 이미지는 항상 같은 파일이다 → 이미 있으면 다시 쓰지 않고(멱등), 동시에 같은 키를 저장해도
 * 임시 파일에 쓴 뒤 옮기므로 반쯤 쓰인 파일이 보이지 않는다. 키는 정규식과 "정규화한 경로가 루트 안인가" 두 번 검사한다(경로 탈출 방지).
 * 이미지에 친구 닉네임이 있어 저장 폴더(data/)는 git 제외다.
 * 설명: docs/design/m4-design.md D7·§2.3
 */
@Component
public class LocalImageStore implements ImageStore {

    private static final Pattern HASH = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern KEY = Pattern.compile("[0-9a-f]{2}/[0-9a-f]{64}\\.(png|jpg)");

    private final Path root;

    public LocalImageStore(@Value("${app.image-store.local-dir}") String localDir) {
        this.root = Path.of(localDir).toAbsolutePath().normalize();
    }

    @Override
    public String put(String hash, String mimeType, byte[] bytes) {
        if (hash == null || !HASH.matcher(hash).matches()) {
            throw new IllegalArgumentException("해시 형식이 올바르지 않습니다.");
        }
        String extension = switch (mimeType == null ? "" : mimeType) {
            case "image/png" -> "png";
            case "image/jpeg" -> "jpg";
            default -> throw new IllegalArgumentException("지원하지 않는 이미지 형식입니다.");
        };
        String key = hash.substring(0, 2) + "/" + hash + "." + extension;
        Path target = resolve(key);
        if (Files.isRegularFile(target)) {
            return key;
        }
        Path temp = null;
        try {
            Files.createDirectories(target.getParent());
            temp = Files.createTempFile(target.getParent(), hash, ".tmp");
            Files.write(temp, bytes);
            move(temp, target);
        } catch (IOException e) {
            // 다른 요청이 같은 키를 먼저 완성했다면 내용이 같으므로 성공으로 본다.
            if (!Files.isRegularFile(target)) {
                throw new UncheckedIOException("이미지를 저장하지 못했습니다.", e);
            }
        } finally {
            deleteQuietly(temp);
        }
        return key;
    }

    @Override
    public byte[] get(String key) {
        Path path = resolve(key);
        try {
            return Files.readAllBytes(path);
        } catch (NoSuchFileException e) {
            throw new UncheckedIOException("저장된 이미지가 없습니다.", e);
        } catch (IOException e) {
            throw new UncheckedIOException("이미지를 읽지 못했습니다.", e);
        }
    }

    /** 정규식으로 형태를 막고, 정규화한 결과가 루트 밖이면 한 번 더 거절한다. */
    private Path resolve(String key) {
        if (key == null || !KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("이미지 키 형식이 올바르지 않습니다.");
        }
        Path resolved = root.resolve(key).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("이미지 키가 저장소 밖을 가리킵니다.");
        }
        return resolved;
    }

    private static void move(Path temp, Path target) throws IOException {
        try {
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, target);
        }
    }

    private static void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            // 임시 파일 정리 실패는 무시한다(다음 저장에 영향 없음).
        }
    }
}
