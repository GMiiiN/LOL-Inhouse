package com.lolinhouse.edit;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;

import com.jayway.jsonpath.JsonPath;
import com.lolinhouse.IntegrationTest;
import com.lolinhouse.champion.CatalogSnapshot;
import com.lolinhouse.champion.Champion;
import com.lolinhouse.champion.ChampionCatalog;
import com.lolinhouse.common.web.ApiException;
import com.lolinhouse.domain.DuelTeam;
import com.lolinhouse.domain.Side;
import com.lolinhouse.ingest.image.ImageStore;
import com.lolinhouse.player.PlayerCreateRequest;
import com.lolinhouse.player.PlayerService;
import com.lolinhouse.session.FearlessMode;
import com.lolinhouse.session.GameSet;
import com.lolinhouse.session.GameSet.DisplayedTotals;
import com.lolinhouse.session.GameSetRepository;
import com.lolinhouse.session.InhouseSessionService;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 역할: 세트 삭제·복구·삭제 목록 API(M6 PR-A)를 실제 MySQL로 검증한다. 세트는 가져오기 API로 만든다.
 * 공유 DB라 묶음 시작일은 7000~7049년에서 무작위로 고른다.
 */
@IntegrationTest
@AutoConfigureMockMvc
@WithMockUser(roles = "ADMIN")
class SetLifecycleApiTest {

    private static final List<String> POSITIONS = List.of("TOP", "JUNGLE", "MID", "ADC", "SUPPORT");

    @Autowired
    MockMvc mockMvc;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    JsonMapper json;
    @Autowired
    InhouseSessionService sessions;
    @Autowired
    PlayerService playerService;
    @Autowired
    SetLifecycleService lifecycle;
    @Autowired
    EntityManagerFactory entityManagerFactory;
    @Autowired
    GameSetRepository gameSets;
    @Autowired
    ImageStore imageStore;

    @MockitoBean
    ChampionCatalog catalog;

    @BeforeEach
    void setUp() {
        List<Champion> champions = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            champions.add(new Champion(champion(i), String.valueOf(i + 1), "챔피언" + i));
        }
        when(catalog.current()).thenReturn(Optional.of(new CatalogSnapshot("16.20.1", champions, (crop, ban) -> null)));
    }

    // ----- 데이터 준비 -----

    static String champion(int index) {
        return String.format("Champion%02d", index);
    }

    record Ctx(long sessionId, String sessionDate, long duelId, long[] a, long[] b, String shotDate) {
    }

    Ctx newCtx(FearlessMode mode) {
        for (int attempt = 0; attempt < 50; attempt++) {
            LocalDate date = LocalDate.of(7000, 1, 1).plusDays(ThreadLocalRandom.current().nextInt(50 * 365));
            try {
                long sessionId = sessions.create(date).id();
                return new Ctx(sessionId, date.toString(), sessions.createDuel(sessionId, mode).id(), newPlayers(),
                        newPlayers(), date.toString());
            } catch (ApiException e) {
                // 같은 시작일 묶음이 이미 있으면 다른 날짜로 다시
            }
        }
        throw new IllegalStateException("빈 날짜를 찾지 못했습니다.");
    }

    long newDuel(Ctx ctx, FearlessMode mode) {
        return sessions.createDuel(ctx.sessionId(), mode).id();
    }

    long[] newPlayers() {
        long[] ids = new long[5];
        for (int i = 0; i < 5; i++) {
            String suffix = UUID.randomUUID().toString().substring(0, 8);
            ids[i] = playerService.create(new PlayerCreateRequest("실명" + suffix, null, List.of("선수" + suffix))).id();
        }
        return ids;
    }

    Map<String, Object> body(String date, String winner, long[] blue, long[] red, int pickBase, int banBase) {
        List<Map<String, Object>> players = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            players.add(player(blue[i], "BLUE", i, pickBase + i));
            players.add(player(red[i], "RED", i, pickBase + 5 + i));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("shotDate", date);
        body.put("durationSec", null);
        body.put("winnerSide", winner);
        body.put("bans", Map.of(
                "BLUE", List.of(champion(banBase), champion(banBase + 1), champion(banBase + 2),
                        champion(banBase + 3), champion(banBase + 4)),
                "RED", List.of(champion(banBase + 5), champion(banBase + 6), champion(banBase + 7),
                        champion(banBase + 8), champion(banBase + 9))));
        body.put("players", players);
        return body;
    }

    Map<String, Object> player(long id, String side, int lane, int pick) {
        Map<String, Object> player = new LinkedHashMap<>();
        player.put("playerId", id);
        player.put("side", side);
        player.put("position", POSITIONS.get(lane));
        player.put("championKey", champion(pick));
        player.put("kills", 3 + lane + pick);
        player.put("deaths", 2);
        player.put("assists", 5);
        player.put("cs", 150 + lane);
        player.put("gold", 9000 + 100 * lane);
        return player;
    }

    ResultActions importSet(long duelId, Map<String, Object> body) throws Exception {
        return mockMvc.perform(post("/api/admin/duels/" + duelId + "/sets/import").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }

    long importedId(long duelId, Map<String, Object> body) throws Exception {
        String response = importSet(duelId, body).andExpect(status().isCreated()).andReturn().getResponse()
                .getContentAsString();
        return ((Number) JsonPath.read(response, "$.setId")).longValue();
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> deletedRow(long setId) throws Exception {
        String response = mockMvc.perform(get("/api/admin/deleted-sets")).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString();
        List<Map<String, Object>> rows = json.readValue(response, List.class);
        return rows.stream().filter(r -> ((Number) r.get("setId")).longValue() == setId).findFirst().orElseThrow();
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> blockersOf(Map<String, Object> row) {
        return (Map<String, Object>) row.get("blockers");
    }

    ResultActions deleteSet(long setId) throws Exception {
        return mockMvc.perform(delete("/api/admin/sets/" + setId).with(csrf()));
    }

    ResultActions restoreSet(long setId) throws Exception {
        return mockMvc.perform(post("/api/admin/sets/" + setId + "/restore").with(csrf()));
    }

    // ----- 삭제 -----

    @Test
    void 삭제하면_204이고_공개_조회_통계_등록_맥락에서_빠지고_복구하면_원상태가_된다() throws Exception {
        Ctx ctx = newCtx(FearlessMode.RESET);
        long first = importedId(ctx.duelId(), body(ctx.shotDate(), "BLUE", ctx.a(), ctx.b(), 0, 30));
        long second = importedId(ctx.duelId(), body(ctx.shotDate(), "BLUE", ctx.b(), ctx.a(), 10, 30));
        String contextUrl = "/api/admin/duels/" + ctx.duelId() + "/registration-context";
        String recordsUrl = "/api/records?from=" + ctx.sessionDate() + "&to=" + ctx.sessionDate();

        mockMvc.perform(get(contextUrl)).andExpect(jsonPath("$.nextSetNo").value(3))
                .andExpect(jsonPath("$.restrictedChampions.length()").value(20));
        mockMvc.perform(get("/api/duels/" + ctx.duelId())).andExpect(jsonPath("$.totalSets").value(2));

        deleteSet(second).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/sets/" + second)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/sets/" + first)).andExpect(status().isOk());
        mockMvc.perform(get(recordsUrl)).andExpect(jsonPath("$.totals.sets").value(1));
        mockMvc.perform(get("/api/duels/" + ctx.duelId())).andExpect(jsonPath("$.totalSets").value(1))
                .andExpect(jsonPath("$.score.TEAM1").value(1)).andExpect(jsonPath("$.score.TEAM2").value(0));
        mockMvc.perform(get(contextUrl)).andExpect(jsonPath("$.nextSetNo").value(2))
                .andExpect(jsonPath("$.restrictedChampions.length()").value(10));
        assertThat(jdbc.queryForObject("select deleted_at is not null from game_set where id = ?", Boolean.class,
                second)).isTrue();

        restoreSet(second).andExpect(status().isOk()).andExpect(jsonPath("$.setId").value(second))
                .andExpect(jsonPath("$.warnings.length()").value(0))
                .andExpect(jsonPath("$.fearlessConflicts.length()").value(0));

        mockMvc.perform(get("/api/sets/" + second)).andExpect(status().isOk());
        mockMvc.perform(get(recordsUrl)).andExpect(jsonPath("$.totals.sets").value(2));
        mockMvc.perform(get("/api/duels/" + ctx.duelId())).andExpect(jsonPath("$.score.TEAM1").value(1))
                .andExpect(jsonPath("$.score.TEAM2").value(1));
        mockMvc.perform(get(contextUrl)).andExpect(jsonPath("$.nextSetNo").value(3))
                .andExpect(jsonPath("$.restrictedChampions.length()").value(20));
    }

    @Test
    void 이미_삭제된_세트를_다시_삭제하면_409_없는_세트는_404() throws Exception {
        Ctx ctx = newCtx(FearlessMode.RESET);
        long setId = importedId(ctx.duelId(), body(ctx.shotDate(), "BLUE", ctx.a(), ctx.b(), 0, 30));

        deleteSet(setId).andExpect(status().isNoContent());
        deleteSet(setId).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SET_ALREADY_DELETED"));
        deleteSet(Long.MAX_VALUE).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("SET_NOT_FOUND"));
        restoreSet(Long.MAX_VALUE).andExpect(status().isNotFound());
    }

    @Test
    void 삭제되지_않은_세트를_복구하면_409_SET_NOT_DELETED() throws Exception {
        Ctx ctx = newCtx(FearlessMode.RESET);
        long setId = importedId(ctx.duelId(), body(ctx.shotDate(), "BLUE", ctx.a(), ctx.b(), 0, 30));

        restoreSet(setId).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SET_NOT_DELETED"));
    }

    // ----- 복구 충돌 -----

    @Test
    void 같은_경기를_다시_등록한_뒤_복구하면_409_RESTORE_FINGERPRINT_CONFLICT와_기존_세트_정보() throws Exception {
        Ctx ctx = newCtx(FearlessMode.RESET);
        long otherDuel = newDuel(ctx, FearlessMode.RESET);
        Map<String, Object> same = body(ctx.shotDate(), "BLUE", ctx.a(), ctx.b(), 0, 30);
        long original = importedId(ctx.duelId(), same);
        deleteSet(original).andExpect(status().isNoContent());
        long replacement = importedId(otherDuel, same);

        restoreSet(original).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESTORE_FINGERPRINT_CONFLICT"))
                .andExpect(jsonPath("$.existingSetId").value(replacement))
                .andExpect(jsonPath("$.existingDuelId").value(otherDuel))
                .andExpect(jsonPath("$.existingSetNo").value(1));
        assertThat(jdbc.queryForObject("select deleted_at is not null from game_set where id = ?", Boolean.class,
                original)).isTrue();

        Map<String, Object> blockers = blockersOf(deletedRow(original));
        assertThat(((Number) blockers.get("fingerprintSetId")).longValue()).isEqualTo(replacement);
        assertThat(blockers.get("setNoSetId")).isNull();
    }

    @Test
    void 마지막_세트를_지우고_같은_번호로_다시_등록한_뒤_복구하면_409_RESTORE_SET_NO_CONFLICT() throws Exception {
        Ctx ctx = newCtx(FearlessMode.RESET);
        long original = importedId(ctx.duelId(), body(ctx.shotDate(), "BLUE", ctx.a(), ctx.b(), 0, 30));
        deleteSet(original).andExpect(status().isNoContent());
        long replacement = importedId(ctx.duelId(), body(ctx.shotDate(), "RED", ctx.a(), ctx.b(), 10, 30));
        assertThat(jdbc.queryForObject("select set_no from game_set where id = ?", Integer.class, replacement))
                .isEqualTo(1);

        restoreSet(original).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESTORE_SET_NO_CONFLICT"))
                .andExpect(jsonPath("$.existingSetId").value(replacement));

        Map<String, Object> blockers = blockersOf(deletedRow(original));
        assertThat(((Number) blockers.get("setNoSetId")).longValue()).isEqualTo(replacement);
        assertThat(blockers.get("fingerprintSetId")).isNull();
    }

    @Test
    void 피어리스가_겹치는_복구는_200과_경고로_허용된다() throws Exception {
        Ctx ctx = newCtx(FearlessMode.RESET);
        long first = importedId(ctx.duelId(), body(ctx.shotDate(), "BLUE", ctx.a(), ctx.b(), 0, 30));
        importedId(ctx.duelId(), body(ctx.shotDate(), "RED", ctx.b(), ctx.a(), 10, 30));
        deleteSet(first).andExpect(status().isNoContent());
        // 1세트가 빠진 동안 3세트가 1세트와 같은 픽(0~9)을 쓴다
        Map<String, Object> thirdBody = body(ctx.shotDate(), "BLUE", ctx.a(), ctx.b(), 0, 20);
        @SuppressWarnings("unchecked")
        Map<String, Object> firstPlayer = ((List<Map<String, Object>>) thirdBody.get("players")).get(0);
        firstPlayer.put("kills", 40); // 지문이 1세트와 같아지지 않게
        long third = importedId(ctx.duelId(), thirdBody);
        assertThat(jdbc.queryForObject("select set_no from game_set where id = ?", Integer.class, third))
                .isEqualTo(3);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> listed = (List<Map<String, Object>>) deletedRow(first).get("fearlessConflicts");
        assertThat(listed).hasSize(10);
        assertThat(((Number) listed.get(0).get("setId")).longValue()).isEqualTo(third);

        restoreSet(first).andExpect(status().isOk()).andExpect(jsonPath("$.warnings.length()").value(1))
                .andExpect(jsonPath("$.fearlessConflicts.length()").value(10))
                .andExpect(jsonPath("$.fearlessConflicts[0].setId").value(third))
                .andExpect(jsonPath("$.fearlessConflicts[0].duelId").value(ctx.duelId()))
                .andExpect(jsonPath("$.fearlessConflicts[0].duelSeq").value(1))
                .andExpect(jsonPath("$.fearlessConflicts[0].setNo").value(3))
                .andExpect(jsonPath("$.fearlessConflicts[0].championKey").value(champion(0)));
    }

    // ----- 삭제 목록 -----

    @Test
    void 삭제_목록은_최근_삭제순이고_세트_정보를_담는다() throws Exception {
        Ctx ctx = newCtx(FearlessMode.RESET);
        long first = importedId(ctx.duelId(), body(ctx.shotDate(), "BLUE", ctx.a(), ctx.b(), 0, 30));
        long second = importedId(ctx.duelId(), body(ctx.shotDate(), "RED", ctx.b(), ctx.a(), 10, 30));
        deleteSet(first).andExpect(status().isNoContent());
        deleteSet(second).andExpect(status().isNoContent());

        String response = mockMvc.perform(get("/api/admin/deleted-sets")).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString();
        List<Integer> ids = JsonPath.read(response, "$[*].setId");
        assertThat(ids.indexOf((int) second)).isLessThan(ids.indexOf((int) first));
        assertThat(ids.indexOf((int) first)).isGreaterThanOrEqualTo(0);

        Map<String, Object> row = deletedRow(first);
        assertThat(((Number) row.get("duelId")).longValue()).isEqualTo(ctx.duelId());
        assertThat(row.get("duelSeq")).isEqualTo(1);
        assertThat(((Number) row.get("sessionId")).longValue()).isEqualTo(ctx.sessionId());
        assertThat(row.get("sessionStartDate")).isEqualTo(ctx.sessionDate());
        assertThat(row.get("setNo")).isEqualTo(1);
        assertThat(row.get("shotDate")).isEqualTo(ctx.shotDate());
        assertThat(row.get("winnerTeam")).isEqualTo("TEAM1");
        assertThat(row.get("team1Side")).isEqualTo("BLUE");
        assertThat(row.get("durationSec")).isNull();
        assertThat(row.get("deletedAt")).isNotNull();
        assertThat(row).containsKeys("team1Kills", "team2Kills");
    }

    @Test
    void 삭제_목록_쿼리_수는_삭제_세트_수와_무관하다() throws Exception {
        Ctx one = newCtx(FearlessMode.RESET);
        deleteSet(importedId(one.duelId(), body(one.shotDate(), "BLUE", one.a(), one.b(), 0, 30)))
                .andExpect(status().isNoContent());
        SessionFactory factory = entityManagerFactory.unwrap(SessionFactory.class);
        factory.getStatistics().setStatisticsEnabled(true);

        factory.getStatistics().clear();
        lifecycle.deletedSets();
        long before = factory.getStatistics().getPrepareStatementCount();

        for (int i = 0; i < 3; i++) {
            Ctx ctx = newCtx(FearlessMode.RESET);
            long id = importedId(ctx.duelId(), body(ctx.shotDate(), "BLUE", ctx.a(), ctx.b(), 0, 30));
            deleteSet(id).andExpect(status().isNoContent());
        }
        factory.getStatistics().clear();
        lifecycle.deletedSets();
        long after = factory.getStatistics().getPrepareStatementCount();

        assertThat(after).isEqualTo(before);
        factory.getStatistics().setStatisticsEnabled(false);
    }

    // ----- 이미지·보안·동시성 -----

    @Test
    void 삭제된_세트도_담당자는_원본_이미지를_볼_수_있다() throws Exception {
        Ctx ctx = newCtx(FearlessMode.RESET);
        byte[] bytes = new byte[] {(byte) 0x89, 'P', 'N', 'G', 1, 2, 3};
        String hash = (UUID.randomUUID().toString() + UUID.randomUUID()).replace("-", "").substring(0, 64);
        String key = imageStore.put(hash, "image/png", bytes);
        GameSet set = gameSets.saveAndFlush(GameSet.create(ctx.duelId(), 1, Side.BLUE, DuelTeam.TEAM1,
                DuelTeam.TEAM1, LocalDate.parse(ctx.shotDate()), 777, new DisplayedTotals(1, 2, 3, 4),
                new DisplayedTotals(5, 6, 7, 8), hash, key));
        deleteSet(set.id()).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/admin/sets/" + set.id() + "/image")).andExpect(status().isOk())
                .andExpect(content().bytes(bytes));
    }

    @Test
    @WithAnonymousUser
    void 익명은_삭제_복구_목록_모두_401() throws Exception {
        mockMvc.perform(delete("/api/admin/sets/1").with(csrf())).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/admin/sets/1/restore").with(csrf())).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/deleted-sets")).andExpect(status().isUnauthorized());
    }

    @Test
    void CSRF_없이_삭제_복구하면_403() throws Exception {
        mockMvc.perform(delete("/api/admin/sets/1")).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/sets/1/restore")).andExpect(status().isForbidden());
    }

    @Test
    void 같은_세트를_동시에_삭제하면_하나만_성공하고_나머지는_409() throws Exception {
        Ctx ctx = newCtx(FearlessMode.RESET);
        long setId = importedId(ctx.duelId(), body(ctx.shotDate(), "BLUE", ctx.a(), ctx.b(), 0, 30));
        int threads = 4;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<String>> results = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            Callable<String> task = () -> {
                ready.countDown();
                go.await();
                try {
                    lifecycle.delete(setId);
                    return "OK";
                } catch (ApiException e) {
                    return e.code();
                }
            };
            results.add(pool.submit(task));
        }
        ready.await();
        go.countDown();
        List<String> outcomes = new ArrayList<>();
        for (Future<String> result : results) {
            outcomes.add(result.get());
        }
        pool.shutdown();

        assertThat(outcomes.stream().filter("OK"::equals)).hasSize(1);
        assertThat(outcomes.stream().filter("SET_ALREADY_DELETED"::equals)).hasSize(threads - 1);
    }
}
