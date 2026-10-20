package com.lolinhouse.champion;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

import com.lolinhouse.ingest.FakeDataDragonSource;
import com.lolinhouse.ingest.extract.Deadline;
import com.lolinhouse.ingest.extract.MutableClock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class DataDragonChampionCatalogTest {

    private static final Duration INTERVAL = Duration.ofHours(12);

    @TempDir
    Path cacheDir;

    private final FakeDataDragonSource source = new FakeDataDragonSource();
    private final List<DataDragonChampionCatalog> created = new ArrayList<>();

    @AfterEach
    void tearDown() {
        created.forEach(DataDragonChampionCatalog::shutdown);
    }

    private DataDragonChampionCatalog catalog(DataDragonSource useSource, Clock clock) {
        DataDragonChampionCatalog catalog = new DataDragonChampionCatalog(useSource, cacheDir, INTERVAL, clock);
        created.add(catalog);
        return catalog;
    }

    private DataDragonChampionCatalog catalog(DataDragonSource useSource) {
        return catalog(useSource, Clock.systemUTC());
    }

    private static void awaitVersion(DataDragonChampionCatalog catalog, String version) throws Exception {
        long end = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < end) {
            if (catalog.current().map(CatalogSnapshot::version).filter(version::equals).isPresent()) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("버전 " + version + "에 도달하지 못했습니다.");
    }

    @Test
    void firstLoadBuildsSortedSnapshotAndWritesCache() {
        CatalogSnapshot snapshot = catalog(source).current().orElseThrow();

        assertThat(snapshot.version()).isEqualTo("16.20.1");
        assertThat(snapshot.champions()).hasSize(20);
        assertThat(snapshot.champions()).extracting(Champion::name).isSorted();
        assertThat(snapshot.contains("Champion07")).isTrue();
        assertThat(Files.isRegularFile(cacheDir.resolve("16.20.1/champion.json"))).isTrue();
        assertThat(Files.isRegularFile(cacheDir.resolve("16.20.1/Champion19.png"))).isTrue();
        assertThat(source.fetchCount()).isEqualTo(1 + 1 + 20);
    }

    @Test
    void secondCatalogReusesDiskCacheAndOnlyAsksForVersion() {
        catalog(source).current().orElseThrow();
        source.reset();

        CatalogSnapshot snapshot = catalog(source).current().orElseThrow();

        assertThat(snapshot.champions()).hasSize(20);
        assertThat(source.fetchCount()).isEqualTo(1);
    }

    @Test
    void networkFailureFallsBackToNewestCompleteCachedVersion() throws Exception {
        catalog(source).current().orElseThrow();
        // 덜 받아진 더 높은 버전 캐시가 있어도 완전한 캐시를 고른다
        Files.createDirectories(cacheDir.resolve("15.1.1"));
        Files.createDirectories(cacheDir.resolve("99.0.0"));
        Files.writeString(cacheDir.resolve("99.0.0/champion.json"), "{}", StandardCharsets.UTF_8);
        source.setOffline(true);

        CatalogSnapshot snapshot = catalog(source).current().orElseThrow();

        assertThat(snapshot.version()).isEqualTo("16.20.1");
    }

    @Test
    void emptyWhenOfflineAndNoCache() {
        source.setOffline(true);
        assertThat(catalog(source).current()).isEmpty();
    }

    @Test
    void rejectsUnsafeChampionIdsAndBadVersions() {
        DataDragonSource badId = path -> {
            if (path.equals("/api/versions.json")) {
                return "[\"1.2.3\"]".getBytes(StandardCharsets.UTF_8);
            }
            return ("{\"data\":{\"A\":{\"id\":\"../evil\",\"key\":\"1\",\"name\":\"가\"},"
                    + "\"B\":{\"id\":\"B\",\"key\":\"2\",\"name\":\"나\"}}}").getBytes(StandardCharsets.UTF_8);
        };
        assertThat(catalog(badId).current()).isEmpty();

        DataDragonSource badVersion = path -> "[\"../../x\"]".getBytes(StandardCharsets.UTF_8);
        assertThat(catalog(badVersion).current()).isEmpty();
    }

    @Test
    void concurrentCallsLoadOnlyOnce() throws Exception {
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        DataDragonChampionCatalog catalog = catalog(source);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Optional<CatalogSnapshot>>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                return catalog.current();
            }));
        }
        start.countDown();
        for (Future<Optional<CatalogSnapshot>> future : futures) {
            assertThat(future.get()).isPresent();
        }
        pool.shutdown();

        assertThat(source.fetchCount()).isEqualTo(1 + 1 + 20);
    }

    @Test
    void deadlineLimitsWaitingButLoadContinuesInBackground() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        DataDragonSource slow = path -> {
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("중단");
            }
            return source.fetch(path);
        };
        DataDragonChampionCatalog catalog = catalog(slow);
        Deadline shortDeadline = Deadline.of(Clock.systemUTC(), Instant.now(), Duration.ofMillis(200));

        assertThat(catalog.current(shortDeadline)).isEmpty();

        release.countDown();
        awaitVersion(catalog, "16.20.1");
    }

    @Test
    void staleSnapshotIsReturnedAtOnceAndRefreshedInBackground() throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-10-17T00:00:00Z"));
        AtomicReference<FakeDataDragonSource> active = new AtomicReference<>(new FakeDataDragonSource(20, "16.20.1"));
        DataDragonChampionCatalog catalog = catalog(path -> active.get().fetch(path), clock);
        assertThat(catalog.current().orElseThrow().version()).isEqualTo("16.20.1");

        active.set(new FakeDataDragonSource(21, "16.21.1"));
        assertThat(catalog.current().orElseThrow().version()).isEqualTo("16.20.1");

        clock.advance(Duration.ofHours(13));
        assertThat(catalog.current().orElseThrow().version()).isEqualTo("16.20.1");
        awaitVersion(catalog, "16.21.1");
        assertThat(catalog.current().orElseThrow().champions()).hasSize(21);
    }

    @Test
    void failureCooldownAvoidsHammeringTheNetwork() {
        MutableClock clock = new MutableClock(Instant.parse("2026-10-17T00:00:00Z"));
        source.setOffline(true);
        DataDragonChampionCatalog catalog = catalog(source, clock);

        assertThat(catalog.current()).isEmpty();
        int afterFirst = source.fetchCount();
        assertThat(catalog.current()).isEmpty();
        assertThat(source.fetchCount()).isEqualTo(afterFirst);

        clock.advance(DataDragonChampionCatalog.RETRY_COOLDOWN.plusSeconds(1));
        source.setOffline(false);
        assertThat(catalog.current()).isPresent();
    }
}
