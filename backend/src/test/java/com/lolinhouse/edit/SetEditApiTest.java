package com.lolinhouse.edit;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import com.jayway.jsonpath.JsonPath;
import com.lolinhouse.IntegrationTest;
import com.lolinhouse.champion.CatalogSnapshot;
import com.lolinhouse.champion.Champion;
import com.lolinhouse.champion.ChampionCatalog;
import com.lolinhouse.common.web.ApiException;
import com.lolinhouse.player.PlayerCreateRequest;
import com.lolinhouse.player.PlayerService;
import com.lolinhouse.session.FearlessMode;
import com.lolinhouse.session.InhouseSessionService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 역할: 저장된 세트 수정 API(M6 PR-B: GET form, POST check, PUT)를 실제 MySQL로 검증한다. 세트는 가져오기 API로 만든다.
 * 공유 DB라 묶음 시작일은 7050~7099년에서 무작위로 고른다.
 */
@IntegrationTest
@AutoConfigureMockMvc
@WithMockUser(roles = "ADMIN")
class SetEditApiTest {

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

    record Ctx(long sessionId, String date, long duelId, long[] a, long[] b) {
    }

    Ctx newCtx(FearlessMode mode) {
        for (int attempt = 0; attempt < 50; attempt++) {
            LocalDate date = LocalDate.of(7050, 1, 1).plusDays(ThreadLocalRandom.current().nextInt(50 * 365));
            try {
                long sessionId = sessions.create(date).id();
                return new Ctx(sessionId, date.toString(), sessions.createDuel(sessionId, mode).id(), newPlayers(),
                        newPlayers());
            } catch (ApiException e) {
                // 같은 시작일 묶음이 이미 있으면 다른 날짜로 다시
            }
        }
        throw new IllegalStateException("빈 날짜를 찾지 못했습니다.");
    }

    long[] newPlayers() {
        long[] ids = new long[5];
        for (int i = 0; i < 5; i++) {
            String suffix = UUID.randomUUID().toString().substring(0, 8);
            ids[i] = playerService.create(new PlayerCreateRequest("실명" + suffix, null, List.of("선수" + suffix))).id();
        }
        return ids;
    }

    Map<String, Object> importBody(String date, Integer durationSec, String winner, long[] blue, long[] red,
            int pickBase, int banBase) {
        List<Map<String, Object>> players = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            players.add(player(blue[i], "BLUE", i, pickBase + i));
            players.add(player(red[i], "RED", i, pickBase + 5 + i));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("shotDate", date);
        body.put("durationSec", durationSec);
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

    long importSet(long duelId, Map<String, Object> body) throws Exception {
        String response = mockMvc.perform(post("/api/admin/duels/" + duelId + "/sets/import").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.setId")).longValue();
    }

    /** 세트 1(블루 = a팀, 픽 0~9, 밴 30~39, 시간 모름). */
    long firstSet(Ctx ctx) throws Exception {
        return importSet(ctx.duelId(), importBody(ctx.date(), null, "BLUE", ctx.a(), ctx.b(), 0, 30));
    }

    /** 세트 2(블루 = b팀, 픽 10~19, 밴 30~39, 시간 25:42). */
    long secondSet(Ctx ctx) throws Exception {
        return importSet(ctx.duelId(), importBody(ctx.date(), 1542, "BLUE", ctx.b(), ctx.a(), 10, 30));
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> formOf(long setId) throws Exception {
        String response = mockMvc.perform(get("/api/admin/sets/" + setId + "/form")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readValue(response, Map.class);
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> editBody(Map<String, Object> formResponse) {
        return (Map<String, Object>) formResponse.get("form");
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> row(Map<String, Object> body, String team, int index) {
        return ((List<Map<String, Object>>) ((Map<String, Object>) body.get(team)).get("rows")).get(index);
    }

    ResultActions putSet(long setId, Map<String, Object> body) throws Exception {
        return mockMvc.perform(put("/api/admin/sets/" + setId).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)));
    }

    ResultActions checkSet(long setId, Map<String, Object> body) throws Exception {
        return mockMvc.perform(post("/api/admin/sets/" + setId + "/check").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }

    String dbChampion(long setId, long playerId) {
        return jdbc.queryForObject("select champion_key from game_set_player where game_set_id = ? and player_id = ?",
                String.class, setId, playerId);
    }

    // ----- 폼 -----

    @Test
    void 폼은_저장된_세트를_등록_명령_모양으로_펼치고_그대로_검사하면_오류가_없다() throws Exception {
        Ctx ctx = newCtx(FearlessMode.RESET);
        long first = firstSet(ctx);
        long second = secondSet(ctx);

        for (long setId : new long[] {first, second}) {
            Map<String, Object> response = formOf(setId);
            Map<String, Object> form = editBody(response);
            assertThat(response.get("deleted")).isEqualTo(false);
            assertThat(response.get("hasImage")).isEqualTo(false);
            assertThat(response.get("fearlessConflicts")).isEqualTo(List.of());
            assertThat(form.keySet()).containsExactlyInAnyOrder("topSide", "topResult", "shotDate", "duration", "top",
                    "bottom", "expectedUpdatedAt");
            assertThat(form.get("expectedUpdatedAt")).isEqualTo(response.get("updatedAt"));
            for (int i = 0; i < 5; i++) {
                assertThat(row(form, "top", i).get("screenRow")).isEqualTo(i + 1);
                assertThat(row(form, "top", i).get("resolution")).isEqualTo("EXISTING");
                assertThat(row(form, "top", i).get("addAlias")).isEqualTo(false);
                assertThat((String) row(form, "bottom", i).get("nickname")).isNotBlank();
            }
            checkSet(setId, form).andExpect(status().isOk()).andExpect(jsonPath("$.errors.length()").value(0))
                    .andExpect(jsonPath("$.teamChange.kind").value("NONE"))
                    .andExpect(jsonPath("$.fearlessConflicts.length()").value(0));
        }
        assertThat(editBody(formOf(first)).get("duration")).isEqualTo("");
        assertThat(editBody(formOf(second)).get("duration")).isEqualTo("25:42");
        assertThat(editBody(formOf(second)).get("topSide")).isEqualTo("BLUE");
        assertThat(formOf(second).get("setNo")).isEqualTo(2);
    }

    @Test
    void 없는_세트의_폼은_404_삭제된_세트의_폼은_200과_deleted_true() throws Exception {
        mockMvc.perform(get("/api/admin/sets/" + Long.MAX_VALUE + "/form")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SET_NOT_FOUND"));
        Ctx ctx = newCtx(FearlessMode.RESET);
        long first = firstSet(ctx);
        mockMvc.perform(delete("/api/admin/sets/" + first).with(csrf())).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/admin/sets/" + first + "/form")).andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted").value(true));
    }

    // ----- 저장 -----

    @Test
    void 수정하면_세트_정보와_승패가_공개_조회에_반영된다() throws Exception {
        Ctx ctx = newCtx(FearlessMode.RESET);
        long first = firstSet(ctx);
        Map<String, Object> response = formOf(first);
        Map<String, Object> form = editBody(response);
        form.put("topResult", "LOSS");
        form.put("duration", "30:10");
        row(form, "top", 0).put("championKey", champion(20));
        row(form, "top", 0).put("kills", 99);

        putSet(first, form).andExpect(status().isOk()).andExpect(jsonPath("$.setId").value(first))
                .andExpect(jsonPath("$.topTeam").value("TEAM1")).andExpect(jsonPath("$.createdPlayers.length()").value(0))
                .andExpect(jsonPath("$.addedAliases.length()").value(0));

        assertThat(dbChampion(first, ctx.a()[0])).isEqualTo(champion(20));
        assertThat(jdbc.queryForObject("select duration_sec from game_set where id = ?", Integer.class, first))
                .isEqualTo(1810);
        assertThat(jdbc.queryForObject("select fingerprint from game_set where id = ?", String.class, first))
                .contains("|1810|");
        mockMvc.perform(get("/api/sets/" + first)).andExpect(status().isOk());
        mockMvc.perform(get("/api/duels/" + ctx.duelId())).andExpect(jsonPath("$.score.TEAM1").value(0))
                .andExpect(jsonPath("$.score.TEAM2").value(1));
        assertThat(jdbc.queryForObject("select count(*) from game_set_player where game_set_id = ?", Integer.class,
                first)).isEqualTo(10);
        assertThat(jdbc.queryForObject("select count(*) from game_set_ban where game_set_id = ?", Integer.class,
                first)).isEqualTo(10);
        assertThat(jdbc.queryForObject("select image_hash is null and set_no = 1 from game_set where id = ?",
                Boolean.class, first)).isTrue();
    }

    @Test
    void 시간_모름_세트에_시간을_넣으면_지문이_바뀌고_다시_모름으로_돌릴_수_있다() throws Exception {
        Ctx ctx = newCtx(FearlessMode.RESET);
        long first = firstSet(ctx);
        Map<String, Object> form = editBody(formOf(first));
        form.put("duration", "20:00");
        putSet(first, form).andExpect(status().isOk());
        form = editBody(formOf(first));
        form.put("duration", "");
        putSet(first, form).andExpect(status().isOk());

        assertThat(jdbc.queryForObject("select duration_sec is null from game_set where id = ?", Boolean.class, first))
                .isTrue();
        assertThat(jdbc.queryForObject("select fingerprint from game_set where id = ?", String.class, first))
                .contains("|?|");
    }

    @Test
    void 바꾸지_않고_저장해도_자기_지문은_중복이_아니라_200이고_updatedAt이_정밀도_손실없이_왕복한다() throws Exception {
        Ctx ctx = newCtx(FearlessMode.RESET);
        long first = firstSet(ctx);
        Map<String, Object> response = formOf(first);
        Instant before = Instant.parse((String) response.get("updatedAt"));

        putSet(first, editBody(response)).andExpect(status().isOk());

        Map<String, Object> after = formOf(first);
        assertThat(Instant.parse((String) after.get("updatedAt"))).isAfter(before);
        assertThat(jdbc.queryForObject("select updated_at from game_set where id = ?", java.sql.Timestamp.class, first)
                .toInstant()).isEqualTo(Instant.parse((String) after.get("updatedAt")));
    }

    @Test
    @SuppressWarnings("unchecked")
    void 선수와_밴만_바꿔도_updatedAt이_갱신되고_낡은_expectedUpdatedAt은_409() throws Exception {
        Ctx ctx = newCtx(FearlessMode.RESET);
        long first = firstSet(ctx);
        Map<String, Object> response = formOf(first);
        Map<String, Object> form = editBody(response);
        ((List<String>) ((Map<String, Object>) form.get("top")).get("bans")).set(0, champion(25));
        @SuppressWarnings("unchecked")
        Map<String, Object> stale = json.readValue(json.writeValueAsString(form), Map.class);

        putSet(first, form).andExpect(status().isOk());

        assertThat(Instant.parse((String) formOf(first).get("updatedAt")))
                .isAfter(Instant.parse((String) response.get("updatedAt")));
        putSet(first, stale).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SET_MODIFIED"));
    }

    @Test
    void 같은_팀_포지션_맞바꿈은_UNIQUE_함정_없이_200() throws Exception {
        Ctx ctx = newCtx(FearlessMode.RESET);
        long first = firstSet(ctx);
        Map<String, Object> form = editBody(formOf(first));
        Object p0 = row(form, "top", 0).get("position");
        row(form, "top", 0).put("position", row(form, "top", 1).get("position"));
        row(form, "top", 1).put("position", p0);

        putSet(first, form).andExpect(status().isOk());

        assertThat(jdbc.queryForObject("select position from game_set_player where game_set_id = ? and player_id = ?",
                String.class, first, ctx.a()[0])).isEqualTo("JUNGLE");
        assertThat(jdbc.queryForObject("select position from game_set_player where game_set_id = ? and player_id = ?",
                String.class, first, ctx.a()[1])).isEqualTo("TOP");
    }

    @Test
    void 두_팀_사이에서_선수를_바꿔도_200이고_A팀_판별이_유지된다() throws Exception {
        Ctx ctx = newCtx(FearlessMode.RESET);
        long first = firstSet(ctx);
        Map<String, Object> form = editBody(formOf(first));
        Object topId = row(form, "top", 0).get("playerId");
        Object bottomId = row(form, "bottom", 0).get("playerId");
        row(form, "top", 0).put("playerId", bottomId);
        row(form, "bottom", 0).put("playerId", topId);

        putSet(first, form).andExpect(status().isOk()).andExpect(jsonPath("$.topTeam").value("TEAM1"));

        assertThat(jdbc.queryForObject("select team from game_set_player where game_set_id = ? and player_id = ?",
                String.class, first, ctx.b()[0])).isEqualTo("TEAM1");
        assertThat(jdbc.queryForObject("select team from game_set_player where game_set_id = ? and player_id = ?",
                String.class, first, ctx.a()[0])).isEqualTo("TEAM2");
    }

    @Test
    void 위쪽_진영과_팀을_통째로_바꾸는_수정은_선수를_따라가고_첫_세트_진영_경고가_나온다() throws Exception {
        Ctx ctx = newCtx(FearlessMode.RESET);
        long first = firstSet(ctx);
        Map<String, Object> form = editBody(formOf(first));
        form.put("topSide", "RED");

        putSet(first, form).andExpect(status().isOk()).andExpect(jsonPath("$.topTeam").value("TEAM1"))
                .andExpect(jsonPath("$.warnings.length()").value(1));

        assertThat(jdbc.queryForObject("select team1_side from game_set where id = ?", String.class, first))
                .isEqualTo("RED");
        assertThat(jdbc.queryForObject("select count(*) from game_set_ban where game_set_id = ? and side = 'RED' "
                + "and turn = 2 and champion_key = ?", Integer.class, first, champion(30))).isEqualTo(1);
    }

    // ----- 지문 -----

    @Test
    void 다른_세트와_같은_지문이_되면_409_DUPLICATE_SET과_기존_세트_정보() throws Exception {
        Ctx ctx = newCtx(FearlessMode.RESET);
        long first = firstSet(ctx);
        long second = importSet(ctx.duelId(), importBody(ctx.date(), null, "BLUE", ctx.b(), ctx.a(), 10, 30));
        Map<String, Object> source = editBody(formOf(first));
        Map<String, Object> form = editBody(formOf(second));
        form.put("duration", "");
        copyTotalsFrom(source, form);

        putSet(second, form).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_SET"))
                .andExpect(jsonPath("$.existingSetId").value(first));
    }

    @SuppressWarnings("unchecked")
    private void copyTotalsFrom(Map<String, Object> source, Map<String, Object> to) {
        for (String team : List.of("top", "bottom")) {
            Map<String, Object> from = (Map<String, Object>) source.get(team);
            Map<String, Object> target = (Map<String, Object>) to.get(team);
            for (String key : List.of("displayedKills", "displayedGold")) {
                target.put(key, from.get(key));
            }
        }
    }

    // ----- 피어리스 -----

    @Test
    void 앞_세트와_픽이_겹치면_422이고_아무것도_바뀌지_않는다() throws Exception {
        Ctx ctx = newCtx(FearlessMode.RESET);
        firstSet(ctx);
        long second = secondSet(ctx);
        Map<String, Object> response = formOf(second);
        Map<String, Object> form = editBody(response);
        row(form, "top", 0).put("championKey", champion(0));

        putSet(second, form).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SET_RULES_VIOLATED"))
                .andExpect(jsonPath("$.violations.length()").value(1))
                .andExpect(jsonPath("$.warnings").isArray());

        Map<String, Object> after = formOf(second);
        assertThat(after.get("updatedAt")).isEqualTo(response.get("updatedAt"));
        assertThat(dbChampion(second, ctx.b()[0])).isEqualTo(champion(10));
        checkSet(second, form).andExpect(status().isOk()).andExpect(jsonPath("$.errors.length()").value(1));
    }

    @Test
    void 뒤_세트와_겹치면_200과_경고_충돌_목록() throws Exception {
        Ctx ctx = newCtx(FearlessMode.RESET);
        long first = firstSet(ctx);
        long second = secondSet(ctx);
        Map<String, Object> form = editBody(formOf(first));
        row(form, "top", 0).put("championKey", champion(10));

        putSet(first, form).andExpect(status().isOk()).andExpect(jsonPath("$.fearlessConflicts.length()").value(1))
                .andExpect(jsonPath("$.fearlessConflicts[0].setId").value(second))
                .andExpect(jsonPath("$.fearlessConflicts[0].duelId").value(ctx.duelId()))
                .andExpect(jsonPath("$.fearlessConflicts[0].duelSeq").value(1))
                .andExpect(jsonPath("$.fearlessConflicts[0].setNo").value(2))
                .andExpect(jsonPath("$.fearlessConflicts[0].championKey").value(champion(10)))
                .andExpect(jsonPath("$.warnings.length()").value(1));
        assertThat(formOf(first).get("fearlessConflicts")).asList().hasSize(1);
        assertThat(formOf(second).get("restrictedChampions")).asList().contains(champion(1)).doesNotContain(champion(0));
    }

    // ----- 삭제된 세트·새 선수 -----

    @Test
    void 삭제된_세트는_검사와_수정이_409_SET_DELETED() throws Exception {
        Ctx ctx = newCtx(FearlessMode.RESET);
        long first = firstSet(ctx);
        Map<String, Object> form = editBody(formOf(first));
        mockMvc.perform(delete("/api/admin/sets/" + first).with(csrf())).andExpect(status().isNoContent());

        checkSet(first, form).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SET_DELETED"));
        putSet(first, form).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SET_DELETED"));
    }

    @Test
    void 새_미등록_선수와_별칭_추가가_함께_저장된다() throws Exception {
        Ctx ctx = newCtx(FearlessMode.RESET);
        long first = firstSet(ctx);
        Map<String, Object> form = editBody(formOf(first));
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> newRow = row(form, "top", 0);
        newRow.put("resolution", "NEW");
        newRow.put("playerId", null);
        newRow.put("nickname", "신규" + suffix);
        Map<String, Object> aliasRow = row(form, "bottom", 1);
        aliasRow.put("addAlias", true);
        aliasRow.put("nickname", "별칭" + suffix);

        putSet(first, form).andExpect(status().isOk()).andExpect(jsonPath("$.createdPlayers.length()").value(1))
                .andExpect(jsonPath("$.createdPlayers[0].nickname").value("신규" + suffix))
                .andExpect(jsonPath("$.addedAliases.length()").value(1))
                .andExpect(jsonPath("$.addedAliases[0].playerId").value(ctx.b()[1]));

        assertThat(jdbc.queryForObject("select count(*) from game_set_player where game_set_id = ? and player_id = ?",
                Integer.class, first, ctx.a()[0])).isZero();
        assertThat(jdbc.queryForObject("select count(*) from player_alias where nickname = ?", Integer.class,
                "별칭" + suffix)).isEqualTo(1);
    }

    @Test
    void 다른_선수의_별칭을_추가하려_하면_409_ALIAS_CONFLICT이고_변경이_없다() throws Exception {
        Ctx ctx = newCtx(FearlessMode.RESET);
        long first = firstSet(ctx);
        Map<String, Object> response = formOf(first);
        Map<String, Object> form = editBody(response);
        Map<String, Object> aliasRow = row(form, "bottom", 1);
        aliasRow.put("addAlias", true);
        aliasRow.put("nickname", row(form, "top", 0).get("nickname"));
        row(form, "top", 1).put("championKey", champion(21));

        putSet(first, form).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ALIAS_CONFLICT"));

        assertThat(formOf(first).get("updatedAt")).isEqualTo(response.get("updatedAt"));
        assertThat(dbChampion(first, ctx.a()[1])).isEqualTo(champion(1));
    }

    // ----- 보안·형태 -----

    @Test
    @WithAnonymousUser
    void 익명은_401() throws Exception {
        mockMvc.perform(get("/api/admin/sets/1/form")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/admin/sets/1/check").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{}")).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/admin/sets/1").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void CSRF_없는_수정은_403() throws Exception {
        mockMvc.perform(put("/api/admin/sets/1").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/sets/1/check").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void 본문이_비면_400() throws Exception {
        Ctx ctx = newCtx(FearlessMode.RESET);
        long first = firstSet(ctx);
        mockMvc.perform(put("/api/admin/sets/" + first).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{}")).andExpect(status().isBadRequest());
    }
}
