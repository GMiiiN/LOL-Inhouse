package com.lolinhouse.champion;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.Collator;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import javax.imageio.ImageIO;

import com.lolinhouse.ingest.extract.Deadline;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 역할: Data Dragon의 챔피언 목록(ko_KR)과 공식 아이콘으로 만든 {@link CatalogSnapshot}을 관리한다.
 * 읽는 순서는 versions.json(최신 버전) → champion.json → 챔피언별 아이콘이며, 받은 파일은 디스크
 * {cache-dir}/{버전}/ 에 저장해 다음부터 네트워크 없이 쓴다. 버전은 12시간(refresh-interval)마다 다시 확인한다.
 * 규칙: ①읽기는 전용 스레드 하나에서 한 번에 하나만 하고(동시 호출은 같은 읽기를 기다림) ②이미 스냅샷이 있으면 호출자를 막지 않고
 * 갱신은 백그라운드로 하며 ③네트워크가 실패하면 캐시된 가장 높은 버전으로 대신하고 그것도 없으면 empty ④실패 뒤 30초는 다시 시도하지 않는다
 * ⑤마감(Deadline)을 받으면 그 안에서만 기다린다(읽기는 계속 진행) ⑥버전은 숫자.숫자.숫자, 챔피언 id는 영문·숫자만 허용한다(경로·캐시 파일명으로 쓰이므로).
 * 앱이 뜬 직후(ApplicationReadyEvent) 백그라운드로 미리 읽어 두며, 실패해도 기동은 성공한다.
 * 설명: docs/design/m4-design.md D6, §2.2, §11.2-2
 */
@Component
public class DataDragonChampionCatalog implements ChampionCatalog {

    private static final Logger log = LoggerFactory.getLogger(DataDragonChampionCatalog.class);

    /** deadline 없이 current()를 부를 때 처음 읽기를 기다리는 최대 시간. */
    static final Duration DEFAULT_WAIT = Duration.ofSeconds(20);
    /** 읽기 실패 뒤 다시 시도하지 않는 시간(오프라인일 때 요청마다 네트워크를 두드리지 않으려고). */
    static final Duration RETRY_COOLDOWN = Duration.ofSeconds(30);

    private static final Pattern VERSION = Pattern.compile("[0-9]{1,6}\\.[0-9]{1,6}\\.[0-9]{1,6}");
    private static final Pattern CHAMPION_ID = Pattern.compile("[A-Za-z0-9]+");

    /** 읽은 스냅샷과 마지막으로 최신 버전을 확인한 시각(캐시 대체로 뜬 경우는 EPOCH = 곧 다시 확인 대상). */
    private record Loaded(CatalogSnapshot snapshot, Instant checkedAt) {
    }

    private final DataDragonSource source;
    private final Path cacheDir;
    private final Duration refreshInterval;
    private final Clock clock;
    private final JsonMapper json = JsonMapper.builder().build();
    private final ExecutorService loader = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "ddragon-loader");
        thread.setDaemon(true);
        return thread;
    });

    private volatile Loaded loaded;
    private volatile Instant retryNotBefore = Instant.MIN;
    /** 진행 중이거나 마지막으로 끝난 읽기. this 락으로 보호. */
    private CompletableFuture<Void> loading;

    @Autowired
    public DataDragonChampionCatalog(DataDragonSource source, @Value("${app.ddragon.cache-dir}") String cacheDir,
            @Value("${app.ddragon.refresh-interval}") Duration refreshInterval) {
        this(source, Path.of(cacheDir), refreshInterval, Clock.systemUTC());
    }

    public DataDragonChampionCatalog(DataDragonSource source, Path cacheDir, Duration refreshInterval, Clock clock) {
        this.source = source;
        this.cacheDir = cacheDir;
        this.refreshInterval = refreshInterval;
        this.clock = clock;
    }

    @Override
    public Optional<CatalogSnapshot> current() {
        return current(Deadline.of(clock, clock.instant(), DEFAULT_WAIT));
    }

    @Override
    public Optional<CatalogSnapshot> current(Deadline deadline) {
        Loaded now = loaded;
        if (now != null) {
            if (refreshDue(now)) {
                startLoad();
            }
            return Optional.of(now.snapshot());
        }
        CompletableFuture<Void> future = startLoad();
        try {
            future.get(deadline.remaining().toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException | ExecutionException e) {
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
        return Optional.ofNullable(loaded).map(Loaded::snapshot);
    }

    /** 앱이 뜬 직후 백그라운드에서 미리 읽는다. 기다리지 않고, 실패해도 기동에 영향이 없다. */
    @EventListener(ApplicationReadyEvent.class)
    public void warmUp() {
        startLoad();
    }

    @PreDestroy
    void shutdown() {
        loader.shutdownNow();
    }

    private boolean refreshDue(Loaded state) {
        Instant now = clock.instant();
        return !now.isBefore(state.checkedAt().plus(refreshInterval)) && !now.isBefore(retryNotBefore);
    }

    /** 읽기가 진행 중이면 그것을, 아니면 새로 시작한다(이미 최신이거나 쿨다운 중이면 이미 끝난 빈 결과). */
    private synchronized CompletableFuture<Void> startLoad() {
        if (loading != null && !loading.isDone()) {
            return loading;
        }
        Loaded current = loaded;
        if (current != null && !refreshDue(current)) {
            return CompletableFuture.completedFuture(null);
        }
        if (clock.instant().isBefore(retryNotBefore)) {
            return CompletableFuture.completedFuture(null);
        }
        loading = CompletableFuture.runAsync(this::load, loader);
        return loading;
    }

    /** 읽기 본체. 예외를 밖으로 던지지 않고 loaded를 갱신하거나 쿨다운만 건다. */
    private void load() {
        try {
            String latest = fetchLatestVersion();
            Loaded existing = loaded;
            CatalogSnapshot snapshot = existing != null && existing.snapshot().version().equals(latest)
                    ? existing.snapshot()
                    : buildSnapshot(latest, true);
            loaded = new Loaded(snapshot, clock.instant());
            return;
        } catch (IOException | RuntimeException e) {
            log.warn("Data Dragon 최신 버전을 불러오지 못했습니다({}).", e.getClass().getSimpleName());
        }
        retryNotBefore = clock.instant().plus(RETRY_COOLDOWN);
        if (loaded == null) {
            CatalogSnapshot cached = newestCachedSnapshot();
            if (cached != null) {
                log.info("캐시된 Data Dragon {} 버전을 대신 사용합니다.", cached.version());
                loaded = new Loaded(cached, Instant.EPOCH);
            }
        }
    }

    private String fetchLatestVersion() throws IOException {
        String version = json.readTree(source.fetch("/api/versions.json")).path(0).asString();
        if (!VERSION.matcher(version).matches()) {
            throw new IOException("버전 형식 오류");
        }
        return version;
    }

    /** 이 버전의 목록과 아이콘으로 스냅샷을 만든다. allowNetwork=false면 캐시에 없는 파일은 NoSuchFileException. */
    private CatalogSnapshot buildSnapshot(String version, boolean allowNetwork) throws IOException {
        byte[] listBytes = cachedBytes(version, "champion.json", "/cdn/" + version + "/data/ko_KR/champion.json",
                allowNetwork, this::isChampionList);
        JsonNode data = json.readTree(listBytes).path("data");
        List<Champion> champions = new ArrayList<>();
        List<IconMatcher.Icon> icons = new ArrayList<>();
        for (JsonNode node : data) {
            String id = node.path("id").asString();
            String key = node.path("key").asString();
            String name = node.path("name").asString();
            if (!CHAMPION_ID.matcher(id).matches()) {
                throw new IOException("챔피언 id 형식 오류");
            }
            byte[] iconBytes = cachedBytes(version, id + ".png", "/cdn/" + version + "/img/champion/" + id + ".png",
                    allowNetwork, bytes -> readImage(bytes) != null);
            champions.add(new Champion(id, key, name.isBlank() ? id : name));
            icons.add(new IconMatcher.Icon(key, id, readImage(iconBytes)));
        }
        if (champions.size() < 2) {
            throw new IOException("챔피언 목록이 비어 있습니다");
        }
        champions.sort(Comparator.comparing(Champion::name, Collator.getInstance(Locale.KOREAN))
                .thenComparing(Champion::id));
        return new CatalogSnapshot(version, champions, new IconMatcher(icons));
    }

    /**
     * 캐시 파일이 있고 유효하면 그것을, 없으면 네트워크에서 받아(유효할 때만) 캐시에 저장하고 돌려준다.
     * 유효하지 않은 캐시 파일은 지운다(깨진 파일 때문에 그 버전이 영영 실패하지 않도록).
     */
    private byte[] cachedBytes(String version, String name, String path, boolean allowNetwork,
            Predicate<byte[]> valid) throws IOException {
        Path file = cacheDir.resolve(version).resolve(name);
        if (Files.isRegularFile(file)) {
            byte[] bytes = Files.readAllBytes(file);
            if (valid.test(bytes)) {
                return bytes;
            }
            Files.deleteIfExists(file);
        }
        if (!allowNetwork) {
            throw new NoSuchFileException(file.toString());
        }
        byte[] bytes = source.fetch(path);
        if (!valid.test(bytes)) {
            throw new IOException("Data Dragon 응답 형식 오류");
        }
        try {
            writeAtomically(file, bytes);
        } catch (IOException e) {
            log.warn("Data Dragon 캐시를 쓰지 못했습니다({}). 메모리로만 사용합니다.", e.getClass().getSimpleName());
        }
        return bytes;
    }

    /** 임시 파일에 쓴 뒤 옮겨, 중간에 끊겨도 반쪽 파일이 캐시에 남지 않게 한다. */
    private static void writeAtomically(Path file, byte[] bytes) throws IOException {
        Files.createDirectories(file.getParent());
        Path temp = Files.createTempFile(file.getParent(), "tmp-", ".part");
        try {
            Files.write(temp, bytes);
            Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private boolean isChampionList(byte[] bytes) {
        try {
            return json.readTree(bytes).path("data").isObject();
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static BufferedImage readImage(byte[] bytes) {
        try {
            return ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (IOException e) {
            return null;
        }
    }

    /** 캐시에 통째로 있는 버전 중 가장 높은 것의 스냅샷. 없으면 null. */
    private CatalogSnapshot newestCachedSnapshot() {
        if (!Files.isDirectory(cacheDir)) {
            return null;
        }
        List<String> versions;
        try (Stream<Path> children = Files.list(cacheDir)) {
            versions = children.filter(Files::isDirectory).map(path -> path.getFileName().toString())
                    .filter(name -> VERSION.matcher(name).matches())
                    .sorted(Comparator.comparing(DataDragonChampionCatalog::versionNumbers,
                            DataDragonChampionCatalog::compareNumbers).reversed())
                    .toList();
        } catch (IOException e) {
            return null;
        }
        for (String version : versions) {
            try {
                return buildSnapshot(version, false);
            } catch (IOException | RuntimeException e) {
                // 이 버전의 캐시가 덜 받아졌다. 더 낮은 버전을 시도한다.
            }
        }
        return null;
    }

    private static int[] versionNumbers(String version) {
        return java.util.Arrays.stream(version.split("\\.")).mapToInt(Integer::parseInt).toArray();
    }

    private static int compareNumbers(int[] a, int[] b) {
        return java.util.Arrays.compare(a, b);
    }
}
