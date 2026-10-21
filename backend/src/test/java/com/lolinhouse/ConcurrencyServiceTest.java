package com.lolinhouse;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;

import com.lolinhouse.common.web.ApiException;
import com.lolinhouse.domain.Position;
import com.lolinhouse.player.PlayerCreateRequest;
import com.lolinhouse.player.PlayerRepository;
import com.lolinhouse.player.PlayerResponse;
import com.lolinhouse.player.PlayerService;
import com.lolinhouse.player.PlayerUpdateRequest;
import com.lolinhouse.session.DuelRepository;
import com.lolinhouse.session.InhouseSessionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 역할: 서비스를 두 스레드가 동시에 호출할 때의 경쟁 조건(마지막 별칭 삭제, 같은 닉네임 동시 추가,
 * 같은 날짜 묶음·같은 번호 대결 동시 생성)이 정확히 한쪽만 성공하고 나머지는 약속된 409로 끝나는지 검증한다.
 * 스레드마다 별도 트랜잭션·커넥션이 필요하므로 MockMvc가 아니라 서비스를 직접 호출한다(테스트 메서드는 트랜잭션 밖).
 */
@IntegrationTest
class ConcurrencyServiceTest {

    @Autowired
    PlayerService playerService;

    @Autowired
    PlayerRepository playerRepository;

    @Autowired
    InhouseSessionService sessionService;

    @Autowired
    DuelRepository duelRepository;

    @Autowired
    TransactionTemplate transactionTemplate;

    static String unique(String base) {
        return base + UUID.randomUUID().toString().substring(0, 8);
    }

    /** 모든 작업이 준비될 때까지 기다렸다가 동시에 출발시키고, 각 작업의 결과(성공 값 또는 예외)를 모아 돌려준다. */
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
    void concurrentDeleteOfTwoAliasesLeavesExactlyOneAndRejectsTheOtherAsLastAlias() throws Exception {
        for (int round = 0; round < 5; round++) {
            PlayerResponse player = playerService.create(new PlayerCreateRequest("경쟁" + round, Position.MID,
                    List.of(unique("경쟁A"), unique("경쟁B"))));
            long playerId = player.id();
            long first = player.aliases().get(0).id();
            long second = player.aliases().get(1).id();

            List<Object> results = runConcurrently(List.of(
                    () -> {
                        playerService.deleteAlias(playerId, first);
                        return "deleted";
                    },
                    () -> {
                        playerService.deleteAlias(playerId, second);
                        return "deleted";
                    }));

            assertThat(results.stream().filter("deleted"::equals)).hasSize(1);
            assertThat(results.stream().filter(r -> !"deleted".equals(r)).map(ConcurrencyServiceTest::codeOf))
                    .containsExactly("LAST_ALIAS");
            PlayerResponse after = playerService.get(playerId);
            assertThat(after.aliases()).hasSize(1);
            // 남은 별칭이 대표 닉네임이 되어 있어야 한다(삭제한 쪽이 대표였더라도)
            assertThat(playerRepository.findById(playerId).orElseThrow().gameNickname())
                    .isEqualTo(after.aliases().get(0).nickname());
        }
    }

    @Test
    void updateWaitingBehindRepresentativeAliasDeleteDoesNotRestoreTheDeletedNickname() throws Exception {
        // 순서를 강제한다: 삭제 트랜잭션이 선수 행을 잠그고 대표 닉네임을 바꾼 채 커밋을 미루는 동안 수정 요청이 들어온다.
        // 수정이 잠금 없이 예전 행을 읽으면, 삭제 커밋 뒤 늦은 UPDATE가 지워진 닉네임을 되살린다(Codex M3 리뷰 1).
        String representative = unique("대표");
        String other = unique("남는");
        PlayerResponse player = playerService.create(new PlayerCreateRequest("수정경쟁", Position.TOP,
                List.of(representative, other)));
        long playerId = player.id();
        long representativeAliasId = player.aliases().stream()
                .filter(a -> a.nickname().equals(representative)).findFirst().orElseThrow().id();

        CountDownLatch deletedButNotCommitted = new CountDownLatch(1);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<?> delete = pool.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                playerService.deleteAlias(playerId, representativeAliasId);
                deletedButNotCommitted.countDown();
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
            deletedButNotCommitted.await();

            playerService.update(playerId, new PlayerUpdateRequest("수정경쟁2", Position.MID));
            delete.get();
        } finally {
            pool.shutdownNow();
        }

        var after = playerRepository.findById(playerId).orElseThrow();
        assertThat(after.gameNickname()).isEqualTo(other);
        assertThat(after.realName()).isEqualTo("수정경쟁2");
        assertThat(after.mainPosition()).isEqualTo(Position.MID);
    }

    @Test
    void concurrentAddOfSameNicknameToTwoPlayersSucceedsOnceAndConflictsOnce() throws Exception {
        for (int round = 0; round < 5; round++) {
            long playerA = playerService.create(new PlayerCreateRequest(unique("동시A"), Position.TOP,
                    List.of(unique("동시기본")))).id();
            long playerB = playerService.create(new PlayerCreateRequest(unique("동시B"), Position.TOP,
                    List.of(unique("동시기본")))).id();
            String contested = unique("Contested");

            List<Object> results = runConcurrently(List.of(
                    () -> playerService.addAlias(playerA, contested),
                    () -> playerService.addAlias(playerB, contested.toUpperCase())));

            assertThat(results.stream().filter(r -> !(r instanceof Exception))).hasSize(1);
            assertThat(results.stream().filter(r -> r instanceof Exception).map(ConcurrencyServiceTest::codeOf))
                    .containsExactly("ALIAS_CONFLICT");
        }
    }

    @Test
    void concurrentCreateOnSameDateSucceedsOnceAndReportsExistingSessionId() throws Exception {
        for (int round = 0; round < 5; round++) {
            LocalDate date = LocalDate.of(4000, 1, 1).plusDays(ThreadLocalRandom.current().nextInt(1_000_000));
            List<Object> results = runConcurrently(List.of(
                    () -> sessionService.create(date),
                    () -> sessionService.create(date)));

            assertThat(results.stream().filter(r -> !(r instanceof Exception))).hasSize(1);
            Object failure = results.stream().filter(r -> r instanceof Exception).findFirst().orElseThrow();
            assertThat(codeOf(failure)).isEqualTo("SESSION_DATE_CONFLICT");
            Object winner = results.stream().filter(r -> !(r instanceof Exception)).findFirst().orElseThrow();
            long winnerId = ((com.lolinhouse.session.InhouseSessionSummaryResponse) winner).id();
            assertThat(((ApiException) failure).properties()).containsEntry("existingSessionId", winnerId);
        }
    }

    @Test
    void concurrentDuelCreationNeverProducesDuplicateSeq() throws Exception {
        long sessionId = sessionService.create(
                LocalDate.of(5000, 1, 1).plusDays(ThreadLocalRandom.current().nextInt(1_000_000))).id();
        for (int round = 0; round < 5; round++) {
            List<Object> results = runConcurrently(List.of(
                    () -> sessionService.createDuel(sessionId),
                    () -> sessionService.createDuel(sessionId),
                    () -> sessionService.createDuel(sessionId)));

            assertThat(results.stream().filter(r -> !(r instanceof Exception))).isNotEmpty();
            assertThat(results.stream().filter(r -> r instanceof Exception).map(ConcurrencyServiceTest::codeOf))
                    .allMatch("DUEL_SEQ_CONFLICT"::equals);
        }
        List<Integer> seqs = duelRepository.findBySessionIdOrderBySeqAsc(sessionId).stream()
                .map(d -> d.seq()).toList();
        assertThat(seqs).doesNotHaveDuplicates().isSorted();
        assertThat(seqs.get(0)).isEqualTo(1);
        assertThat(seqs.get(seqs.size() - 1)).isEqualTo(seqs.size()); // 빈 번호 없이 1..n
    }
}
