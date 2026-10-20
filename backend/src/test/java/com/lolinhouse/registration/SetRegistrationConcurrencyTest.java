package com.lolinhouse.registration;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.lolinhouse.IntegrationTest;
import com.lolinhouse.common.web.ApiException;
import com.lolinhouse.ingest.job.ExtractionJobRepository;
import com.lolinhouse.player.PlayerService;
import com.lolinhouse.registration.RegistrationFixtures.Req;
import com.lolinhouse.registration.RegistrationTestData.Game;
import com.lolinhouse.session.FearlessMode;
import com.lolinhouse.session.GameSetRepository;
import com.lolinhouse.session.InhouseSessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 역할: 세트 저장을 두 스레드가 동시에 호출할 때의 경쟁 검증 — 같은 경기(지문)는 하나만 저장되고 나머지는 409 DUPLICATE_SET,
 * 같은 대결의 서로 다른 세트는 대결 행 잠금으로 줄을 서서 둘 다 성공하고 세트 번호가 1, 2가 되며, 같은 작업은 한 번만 저장된다.
 * 스레드마다 별도 트랜잭션이 필요하므로 서비스를 직접 호출한다(테스트 메서드는 트랜잭션 밖).
 */
@IntegrationTest
class SetRegistrationConcurrencyTest {

    @Autowired
    SetRegistrationService registrationService;
    @Autowired
    GameSetRepository gameSets;
    @Autowired
    InhouseSessionService sessions;
    @Autowired
    PlayerService playerService;
    @Autowired
    ExtractionJobRepository jobs;
    @Autowired
    JsonMapper json;

    RegistrationTestData data;

    @BeforeEach
    void setUp() {
        data = new RegistrationTestData(sessions, playerService, jobs, json);
    }

    static List<Object> runConcurrently(List<Callable<Object>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        try {
            CountDownLatch ready = new CountDownLatch(tasks.size());
            CountDownLatch go = new CountDownLatch(1);
            List<Future<Object>> futures = new ArrayList<>();
            for (Callable<Object> task : tasks) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    try {
                        return task.call();
                    } catch (Exception e) {
                        return e;
                    }
                }));
            }
            ready.await();
            go.countDown();
            List<Object> results = new ArrayList<>();
            for (Future<Object> future : futures) {
                try {
                    results.add(future.get());
                } catch (ExecutionException e) {
                    throw new AssertionError(e);
                }
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    static String codeOf(Object result) {
        assertThat(result).isInstanceOf(ApiException.class);
        return ((ApiException) result).code();
    }

    @Test
    void 같은_경기를_서로_다른_대결에_동시에_저장하면_하나만_성공하고_하나는_DUPLICATE_SET이다() throws Exception {
        for (int round = 0; round < 3; round++) {
            long sessionId = data.newSession();
            long duelA = data.newDuel(sessionId, FearlessMode.RESET);
            long duelB = data.newDuel(sessionId, FearlessMode.RESET);
            long[] teamA = data.newPlayers(5);
            long[] teamB = data.newPlayers(5);
            Game game = Game.random();
            SetRegistrationRequest forA = data.request(data.succeededJob(duelA), teamA, teamB, game).build();
            SetRegistrationRequest forB = data.request(data.succeededJob(duelB), teamB, teamA, game).build();

            List<Object> results = runConcurrently(List.of(
                    () -> registrationService.register(duelA, forA),
                    () -> registrationService.register(duelB, forB)));

            assertThat(results.stream().filter(r -> r instanceof SetRegistrationResponse)).hasSize(1);
            Object failure = results.stream().filter(r -> !(r instanceof SetRegistrationResponse)).findFirst()
                    .orElseThrow();
            assertThat(codeOf(failure)).isEqualTo("DUPLICATE_SET");
            assertThat(((ApiException) failure).properties()).containsKeys("existingSetId", "existingDuelId");
        }
    }

    @Test
    void 같은_대결에_서로_다른_세트를_동시에_저장하면_둘_다_성공하고_세트_번호가_1_2다() throws Exception {
        for (int round = 0; round < 3; round++) {
            long duelId = data.newFirstDuel();
            long[] teamA = data.newPlayers(5);
            long[] teamB = data.newPlayers(5);
            SetRegistrationRequest first = data.request(data.succeededJob(duelId), teamA, teamB, Game.random()).build();
            // 두 요청의 챔피언이 겹치지 않아 어느 쪽이 먼저 저장돼도 피어리스를 통과한다
            SetRegistrationRequest second = RegistrationTestData
                    .withOtherChampions(data.request(data.succeededJob(duelId), teamA, teamB, Game.random())).build();

            List<Object> results = runConcurrently(List.of(
                    () -> registrationService.register(duelId, first),
                    () -> registrationService.register(duelId, second)));

            assertThat(results).allMatch(r -> r instanceof SetRegistrationResponse);
            assertThat(results.stream().map(r -> ((SetRegistrationResponse) r).setNo()).sorted().toList())
                    .containsExactly(1, 2);
            assertThat(gameSets.findByDuelIdAndDeletedAtIsNullOrderBySetNoAsc(duelId)).hasSize(2);
        }
    }

    @Test
    void 같은_작업을_동시에_저장하면_한_번만_저장되고_나머지는_EXTRACTION_ALREADY_SAVED다() throws Exception {
        long duelId = data.newFirstDuel();
        long[] teamA = data.newPlayers(5);
        long[] teamB = data.newPlayers(5);
        SetRegistrationRequest request = data.request(data.succeededJob(duelId), teamA, teamB, Game.random()).build();

        List<Object> results = runConcurrently(List.of(
                () -> registrationService.register(duelId, request),
                () -> registrationService.register(duelId, request)));

        assertThat(results.stream().filter(r -> r instanceof SetRegistrationResponse)).hasSize(1);
        assertThat(results.stream().filter(r -> !(r instanceof SetRegistrationResponse)).map(
                SetRegistrationConcurrencyTest::codeOf)).containsExactly("EXTRACTION_ALREADY_SAVED");
        assertThat(gameSets.findByDuelIdAndDeletedAtIsNullOrderBySetNoAsc(duelId)).hasSize(1);
    }

    @Autowired
    org.springframework.transaction.support.TransactionTemplate transactionTemplate;
    @Autowired
    com.lolinhouse.session.InhouseSessionRepository sessionRepository;
    @Autowired
    com.lolinhouse.session.DuelRepository duelRepository;

    @Test
    void 이어가기_대결의_저장은_이전_대결의_미커밋_저장을_기다렸다가_피어리스_위반으로_거절된다() throws Exception {
        long sessionId = data.newSession();
        long duel1 = data.newDuel(sessionId, FearlessMode.RESET);
        long duel2 = data.newDuel(sessionId, FearlessMode.CONTINUE);
        long[] teamA = data.newPlayers(5);
        long[] teamB = data.newPlayers(5);
        // 같은 픽(00~09)이지만 날짜·시간이 달라 지문은 다르다
        SetRegistrationRequest first = data.request(data.succeededJob(duel1), teamA, teamB, Game.random()).build();
        SetRegistrationRequest second = data.request(data.succeededJob(duel2), teamA, teamB, Game.random()).build();

        CountDownLatch firstSaved = new CountDownLatch(1);
        CountDownLatch commitFirst = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            // D1 저장을 커밋 직전에 멈춘다: 바깥 트랜잭션에서 묶음 행을 잠그고 register가 그 트랜잭션에 참여한다
            Future<?> firstTask = pool.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                sessionRepository.findByIdForUpdate(sessionId).orElseThrow();
                registrationService.register(duel1, first);
                firstSaved.countDown();
                try {
                    commitFirst.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
            firstSaved.await();

            Future<Object> secondTask = pool.submit(() -> {
                try {
                    return registrationService.register(duel2, second);
                } catch (Exception e) {
                    return e;
                }
            });
            Thread.sleep(700);
            assertThat(secondTask.isDone()).as("D2 저장은 D1 커밋을 기다려야 한다").isFalse();

            commitFirst.countDown();
            firstTask.get();
            Object result = secondTask.get();

            assertThat(result).isInstanceOf(SetRulesViolatedException.class);
            assertThat(((SetRulesViolatedException) result).violations()).anyMatch(v -> v.contains("피어리스"));
            assertThat(gameSets.findByDuelIdAndDeletedAtIsNullOrderBySetNoAsc(duel1)).hasSize(1);
            assertThat(gameSets.findByDuelIdAndDeletedAtIsNullOrderBySetNoAsc(duel2)).isEmpty();
        } finally {
            commitFirst.countDown();
            pool.shutdownNow();
        }
    }
}
