package com.lolinhouse.common.web;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;

import com.lolinhouse.IntegrationTest;
import com.lolinhouse.champion.ChampionCatalog;
import com.lolinhouse.ingest.FakeScoreboardExtractor;
import com.lolinhouse.ingest.SyntheticScoreboardImage;
import com.lolinhouse.ingest.extract.ExtractionException;
import com.lolinhouse.ingest.image.ImageStore;
import com.lolinhouse.ingest.job.ExtractionErrorCode;
import com.lolinhouse.ingest.job.ExtractionJob;
import com.lolinhouse.ingest.job.ExtractionJobRepository;
import com.lolinhouse.ingest.job.ExtractionRunner;
import com.lolinhouse.ingest.job.JobTransitions;
import com.lolinhouse.domain.Position;
import com.lolinhouse.domain.Side;
import com.lolinhouse.player.PlayerAlias;
import com.lolinhouse.player.PlayerAliasRepository;
import com.lolinhouse.player.PlayerCreateRequest;
import com.lolinhouse.player.PlayerService;
import com.lolinhouse.session.Duel;
import com.lolinhouse.session.DuelRepository;
import com.lolinhouse.session.InhouseSession;
import com.lolinhouse.session.InhouseSessionRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 역할: 로그 정책(닉네임·응답 원문·SQL 값을 로그에 남기지 않는다)을 실제 출력으로 확인한다. 세 곳을 본다.
 * (1) DB UNIQUE 충돌(별칭) — Hibernate의 SQL 오류 로그가 꺼져 있는지, (2) 500 처리기 로그 — 예외 클래스·제약 이름·스택만 남는지,
 * (3) 추출 실패 로그 — Gemini 원문/내부 예외 메시지가 빠지는지. 출력에 있어야 할 것(클래스·코드)도 함께 단정한다.
 * 설명: docs/design/m4-design.md §11.2-9
 */
@IntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class LogPolicyTest {

    @Autowired
    PlayerService playerService;
    @Autowired
    PlayerAliasRepository aliasRepository;
    @Autowired
    FakeScoreboardExtractor extractor;
    @Autowired
    ExtractionJobRepository jobs;
    @Autowired
    JobTransitions transitions;
    @Autowired
    ImageStore imageStore;
    @Autowired
    ChampionCatalog catalog;
    @Autowired
    JsonMapper json;
    @Autowired
    DuelRepository duels;
    @Autowired
    InhouseSessionRepository sessions;

    @AfterEach
    void resetFake() {
        extractor.reset();
    }

    private static String uniqueNickname() {
        return "비밀닉" + UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    void aliasUniqueViolationDoesNotPutTheNicknameInTheLog(CapturedOutput output) {
        String nickname = uniqueNickname();
        Long ownerId = playerService.create(new PlayerCreateRequest("로그실명", Position.TOP, List.of(nickname))).id();
        Long otherId = playerService.create(new PlayerCreateRequest("다른실명", Position.MID, List.of(uniqueNickname())))
                .id();

        // 사전 조회를 거치지 않고 저장소에 바로 넣어 DB UNIQUE 위반(MySQL 1062 + Hibernate 오류 로그 경로)을 만든다.
        assertThatThrownBy(() -> aliasRepository.saveAndFlush(PlayerAlias.of(otherId, nickname)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(ownerId).isNotEqualTo(otherId);

        assertThat(output.getAll()).doesNotContain(nickname);
    }

    @Test
    void racingAliasInsertsThroughPlayerServiceReturn409WithoutNicknameInLog(CapturedOutput output) throws Exception {
        String nickname = uniqueNickname();
        int threads = 6;
        CountDownLatch go = new CountDownLatch(1);
        List<CompletableFuture<String>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(CompletableFuture.supplyAsync(() -> {
                try {
                    go.await();
                    playerService.create(new PlayerCreateRequest("경쟁실명", Position.ADC, List.of(nickname)));
                    return "OK";
                } catch (ApiException e) {
                    return e.code();
                } catch (Exception e) {
                    return e.getClass().getSimpleName();
                }
            }));
        }
        go.countDown();
        List<String> results = new ArrayList<>();
        for (CompletableFuture<String> future : futures) {
            results.add(future.get());
        }

        assertThat(results.stream().filter("OK"::equals)).hasSize(1);
        assertThat(results).contains("ALIAS_CONFLICT");
        assertThat(output.getAll()).doesNotContain(nickname);
    }

    @Test
    void unexpectedExceptionLogKeepsClassAndConstraintButNotMessages(CapturedOutput output) throws Exception {
        String secret = uniqueNickname();
        ApiExceptionHandler handler = new ApiExceptionHandler();
        ServletWebRequest request = new ServletWebRequest(new MockHttpServletRequest());
        ConstraintViolationException cause = new ConstraintViolationException(
                "Duplicate entry '" + secret + "' for key 'player_alias.uk_test_key'",
                new SQLException("Duplicate entry '" + secret + "'", "23000", 1062), "player_alias.uk_test_key");

        handler.handleUnexpected(new DataIntegrityViolationException("저장 실패 " + secret, cause), request);
        handler.handleUnexpected(new IllegalStateException("내부 상태 " + secret), request);

        assertThat(output.getAll()).doesNotContain(secret);
        assertThat(output.getAll()).contains("DataIntegrityViolationException").contains("ConstraintViolationException")
                .contains("player_alias.uk_test_key").contains("IllegalStateException");
    }

    @Test
    void extractionFailureLogsCodeButNotTheRawResponseOrInternalMessages(CapturedOutput output) throws Exception {
        String raw = "RAW-GEMINI-BODY-" + uniqueNickname();
        Duel duel = newDuel();
        long failedByExtractor = queuedJob(duel);
        extractor.thenThrow(new ExtractionException(ExtractionErrorCode.EXTRACTION_REJECTED, 400, false, raw));
        runner().run(failedByExtractor);

        extractor.reset();
        long failedByBug = queuedJob(duel);
        extractor.beforeEachCall(() -> {
            throw new IllegalStateException(raw);
        }).thenReturn(SyntheticScoreboardImage.defaultImage().scoreboard());
        runner().run(failedByBug);

        assertThat(jobs.findById(failedByExtractor).orElseThrow().errorCode())
                .isEqualTo(ExtractionErrorCode.EXTRACTION_REJECTED);
        assertThat(jobs.findById(failedByBug).orElseThrow().errorCode()).isEqualTo(ExtractionErrorCode.INTERNAL_ERROR);
        assertThat(output.getAll()).doesNotContain(raw).doesNotContain("RAW-GEMINI-BODY");
        assertThat(output.getAll()).contains("EXTRACTION_REJECTED").contains("IllegalStateException");
    }

    private ExtractionRunner runner() {
        return new ExtractionRunner(transitions, jobs, imageStore, extractor, catalog, json, "test-model",
                Duration.ofMinutes(5), Clock.systemUTC());
    }

    private Duel newDuel() {
        InhouseSession session = sessions.save(InhouseSession.of(java.time.LocalDate.of(4500, 1, 1)
                .plusDays(java.util.concurrent.ThreadLocalRandom.current().nextInt(1_000_000))));
        return duels.save(Duel.of(session.id(), 1, com.lolinhouse.session.FearlessMode.RESET));
    }

    private long queuedJob(Duel duel) {
        byte[] png = SyntheticScoreboardImage.defaultImage().png();
        String hash = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
        String key = imageStore.put(hash, "image/png", png);
        return jobs.saveAndFlush(ExtractionJob.queued(duel.id(), Side.BLUE, Position.ADC, hash, key)).id();
    }
}
