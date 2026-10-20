package com.lolinhouse.ingest.job;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import com.lolinhouse.PersistenceTest;
import com.lolinhouse.domain.DuelTeam;
import com.lolinhouse.domain.Position;
import com.lolinhouse.domain.Side;
import com.lolinhouse.ingest.ExtractionResultFixture;
import com.lolinhouse.player.Player;
import com.lolinhouse.player.PlayerRepository;
import com.lolinhouse.session.Duel;
import com.lolinhouse.session.DuelRepository;
import com.lolinhouse.session.FearlessMode;
import com.lolinhouse.session.GameSet;
import com.lolinhouse.session.GameSet.DisplayedTotals;
import com.lolinhouse.session.GameSetRepository;
import com.lolinhouse.session.InhouseSession;
import com.lolinhouse.session.InhouseSessionRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 역할: V3 스키마와 Phase 0 계약(엔티티·리포지토리)이 실제 MySQL에서 맞물리는지 확인한다.
 * 확인하는 [확인 필요] 항목: result_json의 String + SqlTypes.JSON 매핑(validate 통과, 이중 인코딩 없음, 왕복),
 * ck_extraction_job_saved가 FK 컬럼 CHECK 제한(3823)에 걸리지 않는지(마이그레이션이 적용되면 통과), 조건부 UPDATE의 행 수 의미.
 * 각 테스트는 트랜잭션 안에서 돌고 롤백된다(@PersistenceTest).
 * 설명: docs/design/m4-design.md §1·§11.3
 */
@PersistenceTest
class ExtractionJobMappingTest {

    private static final Instant T0 = Instant.parse("2026-10-17T00:00:00Z");

    @Autowired
    EntityManager em;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    ExtractionJobRepository jobs;
    @Autowired
    DuelRepository duels;
    @Autowired
    InhouseSessionRepository sessions;
    @Autowired
    GameSetRepository gameSets;
    @Autowired
    PlayerRepository players;

    private final JsonMapper json = JsonMapper.builder().build();
    private int dateSeq = 0;

    private Duel savedDuel(FearlessMode mode) {
        InhouseSession session = sessions.save(InhouseSession.of(LocalDate.of(2026, 3, 1).plusDays(dateSeq++)));
        return duels.save(Duel.of(session.id(), 1, mode));
    }

    private ExtractionJob queuedJob(Long duelId, String hash) {
        return jobs.saveAndFlush(ExtractionJob.queued(duelId, Side.BLUE, Position.ADC, hash, "ab/" + hash + ".png"));
    }

    private void flushAndClear() {
        em.flush();
        em.clear();
    }

    private ExtractionJob reload(Long id) {
        em.clear();
        return jobs.findById(id).orElseThrow();
    }

    // ---------- result_json ----------

    @Test
    void resultJsonRoundTripsAsAJsonObjectWithoutDoubleEncoding() {
        Duel duel = savedDuel(FearlessMode.RESET);
        ExtractionResult result = ExtractionResultFixture.sample();
        String serialized = json.writeValueAsString(result);

        ExtractionJob job = queuedJob(duel.id(), "a".repeat(64));
        job.markRunning(T0);
        job.succeed(serialized, 2, T0.plusSeconds(30));
        jobs.saveAndFlush(job);
        flushAndClear();

        ExtractionJob found = jobs.findById(job.id()).orElseThrow();
        assertThat(json.readValue(found.resultJson(), ExtractionResult.class)).isEqualTo(result);
        // JSON 문자열이 한 번 더 따옴표로 감싸이지 않고 진짜 JSON 객체로 저장됐는지(이중 인코딩 방지)
        assertThat(jdbc.queryForObject("select json_type(result_json) from extraction_job where id = ?",
                String.class, job.id())).isEqualTo("OBJECT");
        assertThat(jdbc.queryForObject("select json_extract(result_json, '$.top.rows[0].nickname') from extraction_job where id = ?",
                String.class, job.id())).isEqualTo("\"합성선수T0\"");
    }

    // ---------- 엔티티 상태 전이 ----------

    @Test
    void entityTransitionsFollowTheStateMachineAndRejectInvalidOnes() {
        ExtractionJob job = ExtractionJob.queued(1L, Side.RED, Position.TOP, "b".repeat(64), "key");
        assertThat(job.status()).isEqualTo(ExtractionJobStatus.QUEUED);

        assertThatThrownBy(() -> job.succeed("{}", 1, T0)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> job.linkGameSet(5L)).isInstanceOf(IllegalStateException.class);

        job.markRunning(T0);
        assertThat(job.status()).isEqualTo(ExtractionJobStatus.RUNNING);
        assertThatThrownBy(() -> job.markRunning(T0)).isInstanceOf(IllegalStateException.class);

        job.succeed("{}", 3, T0.plusSeconds(20));
        assertThat(job.status()).isEqualTo(ExtractionJobStatus.SUCCEEDED);
        assertThat(job.attemptCount()).isEqualTo(3);
        assertThatThrownBy(() -> job.fail(ExtractionErrorCode.INTERNAL_ERROR, 3, T0)).isInstanceOf(IllegalStateException.class);

        job.linkGameSet(5L);
        assertThat(job.gameSetId()).isEqualTo(5L);
        assertThatThrownBy(() -> job.linkGameSet(6L)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> job.moveToDuel(2L)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void savedJobCanBeLinkedToAGameSetAndMovedBeforeThat() {
        Duel duel = savedDuel(FearlessMode.RESET);
        Duel other = savedDuel(FearlessMode.RESET);
        ExtractionJob job = queuedJob(duel.id(), "c".repeat(64));
        job.markRunning(T0);
        job.succeed(json.writeValueAsString(ExtractionResultFixture.sample()), 1, T0);
        job.moveToDuel(other.id());
        GameSet set = gameSets.saveAndFlush(GameSet.create(other.id(), 1, Side.BLUE, DuelTeam.TEAM1, DuelTeam.TEAM1,
                LocalDate.of(2026, 10, 12), 1542, new DisplayedTotals(1, 1, 1, 1), new DisplayedTotals(1, 1, 1, 1),
                job.imageHash(), job.imageKey()));
        job.linkGameSet(set.id());
        jobs.saveAndFlush(job);
        flushAndClear();

        ExtractionJob found = jobs.findById(job.id()).orElseThrow();
        assertThat(found.duelId()).isEqualTo(other.id());
        assertThat(found.gameSetId()).isEqualTo(set.id());
    }

    // ---------- 조건부 UPDATE ----------

    @Test
    void claimRunningSucceedsOnceAndOnlyFromQueued() {
        Duel duel = savedDuel(FearlessMode.RESET);
        ExtractionJob job = queuedJob(duel.id(), "d".repeat(64));

        assertThat(jobs.claimRunning(job.id(), T0)).isEqualTo(1);
        assertThat(jobs.claimRunning(job.id(), T0)).isZero();
        ExtractionJob running = reload(job.id());
        assertThat(running.status()).isEqualTo(ExtractionJobStatus.RUNNING);
        assertThat(running.startedAt()).isEqualTo(T0);
    }

    @Test
    void succeedStoresJsonOnlyFromRunningAndALateSuccessAfterFailureIsDiscarded() {
        Duel duel = savedDuel(FearlessMode.RESET);
        ExtractionJob job = queuedJob(duel.id(), "e".repeat(64));
        String serialized = json.writeValueAsString(ExtractionResultFixture.sample());

        assertThat(jobs.succeed(job.id(), serialized, 1, T0)).as("QUEUED에서는 성공 처리 불가").isZero();

        jobs.claimRunning(job.id(), T0);
        assertThat(jobs.fail(job.id(), ExtractionErrorCode.EXTRACTION_TIMEOUT, 2, T0.plusSeconds(1))).isEqualTo(1);
        assertThat(jobs.succeed(job.id(), serialized, 3, T0.plusSeconds(2))).as("늦은 성공은 덮어쓰지 않는다").isZero();

        ExtractionJob failed = reload(job.id());
        assertThat(failed.status()).isEqualTo(ExtractionJobStatus.FAILED);
        assertThat(failed.errorCode()).isEqualTo(ExtractionErrorCode.EXTRACTION_TIMEOUT);
        assertThat(failed.resultJson()).isNull();
        assertThat(failed.attemptCount()).isEqualTo(2);
    }

    @Test
    void succeedFromRunningWritesResultAndFinishTime() {
        Duel duel = savedDuel(FearlessMode.RESET);
        ExtractionJob job = queuedJob(duel.id(), "f".repeat(64));
        jobs.claimRunning(job.id(), T0);

        int updated = jobs.succeed(job.id(), json.writeValueAsString(ExtractionResultFixture.sample()), 2,
                T0.plusSeconds(40));

        assertThat(updated).isEqualTo(1);
        ExtractionJob done = reload(job.id());
        assertThat(done.status()).isEqualTo(ExtractionJobStatus.SUCCEEDED);
        assertThat(done.attemptCount()).isEqualTo(2);
        assertThat(done.finishedAt()).isEqualTo(T0.plusSeconds(40));
        assertThat(jdbc.queryForObject("select json_type(result_json) from extraction_job where id = ?", String.class,
                job.id())).isEqualTo("OBJECT");
    }

    @Test
    void failWorksFromQueuedAndRunningButNotFromFinishedJobs() {
        Duel duel = savedDuel(FearlessMode.RESET);
        ExtractionJob queued = queuedJob(duel.id(), "1".repeat(64));
        assertThat(jobs.fail(queued.id(), ExtractionErrorCode.BUSY, 0, T0)).isEqualTo(1);
        assertThat(jobs.fail(queued.id(), ExtractionErrorCode.INTERNAL_ERROR, 0, T0)).isZero();
        assertThat(reload(queued.id()).errorCode()).isEqualTo(ExtractionErrorCode.BUSY);

        ExtractionJob running = queuedJob(duel.id(), "2".repeat(64));
        jobs.claimRunning(running.id(), T0);
        assertThat(jobs.fail(running.id(), ExtractionErrorCode.EXTRACTION_REJECTED, 1, T0)).isEqualTo(1);
    }

    @Test
    void expireStaleRunningAndExpireQueuedUseTheirCutoffs() {
        Duel duel = savedDuel(FearlessMode.RESET);
        ExtractionJob running = queuedJob(duel.id(), "3".repeat(64));
        jobs.claimRunning(running.id(), T0);

        assertThat(jobs.expireStaleRunning(running.id(), T0.minusSeconds(1), T0.plusSeconds(700))).as("아직 오래되지 않음").isZero();
        assertThat(jobs.expireStaleRunning(running.id(), T0.plusSeconds(1), T0.plusSeconds(700))).isEqualTo(1);
        assertThat(reload(running.id()).errorCode()).isEqualTo(ExtractionErrorCode.EXTRACTION_TIMEOUT);

        ExtractionJob queued = queuedJob(duel.id(), "4".repeat(64));
        Instant created = queued.createdAt();
        assertThat(jobs.expireQueued(queued.id(), created.minusSeconds(1), created.plusSeconds(700))).isZero();
        assertThat(jobs.expireQueued(queued.id(), created.plusSeconds(1), created.plusSeconds(700))).isEqualTo(1);
        assertThat(reload(queued.id()).errorCode()).isEqualTo(ExtractionErrorCode.EXPIRED);
        assertThat(jobs.expireQueued(queued.id(), created.plusSeconds(1), created.plusSeconds(700))).isZero();
    }

    @Test
    void supersedeOnlyTouchesUnfinishedUnsavedJobsOfTheSameDuelAndHash() {
        Duel duel = savedDuel(FearlessMode.RESET);
        Duel otherDuel = savedDuel(FearlessMode.RESET);
        String hash = "5".repeat(64);
        ExtractionJob queued = queuedJob(duel.id(), hash);
        ExtractionJob running = queuedJob(duel.id(), hash);
        jobs.claimRunning(running.id(), T0);
        ExtractionJob otherHash = queuedJob(duel.id(), "6".repeat(64));
        ExtractionJob otherDuelSameHash = queuedJob(otherDuel.id(), hash);

        assertThat(jobs.supersede(duel.id(), hash, T0)).isEqualTo(2);

        assertThat(reload(queued.id()).errorCode()).isEqualTo(ExtractionErrorCode.SUPERSEDED);
        assertThat(reload(running.id()).errorCode()).isEqualTo(ExtractionErrorCode.SUPERSEDED);
        assertThat(reload(otherHash.id()).status()).isEqualTo(ExtractionJobStatus.QUEUED);
        assertThat(reload(otherDuelSameHash.id()).status()).isEqualTo(ExtractionJobStatus.QUEUED);
    }

    @Test
    void failAllUnfinishedMarksQueuedAndRunningAsInterruptedButKeepsSucceeded() {
        Duel duel = savedDuel(FearlessMode.RESET);
        ExtractionJob queued = queuedJob(duel.id(), "7".repeat(64));
        ExtractionJob running = queuedJob(duel.id(), "8".repeat(64));
        jobs.claimRunning(running.id(), T0);
        ExtractionJob succeeded = queuedJob(duel.id(), "9".repeat(64));
        jobs.claimRunning(succeeded.id(), T0);
        jobs.succeed(succeeded.id(), json.writeValueAsString(ExtractionResultFixture.sample()), 1, T0);

        int changed = jobs.failAllUnfinishedAsInterrupted(T0.plusSeconds(5));

        assertThat(changed).isGreaterThanOrEqualTo(2);
        assertThat(reload(queued.id()).errorCode()).isEqualTo(ExtractionErrorCode.INTERRUPTED);
        assertThat(reload(running.id()).errorCode()).isEqualTo(ExtractionErrorCode.INTERRUPTED);
        assertThat(reload(succeeded.id()).status()).isEqualTo(ExtractionJobStatus.SUCCEEDED);
    }

    @Test
    void reusableCandidatesIncludeUnsavedSucceededFreshQueuedAndFreshRunningNewestFirst() {
        Duel duel = savedDuel(FearlessMode.RESET);
        String hash = "0".repeat(64);
        ExtractionJob failed = queuedJob(duel.id(), hash);
        jobs.fail(failed.id(), ExtractionErrorCode.EXTRACTION_REJECTED, 1, T0);
        ExtractionJob succeeded = queuedJob(duel.id(), hash);
        jobs.claimRunning(succeeded.id(), T0);
        jobs.succeed(succeeded.id(), json.writeValueAsString(ExtractionResultFixture.sample()), 1, T0);
        ExtractionJob staleRunning = queuedJob(duel.id(), hash);
        jobs.claimRunning(staleRunning.id(), T0.minusSeconds(3600));
        ExtractionJob freshQueued = queuedJob(duel.id(), hash);
        em.clear();

        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        List<ExtractionJob> candidates = jobs.findReusableCandidates(duel.id(), hash,
                now.minus(Duration.ofMinutes(10)), now.minus(Duration.ofMinutes(10)));

        assertThat(candidates).extracting(ExtractionJob::id).containsExactly(freshQueued.id(), succeeded.id());
    }

    @Test
    void findByIdForUpdateReadsRowsInsideATransaction() {
        Duel duel = savedDuel(FearlessMode.RESET);
        ExtractionJob job = queuedJob(duel.id(), "7".repeat(63) + "0");

        assertThat(jobs.findByIdForUpdate(job.id())).isPresent();
        assertThat(duels.findByIdForUpdate(duel.id())).isPresent();
        assertThat(sessions.findByIdForUpdate(duel.sessionId())).isPresent();
        assertThat(duels.findByIdForUpdate(-1L)).isEmpty();
    }

    // ---------- 대결 fearless_mode ----------

    @Test
    void duelFearlessModeRoundTripsAndDefaultsToResetForPlainInserts() {
        Duel reset = savedDuel(FearlessMode.RESET);
        Duel continued = duels.save(Duel.of(reset.sessionId(), 2, FearlessMode.CONTINUE));
        flushAndClear();

        assertThat(duels.findById(reset.id()).orElseThrow().fearlessMode()).isEqualTo(FearlessMode.RESET);
        assertThat(duels.findById(continued.id()).orElseThrow().fearlessMode()).isEqualTo(FearlessMode.CONTINUE);
        assertThat(jdbc.queryForObject("select fearless_mode from duel where id = ?", String.class, continued.id()))
                .isEqualTo("CONTINUE");

        jdbc.update("insert into duel (session_id, seq, created_at) values (?, 3, '2026-10-17 00:00:00.000000')",
                reset.sessionId());
        assertThat(jdbc.queryForObject("select fearless_mode from duel where session_id = ? and seq = 3",
                String.class, reset.sessionId())).isEqualTo("RESET");
        assertThatThrownBy(() -> Duel.of(reset.sessionId(), 4, null)).isInstanceOf(IllegalArgumentException.class);
    }

    // ---------- GameSetRepository 신규 조회 ----------

    @Test
    void gameSetQueriesReturnActiveSetsPicksAndTeam1Players() {
        Duel duel = savedDuel(FearlessMode.RESET);
        assertThat(gameSets.findMaxActiveSetNo(duel.id())).isZero();
        assertThat(gameSets.findFirstByDuelIdAndDeletedAtIsNullOrderBySetNoDesc(duel.id())).isEmpty();

        GameSet first = gameSets.save(setWithTenPlayers(duel.id(), 1, "x".repeat(64), "champ", 1542));
        GameSet second = gameSets.save(setWithTenPlayers(duel.id(), 2, "y".repeat(64), "pick", 1700));
        GameSet deleted = gameSets.save(setWithTenPlayers(duel.id(), 3, "z".repeat(64), "gone", 1800));
        deleted.markDeleted();
        flushAndClear();

        assertThat(gameSets.findMaxActiveSetNo(duel.id())).isEqualTo(2);
        assertThat(gameSets.findFirstByDuelIdAndDeletedAtIsNullOrderBySetNoDesc(duel.id()).orElseThrow().id())
                .isEqualTo(second.id());
        assertThat(gameSets.findFirstByImageHashAndDeletedAtIsNull("x".repeat(64)).orElseThrow().id())
                .isEqualTo(first.id());
        assertThat(gameSets.findFirstByImageHashAndDeletedAtIsNull("z".repeat(64))).as("삭제된 세트는 제외").isEmpty();
        assertThat(gameSets.findByFingerprintAndDeletedAtIsNull(first.fingerprint()).orElseThrow().id())
                .isEqualTo(first.id());
        assertThat(gameSets.findActivePickChampionKeys(duel.id())).hasSize(20)
                .contains("champ0", "pick9").doesNotContain("gone0");
        assertThat(gameSets.findActivePickChampionKeysByDuelIds(List.of(duel.id()))).hasSize(20);
        assertThat(gameSets.findTeam1PlayerIds(first.id())).hasSize(5);
    }

    private GameSet setWithTenPlayers(Long duelId, int setNo, String hash, String championPrefix, int durationSec) {
        GameSet set = GameSet.create(duelId, setNo, Side.BLUE, DuelTeam.TEAM1, DuelTeam.TEAM1,
                LocalDate.of(2026, 10, 12), durationSec, new DisplayedTotals(10 + setNo, 5, 20, 60000),
                new DisplayedTotals(5, 10, 8, 50000), hash, "xx/" + hash + ".png");
        Position[] positions = Position.values();
        int pick = 0;
        for (DuelTeam team : DuelTeam.values()) {
            for (int row = 0; row < 5; row++) {
                Player player = players.save(Player.unregistered("n" + setNo + team + row));
                set.addPlayer(player.id(), team, positions[row], row + 1, championPrefix + pick, 1, 1, 1, 100, 10000);
                pick++;
            }
        }
        return set;
    }
}
