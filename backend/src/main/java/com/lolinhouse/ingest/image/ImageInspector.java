package com.lolinhouse.ingest.image;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Iterator;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;

import com.lolinhouse.common.web.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 역할: 업로드된 바이트가 받아도 되는 결과창 이미지인지 검사하고 SHA-256 해시를 계산한다(용량 → 시그니처 → 크기 순).
 * 크기는 픽셀을 전부 풀지 않고 헤더만 읽어 확인한다(작은 파일이 거대한 이미지로 풀리는 압축 폭탄 방지).
 * 거절은 ApiException(400/413)이며 메시지에 파일 내용·이름을 넣지 않는다.
 * 설명: docs/design/m4-design.md §2.3
 */
@Component
public class ImageInspector {

    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 0x50, 0x4E, 0x47};
    private static final byte[] JPEG_SIGNATURE = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};

    private final long maxBytes;
    private final int maxSide;

    public ImageInspector(@Value("${app.extraction.max-image-bytes:10485760}") long maxBytes,
            @Value("${app.extraction.max-image-side:4096}") int maxSide) {
        this.maxBytes = maxBytes;
        this.maxSide = maxSide;
    }

    public InspectedImage inspect(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw ApiException.badRequest("UNSUPPORTED_IMAGE", "비어 있는 파일입니다.");
        }
        if (bytes.length > maxBytes) {
            throw ApiException.payloadTooLarge("IMAGE_TOO_LARGE", "이미지는 10MB 이하여야 합니다.");
        }
        String mimeType;
        String formatName;
        if (startsWith(bytes, PNG_SIGNATURE)) {
            mimeType = "image/png";
            formatName = "png";
        } else if (startsWith(bytes, JPEG_SIGNATURE)) {
            mimeType = "image/jpeg";
            formatName = "jpeg";
        } else {
            throw ApiException.badRequest("UNSUPPORTED_IMAGE", "PNG 또는 JPEG 이미지만 올릴 수 있습니다.");
        }
        int[] size = readSize(bytes, formatName);
        if (size[0] > maxSide || size[1] > maxSide) {
            throw ApiException.badRequest("IMAGE_DIMENSIONS_EXCEEDED",
                    "이미지의 가로·세로는 " + maxSide + "픽셀 이하여야 합니다.");
        }
        return new InspectedImage(bytes, mimeType, sha256Hex(bytes), size[0], size[1]);
    }

    /** ImageIO 리더로 헤더의 가로·세로만 읽는다. 읽을 수 없거나 시그니처와 다른 형식이면 지원하지 않는 이미지다. */
    private static int[] readSize(byte[] bytes, String formatName) {
        try (ImageInputStream input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            while (readers.hasNext()) {
                ImageReader reader = readers.next();
                try {
                    if (!formatName.equalsIgnoreCase(reader.getFormatName())) {
                        continue;
                    }
                    reader.setInput(input, true, true);
                    int width = reader.getWidth(0);
                    int height = reader.getHeight(0);
                    if (width > 0 && height > 0) {
                        return new int[] {width, height};
                    }
                } finally {
                    reader.dispose();
                }
            }
        } catch (IOException | RuntimeException e) {
            // 손상된 헤더. 원인 메시지는 쓰지 않고 아래 거절로 통일한다.
        }
        throw ApiException.badRequest("UNSUPPORTED_IMAGE", "읽을 수 없는 이미지입니다.");
    }

    private static boolean startsWith(byte[] bytes, byte[] prefix) {
        if (bytes.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (bytes[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256을 쓸 수 없습니다.", e);
        }
    }
}
