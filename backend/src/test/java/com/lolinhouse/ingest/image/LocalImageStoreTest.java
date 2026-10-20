package com.lolinhouse.ingest.image;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 역할: 로컬 이미지 저장소의 키 규칙, 멱등 저장, 경로 탈출 방어, 같은 키 동시 저장을 확인한다.
 */
class LocalImageStoreTest {

    static final String HASH = "ab" + "c".repeat(62);

    @TempDir
    Path root;

    LocalImageStore store() {
        return new LocalImageStore(root.toString());
    }

    @Test
    void putThenGetRoundTripsUnderHashPrefixedKey() {
        byte[] bytes = {1, 2, 3, 4};

        String key = store().put(HASH, "image/png", bytes);

        assertThat(key).isEqualTo("ab/" + HASH + ".png");
        assertThat(store().get(key)).isEqualTo(bytes);
        assertThat(root.resolve(key)).exists();
    }

    @Test
    void jpegUsesJpgExtension() {
        assertThat(store().put(HASH, "image/jpeg", new byte[] {9})).isEqualTo("ab/" + HASH + ".jpg");
    }

    @Test
    void existingKeyIsNotWrittenAgain() throws IOException {
        LocalImageStore store = store();
        String key = store.put(HASH, "image/png", new byte[] {1, 2, 3});
        Path file = root.resolve(key);
        Files.setLastModifiedTime(file, FileTime.fromMillis(1_000_000L));

        String again = store.put(HASH, "image/png", new byte[] {9, 9, 9, 9});

        assertThat(again).isEqualTo(key);
        assertThat(Files.readAllBytes(file)).as("처음 내용 그대로").isEqualTo(new byte[] {1, 2, 3});
        assertThat(Files.getLastModifiedTime(file).toMillis()).isEqualTo(1_000_000L);
    }

    @Test
    void noTemporaryFilesRemainAfterPut() throws IOException {
        store().put(HASH, "image/png", new byte[] {1});

        try (Stream<Path> files = Files.walk(root)) {
            assertThat(files.filter(Files::isRegularFile).map(p -> p.getFileName().toString()).toList())
                    .containsExactly(HASH + ".png");
        }
    }

    @Test
    void invalidHashOrMimeTypeIsRejected() {
        LocalImageStore store = store();

        assertThatThrownBy(() -> store.put("../../etc/passwd", "image/png", new byte[] {1}))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.put("AB" + "C".repeat(62), "image/png", new byte[] {1}))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.put(HASH.substring(1), "image/png", new byte[] {1}))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.put(null, "image/png", new byte[] {1}))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.put(HASH, "image/gif", new byte[] {1}))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.put(HASH, null, new byte[] {1}))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void invalidKeysAndPathEscapesAreRejectedOnGet() throws IOException {
        Path outside = Files.writeString(root.resolveSibling("outside-" + System.nanoTime() + ".png"), "secret");
        LocalImageStore store = store();
        List<String> badKeys = List.of("../" + outside.getFileName(), "ab/../../" + outside.getFileName(),
                "/etc/passwd", "ab\\" + HASH + ".png", "ab/" + HASH + ".gif", "ab/" + HASH.toUpperCase() + ".png",
                "ab/" + HASH, "", "ab/" + HASH + ".png/../x.png", "zz/" + HASH + ".png");

        for (String key : badKeys) {
            assertThatThrownBy(() -> store.get(key)).as(key).isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> store.get(null)).isInstanceOf(IllegalArgumentException.class);
        Files.deleteIfExists(outside);
    }

    @Test
    void missingFileIsAnUncheckedIoException() {
        assertThatThrownBy(() -> store().get("ab/" + HASH + ".png")).isInstanceOf(UncheckedIOException.class);
    }

    @Test
    void sameKeyConcurrentPutsAllSucceedAndLeaveOneCompleteFile() throws Exception {
        LocalImageStore store = store();
        byte[] bytes = new byte[200_000];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) (i * 31);
        }
        int threads = 8;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<String>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                Callable<String> task = () -> {
                    start.await();
                    return store.put(HASH, "image/png", bytes);
                };
                futures.add(pool.submit(task));
            }
            start.countDown();
            for (Future<String> future : futures) {
                assertThat(future.get()).isEqualTo("ab/" + HASH + ".png");
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(store.get("ab/" + HASH + ".png")).isEqualTo(bytes);
        try (Stream<Path> files = Files.walk(root)) {
            assertThat(files.filter(Files::isRegularFile).count()).as("임시 파일이 남지 않는다").isEqualTo(1);
        }
    }
}
