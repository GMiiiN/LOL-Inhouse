package com.lolinhouse.ingest.job;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;

import com.jayway.jsonpath.JsonPath;
import com.lolinhouse.IntegrationTest;
import com.lolinhouse.domain.DuelTeam;
import com.lolinhouse.domain.Side;
import com.lolinhouse.ingest.FakeDataDragonSource;
import com.lolinhouse.ingest.FakeScoreboardExtractor;
import com.lolinhouse.ingest.SyntheticScoreboardImage;
import com.lolinhouse.ingest.extract.ExtractionException;
import com.lolinhouse.ingest.image.ImageInspector;
import com.lolinhouse.session.Duel;
import com.lolinhouse.session.DuelRepository;
import com.lolinhouse.session.GameSet;
import com.lolinhouse.session.GameSet.DisplayedTotals;
import com.lolinhouse.session.GameSetRepository;
import com.lolinhouse.session.InhouseSessionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;

import static com.lolinhouse.ingest.job.ExtractionTestSupport.await;
import static com.lolinhouse.ingest.job.ExtractionTestSupport.awaitFinished;
import static com.lolinhouse.ingest.job.ExtractionTestSupport.seededPng;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 역할: 추출 API(설계 §5·§7)를 가짜 Gemini 추출기 + 진짜 카탈로그/매처(가짜 Data Dragon 위) + 진짜 MySQL로 끝까지 검증한다:
 * 인증/CSRF, 202→폴링→SUCCEEDED, 입력 거절, 중복 이미지 409, 재사용 200, retry, 동시 업로드, 대기열 포화, 실패 코드, 오래된 작업 정리.
 * Gemini 키는 더미 값을 속성으로 준다(키 없음 케이스는 ExtractionNotConfiguredTest).
 * 가짜 추출기는 모든 통합 테스트가 같이 쓰므로 @AfterEach에서 reset한다.
 */
@IntegrationTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "GEMINI_API_KEY=test-dummy-key")
class ExtractionApiTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    FakeScoreboardExtractor extractor;
    @Autowired
    FakeDataDragonSource dataDragon;
    @Autowired
    ExtractionJobRepository jobs;
    @Autowired
    DuelRepository duels;
    @Autowired
    InhouseSessionRepository sessions;
    @Autowired
    GameSetRepository gameSets;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    JsonMapper json;

    @AfterEach
    void resetFakes() {
        extractor.reset();
        dataDragon.reset();
    }

    private Duel duel() {
        return ExtractionTestSupport.newDuel(sessions, duels);
    }

    private MockMultipartHttpServletRequestBuilder uploadRequest(long duelId, byte[] bytes, String topSide,
            String position) {
        MockMultipartHttpServletRequestBuilder request = multipart("/api/admin/duels/{id}/extractions", duelId);
        if (bytes != null) {
            request.file(new MockMultipartFile("image", "result.png", "image/png", bytes));
        }
        if (topSide != null) {
            request.param("topSide", topSide);
        }
        if (position != null) {
            request.param("capturerPosition", position);
        }
        return request;
    }

    private ResultActions upload(long duelId, byte[] bytes, boolean retry) throws Exception {
        return mockMvc.perform(uploadRequest(duelId, bytes, "BLUE", "ADC").param("retry", String.valueOf(retry))
                .with(user("admin").roles("ADMIN")).with(csrf()));
    }

    private long jobIdOf(ResultActions result) throws Exception {
        Integer id = JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.jobId");
        return id;
    }

    // ---------- 인증 ----------

    @Test
    void anonymousIs401AndMissingCsrfIs403() throws Exception {
        Duel duel = duel();
        mockMvc.perform(uploadRequest(duel.id(), seededPng(1), "BLUE", "ADC").with(csrf()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(uploadRequest(duel.id(), seededPng(1), "BLUE", "ADC").with(user("admin").roles("ADMIN")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/extractions/1")).andExpect(status().isUnauthorized());
    }

    // ---------- 성공 흐름 ----------

    @Test
    void uploadReturns202ThenJobSucceedsWithMatchedChampionsAndPlainStatusJson() throws Exception {
        SyntheticScoreboardImage synthetic = SyntheticScoreboardImage.defaultImage();
        extractor.thenReturn(synthetic.scoreboard());
        Duel duel = duel();

        ResultActions started = upload(duel.id(), synthetic.png(), false);
        started.andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.reused").value(false))
                .andExpect(header().string("Location", org.hamcrest.Matchers.matchesPattern("/api/admin/extractions/\\d+")));
        long jobId = jobIdOf(started);

        ExtractionJob done = awaitFinished(jobs, jobId);
        assertThat(done.status()).isEqualTo(ExtractionJobStatus.SUCCEEDED);
        assertThat(done.attemptCount()).isEqualTo(1);
        assertThat(extractor.calls()).isEqualTo(1);

        String statusBody = mockMvc.perform(get("/api/admin/extractions/{id}", jobId).with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value(jobId))
                .andExpect(jsonPath("$.duelId").value(duel.id()))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.errorCode").doesNotExist())
                .andExpect(jsonPath("$.attemptCount").value(1))
                .andExpect(jsonPath("$.topSide").value("BLUE"))
                .andExpect(jsonPath("$.capturerPosition").value("ADC"))
                .andExpect(jsonPath("$.savedSetId").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        // 응답 키는 §5 형식 그대로이고 시각(Instant) 필드가 없다.
        java.util.Map<String, Object> body = JsonPath.read(statusBody, "$");
        assertThat(body.keySet()).containsExactlyInAnyOrder("jobId", "duelId", "status", "errorCode", "attemptCount",
                "topSide", "capturerPosition", "savedSetId");

        ExtractionResult result = json.readValue(done.resultJson(), ExtractionResult.class);
        assertThat(result.schemaVersion()).isEqualTo(1);
        assertThat(result.dataDragonVersion()).isEqualTo(FakeDataDragonSource.DEFAULT_VERSION);
        assertThat(result.top().rows()).hasSize(5);
        assertThat(result.bottom().bans()).hasSize(5);
        for (int row = 0; row < 5; row++) {
            assertThat(result.top().rows().get(row).champion().candidates().get(0).id())
                    .isEqualTo(FakeDataDragonSource.championId(synthetic.pickIndex(true, row)));
            assertThat(result.bottom().bans().get(row).champion().candidates().get(0).id())
                    .isEqualTo(FakeDataDragonSource.championId(synthetic.banIndex(false, row)));
        }
    }

    // ---------- 입력 거절 ----------

    @Test
    void badInputsAreRejectedWithTheirCodes() throws Exception {
        Duel duel = duel();
        upload(duel.id(), "이미지 아님".getBytes(), false)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNSUPPORTED_IMAGE"));
        mockMvc.perform(uploadRequest(duel.id(), null, "BLUE", "ADC").with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("IMAGE_REQUIRED"));
        mockMvc.perform(uploadRequest(duel.id(), seededPng(2), null, "ADC").with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("topSide"));
        mockMvc.perform(uploadRequest(duel.id(), seededPng(2), "GRAY", "CARRY").with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.length()").value(2));
        upload(-5L, seededPng(3), false)
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("DUEL_NOT_FOUND"));
        mockMvc.perform(get("/api/admin/extractions/{id}", 999_999_999L).with(user("admin").roles("ADMIN")))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("EXTRACTION_NOT_FOUND"));
        assertThat(extractor.calls()).isZero();
    }

    // ---------- 중복·재사용·retry ----------

    @Test
    void imageAlreadyRegisteredAsActiveSetIs409WithExistingSetInfo() throws Exception {
        Duel duel = duel();
        byte[] png = seededPng(10);
        String hash = new ImageInspector(10 * 1024 * 1024, 4096).inspect(png).hash();
        int duration = 2000 + ThreadLocalRandom.current().nextInt(50_000);
        GameSet saved = gameSets.saveAndFlush(GameSet.create(duel.id(), 1, Side.BLUE, DuelTeam.TEAM1, DuelTeam.TEAM1,
                LocalDate.of(2026, 10, 12), duration, new DisplayedTotals(duration, 1, 1, 1),
                new DisplayedTotals(1, 1, 1, 1), hash, "ab/" + hash + ".png"));
        Duel other = duel();

        upload(other.id(), png, false)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_IMAGE"))
                .andExpect(jsonPath("$.existingSetId").value(saved.id()))
                .andExpect(jsonPath("$.existingDuelId").value(duel.id()))
                .andExpect(jsonPath("$.existingSetNo").value(1));
        assertThat(extractor.calls()).isZero();
    }

    @Test
    void sameImageAgainReusesTheExistingJobWithoutCallingGeminiAgain() throws Exception {
        SyntheticScoreboardImage synthetic = SyntheticScoreboardImage.defaultImage();
        extractor.thenReturn(synthetic.scoreboard());
        Duel duel = duel();
        byte[] png = seededPng(20);

        long first = jobIdOf(upload(duel.id(), png, false).andExpect(status().isAccepted()));
        awaitFinished(jobs, first);
        ResultActions again = upload(duel.id(), png, false);

        again.andExpect(status().isOk()).andExpect(jsonPath("$.reused").value(true))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"));
        assertThat(jobIdOf(again)).isEqualTo(first);
        assertThat(extractor.calls()).isEqualTo(1);
    }

    @Test
    void failedJobIsNotReusedSoTheSameImageCanBeRetriedAsNewJob() throws Exception {
        extractor.thenThrow(new ExtractionException(ExtractionErrorCode.EXTRACTION_REJECTED, 400, false, "원문"));
        Duel duel = duel();
        byte[] png = seededPng(21);

        long first = jobIdOf(upload(duel.id(), png, false));
        awaitFinished(jobs, first);
        long second = jobIdOf(upload(duel.id(), png, false).andExpect(status().isAccepted()));

        assertThat(second).isNotEqualTo(first);
        awaitFinished(jobs, second);
    }

    @Test
    void retryTrueCreatesNewJobAndSupersedesTheUnfinishedOne() throws Exception {
        SyntheticScoreboardImage synthetic = SyntheticScoreboardImage.defaultImage();
        CountDownLatch release = new CountDownLatch(1);
        extractor.thenReturn(synthetic.scoreboard()).beforeEachCall(() -> awaitQuietly(release));
        Duel duel = duel();
        byte[] png = seededPng(30);
        try {
            long first = jobIdOf(upload(duel.id(), png, false));
            await(jobs, first, j -> j.status() == ExtractionJobStatus.RUNNING, Duration.ofSeconds(10));

            ResultActions retried = upload(duel.id(), png, true);
            retried.andExpect(status().isAccepted()).andExpect(jsonPath("$.reused").value(false));
            long second = jobIdOf(retried);
            assertThat(second).isNotEqualTo(first);

            ExtractionJob superseded = jobs.findById(first).orElseThrow();
            assertThat(superseded.status()).isEqualTo(ExtractionJobStatus.FAILED);
            assertThat(superseded.errorCode()).isEqualTo(ExtractionErrorCode.SUPERSEDED);

            release.countDown();
            ExtractionJob newJob = awaitFinished(jobs, second);
            assertThat(newJob.status()).isEqualTo(ExtractionJobStatus.SUCCEEDED);
            // 밀려난 첫 작업의 늦은 성공은 덮어쓰지 않는다
            ExtractionJob stillSuperseded = jobs.findById(first).orElseThrow();
            assertThat(stillSuperseded.status()).isEqualTo(ExtractionJobStatus.FAILED);
            assertThat(stillSuperseded.errorCode()).isEqualTo(ExtractionErrorCode.SUPERSEDED);
            assertThat(stillSuperseded.resultJson()).isNull();
        } finally {
            release.countDown();
        }
    }

    @Test
    void concurrentUploadsOfTheSameImageCreateExactlyOneJob() throws Exception {
        extractor.thenReturn(SyntheticScoreboardImage.defaultImage().scoreboard());
        Duel duel = duel();
        byte[] png = seededPng(40);
        CountDownLatch go = new CountDownLatch(1);

        List<CompletableFuture<Integer[]>> futures = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            futures.add(CompletableFuture.supplyAsync(() -> {
                try {
                    go.await();
                    ResultActions result = upload(duel.id(), png, false);
                    return new Integer[] {result.andReturn().getResponse().getStatus(), (int) jobIdOf(result)};
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }));
        }
        go.countDown();
        Integer[] a = futures.get(0).get();
        Integer[] b = futures.get(1).get();

        assertThat(a[1]).isEqualTo(b[1]);
        assertThat(List.of(a[0], b[0])).containsExactlyInAnyOrder(202, 200);
        assertThat(jdbc.queryForObject("select count(*) from extraction_job where duel_id = ?", Integer.class,
                duel.id())).isEqualTo(1);
        awaitFinished(jobs, a[1]);
    }

    // ---------- 대기열 포화 ----------

    @Test
    void fullQueueRejectsWithBusyAndRecordsFailedBusy() throws Exception {
        SyntheticScoreboardImage synthetic = SyntheticScoreboardImage.defaultImage();
        CountDownLatch release = new CountDownLatch(1);
        extractor.thenReturn(synthetic.scoreboard()).beforeEachCall(() -> awaitQuietly(release));
        Duel duel = duel();
        List<Long> accepted = new ArrayList<>();
        try {
            long running = jobIdOf(upload(duel.id(), seededPng(50), false));
            accepted.add(running);
            await(jobs, running, j -> j.status() == ExtractionJobStatus.RUNNING, Duration.ofSeconds(10));
            // 대기열 용량 2
            accepted.add(jobIdOf(upload(duel.id(), seededPng(51), false).andExpect(status().isAccepted())));
            accepted.add(jobIdOf(upload(duel.id(), seededPng(52), false).andExpect(status().isAccepted())));

            upload(duel.id(), seededPng(53), false)
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code").value("EXTRACTION_BUSY"));

            Long busyId = jdbc.queryForObject("select max(id) from extraction_job where duel_id = ?", Long.class,
                    duel.id());
            ExtractionJob busy = jobs.findById(busyId).orElseThrow();
            assertThat(busy.status()).isEqualTo(ExtractionJobStatus.FAILED);
            assertThat(busy.errorCode()).isEqualTo(ExtractionErrorCode.BUSY);
        } finally {
            release.countDown();
        }
        for (Long id : accepted) {
            assertThat(awaitFinished(jobs, id).status()).isEqualTo(ExtractionJobStatus.SUCCEEDED);
        }
    }

    // ---------- 실패 ----------

    @Test
    void extractorFailureBecomesFailedJobWithItsCodeAndAttemptCount() throws Exception {
        extractor.thenThrow(new ExtractionException(ExtractionErrorCode.EXTRACTION_RATE_LIMITED, 429, true, "원문 응답 내용"));
        Duel duel = duel();

        long jobId = jobIdOf(upload(duel.id(), seededPng(60), false));
        awaitFinished(jobs, jobId);

        mockMvc.perform(get("/api/admin/extractions/{id}", jobId).with(user("admin").roles("ADMIN")))
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.errorCode").value("EXTRACTION_RATE_LIMITED"))
                .andExpect(jsonPath("$.attemptCount").value(1));
    }

    // ---------- 조회 시 오래된 작업 정리 ----------

    private long insertJob(long duelId, String status, String hash, LocalDateTime createdAt, LocalDateTime startedAt) {
        jdbc.update("insert into extraction_job (duel_id, status, top_side, capturer_position, image_hash, image_key, "
                + "attempt_count, created_at, started_at, updated_at) values (?, ?, 'BLUE', 'ADC', ?, ?, 0, ?, ?, ?)",
                duelId, status, hash, "ab/" + hash + ".png", createdAt, startedAt, createdAt);
        return jdbc.queryForObject("select max(id) from extraction_job where duel_id = ?", Long.class, duelId);
    }

    @Test
    void statusTurnsStaleRunningIntoTimeoutAndExpiredQueuedIntoExpired() throws Exception {
        Duel duel = duel();
        LocalDateTime old = LocalDateTime.now(ZoneOffset.UTC).minusMinutes(30);
        long staleRunning = insertJob(duel.id(), "RUNNING", "a".repeat(64), old, old);
        long oldQueued = insertJob(duel.id(), "QUEUED", "b".repeat(64), old, null);
        LocalDateTime fresh = LocalDateTime.now(ZoneOffset.UTC);
        long freshRunning = insertJob(duel.id(), "RUNNING", "c".repeat(64), fresh, fresh);

        mockMvc.perform(get("/api/admin/extractions/{id}", staleRunning).with(user("admin").roles("ADMIN")))
                .andExpect(jsonPath("$.status").value("FAILED")).andExpect(jsonPath("$.errorCode").value("EXTRACTION_TIMEOUT"));
        mockMvc.perform(get("/api/admin/extractions/{id}", oldQueued).with(user("admin").roles("ADMIN")))
                .andExpect(jsonPath("$.status").value("FAILED")).andExpect(jsonPath("$.errorCode").value("EXPIRED"));
        mockMvc.perform(get("/api/admin/extractions/{id}", freshRunning).with(user("admin").roles("ADMIN")))
                .andExpect(jsonPath("$.status").value("RUNNING"));
    }

    @Test
    void oldRunningJobIsNotReusedForTheSameImage() throws Exception {
        extractor.thenReturn(SyntheticScoreboardImage.defaultImage().scoreboard());
        Duel duel = duel();
        byte[] png = seededPng(70);
        String hash = new ImageInspector(10 * 1024 * 1024, 4096).inspect(png).hash();
        LocalDateTime old = LocalDateTime.now(ZoneOffset.UTC).minusMinutes(30);
        long stale = insertJob(duel.id(), "RUNNING", hash, old, old);

        ResultActions result = upload(duel.id(), png, false);

        result.andExpect(status().isAccepted());
        assertThat(jobIdOf(result)).isNotEqualTo(stale);
        awaitFinished(jobs, jobIdOf(result));
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
