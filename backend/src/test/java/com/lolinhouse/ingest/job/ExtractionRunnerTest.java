package com.lolinhouse.ingest.job;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.lolinhouse.IntegrationTest;
import com.lolinhouse.champion.ChampionCatalog;
import com.lolinhouse.domain.Position;
import com.lolinhouse.domain.Side;
import com.lolinhouse.ingest.FakeScoreboardExtractor;
import com.lolinhouse.ingest.SyntheticScoreboardImage;
import com.lolinhouse.ingest.extract.Box;
import com.lolinhouse.ingest.extract.ExtractedScoreboard;
import com.lolinhouse.ingest.extract.ExtractedTeam;
import com.lolinhouse.ingest.extract.ExtractionException;
import com.lolinhouse.ingest.image.ImageInspector;
import com.lolinhouse.ingest.image.ImageStore;
import com.lolinhouse.session.Duel;
import com.lolinhouse.session.DuelRepository;
import com.lolinhouse.session.InhouseSessionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 역할: 실행기의 상태 전이 경쟁과 실패 분기를 확인한다. 실행기를 직접 만들어(시계 주입) 같은 스레드/별도 스레드에서 run을 호출하고,
 * 래치로 "추출 도중"을 붙잡아 그 사이에 다른 쪽(TIMEOUT 정리·복구·밀어내기)이 상태를 바꾸게 한다.
 * 가짜 추출기는 공유 빈이라 @AfterEach에서 reset한다.
 */
@IntegrationTest
class ExtractionRunnerTest {

    /** 테스트가 직접 흘려 보낼 수 있는 시계. */
    static final class MutableClock extends Clock {
        private final AtomicReference<Instant> now = new AtomicReference<>(Instant.now());

        void advance(Duration duration) {
            now.updateAndGet(t -> t.plus(duration));
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    }

    @Autowired
    JobTransitions transitions;
    @Autowired
    ExtractionJobRepository jobs;
    @Autowired
    ImageStore imageStore;
    @Autowired
    ChampionCatalog catalog;
    @Autowired
    FakeScoreboardExtractor extractor;
    @Autowired
    JsonMapper json;
    @Autowired
    DuelRepository duels;
    @Autowired
    InhouseSessionRepository sessions;

    final SyntheticScoreboardImage synthetic = SyntheticScoreboardImage.defaultImage();

    @AfterEach
    void resetFake() {
        extractor.reset();
    }

    private ExtractionRunner runner(ChampionCatalog usedCatalog, Clock clock) {
        return new ExtractionRunner(transitions, jobs, imageStore, extractor, usedCatalog, json, "test-model",
                Duration.ofMinutes(5), clock);
    }

    /** 이미지를 저장소에 넣고 QUEUED 작업을 만든다. */
    private long queuedJob() {
        return queuedJob(ExtractionTestSupport.seededPng((int) (System.nanoTime() % 100_000)));
    }

    private long queuedJob(byte[] png) {
        Duel duel = ExtractionTestSupport.newDuel(sessions, duels);
        var inspected = new ImageInspector(10 * 1024 * 1024, 4096).inspect(png);
        String key = imageStore.put(inspected.hash(), inspected.mimeType(), png);
        return jobs.saveAndFlush(ExtractionJob.queued(duel.id(), Side.BLUE, Position.ADC, inspected.hash(), key)).id();
    }

    /** 추출 호출 안에서 멈췄다가 풀리는 가짜. entered가 0이 되면 추출 도중이다. */
    private record Gate(CountDownLatch entered, CountDownLatch release) {
        static Gate install(FakeScoreboardExtractor extractor, ExtractedScoreboard result) {
            Gate gate = new Gate(new CountDownLatch(1), new CountDownLatch(1));
            extractor.thenReturn(result).beforeEachCall(() -> {
                gate.entered.countDown();
                try {
                    gate.release.await(30, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            return gate;
        }
    }

    private Thread runInThread(ExtractionRunner runner, long jobId) {
        Thread thread = new Thread(() -> runner.run(jobId));
        thread.start();
        return thread;
    }

    @Test
    void successStoresResultWithTwentyMatchesAndCatalogVersion() {
        extractor.thenReturn(synthetic.scoreboard());
        long jobId = queuedJob(synthetic.png());

        runner(catalog, Clock.systemUTC()).run(jobId);

        ExtractionJob job = jobs.findById(jobId).orElseThrow();
        assertThat(job.status()).isEqualTo(ExtractionJobStatus.SUCCEEDED);
        ExtractionResult result = json.readValue(job.resultJson(), ExtractionResult.class);
        assertThat(result.model()).isEqualTo("test-model");
        assertThat(result.dataDragonVersion()).isNotBlank();
        long matched = java.util.stream.Stream.of(result.top(), result.bottom())
                .flatMap(t -> java.util.stream.Stream.concat(t.rows().stream().map(ExtractionResult.Row::champion),
                        t.bans().stream().map(ExtractionResult.Ban::champion)))
                .filter(m -> !m.cropFailed() && !m.candidates().isEmpty()).count();
        assertThat(matched).isEqualTo(20);
    }

    @Test
    void lateSuccessAfterTimeoutCleanupDoesNotOverwriteTheFailure() throws Exception {
        Gate gate = Gate.install(extractor, synthetic.scoreboard());
        long jobId = queuedJob();
        Thread worker = runInThread(runner(catalog, Clock.systemUTC()), jobId);
        assertThat(gate.entered.await(30, TimeUnit.SECONDS)).isTrue();

        // 조회가 오래된 RUNNING을 정리한 것과 같은 조건부 UPDATE
        int expired = transitions.expireStaleRunning(jobId, Instant.now().plusSeconds(3600), Instant.now());
        gate.release.countDown();
        worker.join(30_000);

        assertThat(expired).isEqualTo(1);
        ExtractionJob job = jobs.findById(jobId).orElseThrow();
        assertThat(job.status()).isEqualTo(ExtractionJobStatus.FAILED);
        assertThat(job.errorCode()).isEqualTo(ExtractionErrorCode.EXTRACTION_TIMEOUT);
        assertThat(job.resultJson()).isNull();
    }

    @Test
    void recoveryRacingWithCompletionLeavesInterruptedAndDiscardsTheResult() throws Exception {
        Gate gate = Gate.install(extractor, synthetic.scoreboard());
        long jobId = queuedJob();
        Thread worker = runInThread(runner(catalog, Clock.systemUTC()), jobId);
        assertThat(gate.entered.await(30, TimeUnit.SECONDS)).isTrue();

        new ExtractionJobRecovery(transitions).recover();
        gate.release.countDown();
        worker.join(30_000);

        ExtractionJob job = jobs.findById(jobId).orElseThrow();
        assertThat(job.status()).isEqualTo(ExtractionJobStatus.FAILED);
        assertThat(job.errorCode()).isEqualTo(ExtractionErrorCode.INTERRUPTED);
        assertThat(job.resultJson()).isNull();
    }

    @Test
    void finishingAfterTheJobDeadlineBecomesTimeoutEvenIfExtractionSucceeded() {
        MutableClock clock = new MutableClock();
        extractor.thenReturn(synthetic.scoreboard()).beforeEachCall(() -> clock.advance(Duration.ofMinutes(6)));
        long jobId = queuedJob();

        runner(catalog, clock).run(jobId);

        ExtractionJob job = jobs.findById(jobId).orElseThrow();
        assertThat(job.status()).isEqualTo(ExtractionJobStatus.FAILED);
        assertThat(job.errorCode()).isEqualTo(ExtractionErrorCode.EXTRACTION_TIMEOUT);
        assertThat(job.attemptCount()).isEqualTo(1);
    }

    @Test
    void missingCatalogFailsBeforeGeminiIsCalled() {
        extractor.thenReturn(synthetic.scoreboard());
        long jobId = queuedJob();
        ChampionCatalog empty = Optional::empty;

        runner(empty, Clock.systemUTC()).run(jobId);

        ExtractionJob job = jobs.findById(jobId).orElseThrow();
        assertThat(job.errorCode()).isEqualTo(ExtractionErrorCode.CHAMPION_CATALOG_UNAVAILABLE);
        assertThat(extractor.calls()).isZero();
    }

    @Test
    void unexpectedExceptionBecomesInternalErrorAndIsNotThrown() {
        extractor.beforeEachCall(() -> {
            throw new IllegalStateException("닉네임이 들어간 내부 메시지");
        }).thenReturn(synthetic.scoreboard());
        long jobId = queuedJob();

        runner(catalog, Clock.systemUTC()).run(jobId);

        ExtractionJob job = jobs.findById(jobId).orElseThrow();
        assertThat(job.status()).isEqualTo(ExtractionJobStatus.FAILED);
        assertThat(job.errorCode()).isEqualTo(ExtractionErrorCode.INTERNAL_ERROR);
    }

    @Test
    void missingStoredImageFailsAsImageUnreadable() {
        extractor.thenReturn(synthetic.scoreboard());
        Duel duel = ExtractionTestSupport.newDuel(sessions, duels);
        String hash = "d".repeat(64);
        long jobId = jobs.saveAndFlush(ExtractionJob.queued(duel.id(), Side.RED, Position.TOP, hash,
                "dd/" + hash + ".png")).id();

        runner(catalog, Clock.systemUTC()).run(jobId);

        assertThat(jobs.findById(jobId).orElseThrow().errorCode()).isEqualTo(ExtractionErrorCode.IMAGE_UNREADABLE);
        assertThat(extractor.calls()).isZero();
    }

    @Test
    void jobThatIsNoLongerQueuedIsNotRunAtAll() {
        extractor.thenReturn(synthetic.scoreboard());
        long jobId = queuedJob();
        transitions.fail(jobId, ExtractionErrorCode.SUPERSEDED, 0, Instant.now());

        runner(catalog, Clock.systemUTC()).run(jobId);

        assertThat(extractor.calls()).isZero();
        assertThat(jobs.findById(jobId).orElseThrow().errorCode()).isEqualTo(ExtractionErrorCode.SUPERSEDED);
    }

    @Test
    void extractorFailureKeepsItsCodeAndAttemptCount() {
        extractor.thenThrow(new ExtractionException(ExtractionErrorCode.EXTRACTION_UPSTREAM_UNAVAILABLE, 503, true, "원문"));
        long jobId = queuedJob();

        runner(catalog, Clock.systemUTC()).run(jobId);

        ExtractionJob job = jobs.findById(jobId).orElseThrow();
        assertThat(job.errorCode()).isEqualTo(ExtractionErrorCode.EXTRACTION_UPSTREAM_UNAVAILABLE);
        assertThat(job.attemptCount()).isEqualTo(1);
    }

    @Test
    void aBoxWithNoAreaFailsOnlyThatCell() {
        ExtractedScoreboard base = synthetic.scoreboard();
        List<Box> bans = new java.util.ArrayList<>(base.top().banBoxes());
        bans.set(0, new Box(500, 500, 500, 500));
        ExtractedTeam top = new ExtractedTeam(base.top().kills(), base.top().deaths(), base.top().assists(),
                base.top().gold(), base.top().rows(), bans);
        extractor.thenReturn(new ExtractedScoreboard(base.topResult(), base.shotDate(), base.duration(), top,
                base.bottom()));
        long jobId = queuedJob(synthetic.png());

        runner(catalog, Clock.systemUTC()).run(jobId);

        ExtractionResult result = json.readValue(jobs.findById(jobId).orElseThrow().resultJson(), ExtractionResult.class);
        assertThat(result.top().bans().get(0).champion().cropFailed()).isTrue();
        assertThat(result.top().bans().get(1).champion().cropFailed()).isFalse();
        assertThat(result.top().rows().get(0).champion().cropFailed()).isFalse();
    }
}
