package com.lolinhouse.tier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.jayway.jsonpath.JsonPath;
import com.lolinhouse.IntegrationTest;
import com.lolinhouse.player.PlayerAliasRepository;
import com.lolinhouse.player.PlayerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 역할: 선수 프로필 API(POST/PUT/GET/DELETE /api/admin/player-profiles)와 공개 티어리스트 반영을 MockMvc로 검증한다.
 * 컨텍스트가 테스트 사이에 DB를 공유하므로 실명·닉네임은 매번 고유 접미사를 붙이고, 티어리스트는 자기 선수만 골라 본다.
 */
@IntegrationTest
@AutoConfigureMockMvc
@WithMockUser(roles = "ADMIN")
class PlayerProfileApiTest {

    private static final String PROFILES = "/api/admin/player-profiles";
    private static final String TOP_1_HIGH = "\"TOP\":{\"tier\":1,\"sub\":\"HIGH\"}";

    @Autowired
    MockMvc mockMvc;
    @Autowired
    PlayerRepository playerRepository;
    @Autowired
    PlayerAliasRepository aliasRepository;
    @Autowired
    PlayerLaneTierRepository laneTierRepository;
    @Autowired
    JdbcTemplate jdbc;

    static String unique(String base) {
        return base + UUID.randomUUID().toString().substring(0, 8);
    }

    static String body(String realName, String lanes, List<String> nicknames, String extra) {
        String names = String.join(",", nicknames.stream().map(n -> "\"" + n + "\"").toList());
        return "{\"realName\":" + (realName == null ? "null" : "\"" + realName + "\"")
                + ",\"mainPosition\":null,\"lanes\":{" + lanes + "},\"nicknames\":[" + names + "]" + extra + "}";
    }

    ResultActions send(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder builder,
            String json) throws Exception {
        return mockMvc.perform(builder.with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    int create(String realName, String lanes, String... nicknames) throws Exception {
        String response = send(post(PROFILES), body(realName, lanes, List.of(nicknames), ""))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.id");
    }

    /** 공개 티어리스트에서 한 선수의 한 라인 노드(JSON 경로 결과). */
    Object tierListLane(int playerId, String position, String field) throws Exception {
        String response = mockMvc.perform(get("/api/tier-list")).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString();
        return JsonPath.read(response, "$.players[?(@.id==" + playerId + ")].lanes." + position + "." + field + "");
    }

    // ---------- 만들기 ----------

    @Test
    void 만들면_201과_5라인_키가_모두_있는_응답을_돌려주고_티어리스트에_점수가_보인다() throws Exception {
        String real = unique("실명");
        String nick = unique("닉");

        String response = send(post(PROFILES), body(real, TOP_1_HIGH, List.of(nick), ""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.displayName").value(real))
                .andExpect(jsonPath("$.registered").value(true))
                .andExpect(jsonPath("$.aliases[0].nickname").value(nick))
                .andExpect(jsonPath("$.lanes.TOP.tier").value(1))
                .andExpect(jsonPath("$.lanes.TOP.sub").value("HIGH"))
                .andExpect(jsonPath("$.lanes.JUNGLE").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.lanes.length()").value(5))
                .andReturn().getResponse().getContentAsString();
        int id = JsonPath.read(response, "$.id");

        assertThat(tierListLane(id, "TOP", "score")).isEqualTo(java.util.List.of(63));
        assertThat(tierListLane(id, "TOP", "trend")).isEqualTo(java.util.Collections.singletonList(null));
    }

    @Test
    void 만들_때_라인이_0개면_LANE_TIER_REQUIRED_티어가_범위_밖이면_INVALID_LANE_TIER() throws Exception {
        send(post(PROFILES), body(unique("실명"), "", List.of(unique("닉")), ""))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("LANE_TIER_REQUIRED"));
        send(post(PROFILES), body(unique("실명"), "\"TOP\":null", List.of(unique("닉")), ""))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("LANE_TIER_REQUIRED"));
        send(post(PROFILES), body(unique("실명"), "\"TOP\":{\"tier\":7,\"sub\":\"MID\"}", List.of(unique("닉")), ""))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_LANE_TIER"));
        send(post(PROFILES), body(unique("실명"), "\"TOP\":{\"tier\":3}", List.of(unique("닉")), ""))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_LANE_TIER"));
    }

    @Test
    void 닉네임이_비면_NICKNAME_REQUIRED_11개면_400이다() throws Exception {
        send(post(PROFILES), body(unique("실명"), TOP_1_HIGH, List.of(), ""))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("NICKNAME_REQUIRED"));

        List<String> eleven = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            eleven.add(unique("n"));
        }
        send(post(PROFILES), body(unique("실명"), TOP_1_HIGH, eleven, ""))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_NICKNAME"));
    }

    @Test
    void 다른_선수의_별칭과_겹치면_409와_conflicts이고_선수가_남지_않는다() throws Exception {
        String taken = unique("겹침");
        create(unique("실명"), TOP_1_HIGH, taken);
        String real = unique("실명");

        send(post(PROFILES), body(real, TOP_1_HIGH, List.of(unique("닉"), taken), ""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALIAS_CONFLICT"))
                .andExpect(jsonPath("$.conflicts[0].nickname").value(taken));

        assertThat(playerRepository.findByRealName(real)).isEmpty();
    }

    // ---------- 실명 중복 ----------

    @Test
    void 실명이_같은_선수를_만들면_409이고_M3_API도_같다() throws Exception {
        String real = unique("실명");
        create(real, TOP_1_HIGH, unique("닉"));

        send(post(PROFILES), body(real, TOP_1_HIGH, List.of(unique("닉")), ""))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_REAL_NAME"));
        send(post(PROFILES), body(" " + real + " ", TOP_1_HIGH, List.of(unique("닉")), ""))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_REAL_NAME"));
        send(post("/api/admin/players"),
                "{\"realName\":\"" + real + "\",\"mainPosition\":null,\"nicknames\":[\"" + unique("닉") + "\"]}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_REAL_NAME"));
    }

    @Test
    void 수정에서_자기_실명을_그대로_보내면_통과하고_남의_실명으로_바꾸면_409이며_롤백된다() throws Exception {
        String realA = unique("실명");
        String realB = unique("실명");
        String nickA = unique("닉");
        int idA = create(realA, TOP_1_HIGH, nickA);
        create(realB, TOP_1_HIGH, unique("닉"));

        send(put(PROFILES + "/" + idA), body(realA, TOP_1_HIGH, List.of(nickA), "")).andExpect(status().isOk());
        send(put(PROFILES + "/" + idA), body(realB, "\"TOP\":{\"tier\":6,\"sub\":\"LOW\"}", List.of(nickA), ""))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_REAL_NAME"));

        assertThat(playerRepository.findById((long) idA).orElseThrow().realName()).isEqualTo(realA);
        assertThat(laneTierRepository.findByPlayerId((long) idA).get(0).laneTier().tier()).isEqualTo(1);
    }

    @Test
    void 같은_실명_동시_생성_두_요청은_하나만_성공한다() throws Exception {
        String real = unique("동시");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Callable<Integer>> tasks = List.of(
                    () -> concurrentCreate(real, unique("닉")),
                    () -> concurrentCreate(real, unique("닉")));
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> f : pool.invokeAll(tasks)) {
                statuses.add(f.get());
            }
            assertThat(statuses).containsExactlyInAnyOrder(201, 409);
            assertThat(playerRepository.findByRealName(real)).hasSize(1);
        } finally {
            pool.shutdownNow();
        }
    }

    private int concurrentCreate(String real, String nick) throws Exception {
        return mockMvc.perform(post(PROFILES).with(user("admin").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body(real, TOP_1_HIGH, List.of(nick), "")))
                .andReturn().getResponse().getStatus();
    }

    // ---------- 라인 티어와 ▲▼ ----------

    @Test
    void 티어를_올리면_UP_같은_값으로_저장하면_유지_새_라인은_변동이_아니고_삭제하면_null() throws Exception {
        String real = unique("실명");
        String nick = unique("닉");
        int id = create(real, "\"MID\":{\"tier\":3,\"sub\":\"MID\"}", nick);

        String up = "\"MID\":{\"tier\":2,\"sub\":\"MID\"},\"ADC\":{\"tier\":4,\"sub\":\"LOW\"}";
        send(put(PROFILES + "/" + id), body(real, up, List.of(nick), "")).andExpect(status().isOk());
        assertThat(tierListLane(id, "MID", "trend.direction")).isEqualTo(List.of("UP"));
        assertThat(tierListLane(id, "MID", "trend.fromTier")).isEqualTo(List.of(3));
        assertThat(tierListLane(id, "MID", "trend.fromSub")).isEqualTo(List.of("MID"));
        assertThat(tierListLane(id, "ADC", "trend")).isEqualTo(java.util.Collections.singletonList(null));

        send(put(PROFILES + "/" + id), body(real, up, List.of(nick), "")).andExpect(status().isOk());
        assertThat(tierListLane(id, "MID", "trend.direction")).as("같은 값 저장은 변동 유지").isEqualTo(List.of("UP"));

        String down = "\"MID\":{\"tier\":2,\"sub\":\"LOW\"}";
        send(put(PROFILES + "/" + id), body(real, down, List.of(nick), ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lanes.ADC").value(org.hamcrest.Matchers.nullValue()));
        assertThat(tierListLane(id, "MID", "trend.direction")).isEqualTo(List.of("DOWN"));
        assertThat(tierListLane(id, "MID", "trend.fromSub")).isEqualTo(List.of("MID"));
        assertThat(laneTierRepository.findByPlayerId((long) id)).hasSize(1);
    }

    @Test
    void recordChange가_false면_티어를_바꿔도_변동_표시가_갱신되지_않는다() throws Exception {
        String real = unique("실명");
        String nick = unique("닉");
        int id = create(real, "\"TOP\":{\"tier\":3,\"sub\":\"MID\"},\"MID\":{\"tier\":3,\"sub\":\"MID\"}", nick);
        send(put(PROFILES + "/" + id), body(real, "\"TOP\":{\"tier\":2,\"sub\":\"MID\"},\"MID\":{\"tier\":3,\"sub\":\"MID\"}",
                List.of(nick), "")).andExpect(status().isOk());

        send(put(PROFILES + "/" + id), body(real,
                "\"TOP\":{\"tier\":5,\"sub\":\"MID\"},\"MID\":{\"tier\":1,\"sub\":\"HIGH\"}",
                List.of(nick), ",\"recordChange\":false")).andExpect(status().isOk());

        assertThat(tierListLane(id, "TOP", "tier")).as("값은 바뀐다").isEqualTo(List.of(5));
        assertThat(tierListLane(id, "TOP", "trend.direction")).as("기존 변동은 그대로").isEqualTo(List.of("UP"));
        assertThat(tierListLane(id, "TOP", "trend.fromTier")).isEqualTo(List.of(3));
        assertThat(tierListLane(id, "MID", "trend")).as("변동 없던 라인은 그대로 없음")
                .isEqualTo(java.util.Collections.singletonList(null));
    }

    // ---------- 요주 인물(flagged) ----------

    @Test
    void flagged를_생략하면_생성은_false이고_true로_만들면_티어리스트에_보인다() throws Exception {
        String nick = unique("닉");
        send(post(PROFILES), body(unique("실명"), TOP_1_HIGH, List.of(nick), ""))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.flagged").value(false));

        String real = unique("실명");
        String response = send(post(PROFILES), body(real, TOP_1_HIGH, List.of(unique("닉")), ",\"flagged\":true"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.flagged").value(true))
                .andReturn().getResponse().getContentAsString();
        int id = JsonPath.read(response, "$.id");

        String tierList = mockMvc.perform(get("/api/tier-list")).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString();
        assertThat((List<?>) JsonPath.read(tierList, "$.players[?(@.id==" + id + ")].flagged")).isEqualTo(List.of(true));
    }

    @Test
    void 수정에서_flagged를_바꾸고_생략하면_기존_값을_유지한다() throws Exception {
        String real = unique("실명");
        String nick = unique("닉");
        int id = create(real, TOP_1_HIGH, nick);

        send(put(PROFILES + "/" + id), body(real, TOP_1_HIGH, List.of(nick), ",\"flagged\":true"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.flagged").value(true));
        send(put(PROFILES + "/" + id), body(real, TOP_1_HIGH, List.of(nick), ""))
                .andExpect(status().isOk()).andExpect(jsonPath("$.flagged").value(true));
        send(put(PROFILES + "/" + id), body(real, TOP_1_HIGH, List.of(nick), ",\"flagged\":null"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.flagged").value(true));
        mockMvc.perform(get(PROFILES)).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id==" + id + ")].flagged").value(true));
        send(put(PROFILES + "/" + id), body(real, TOP_1_HIGH, List.of(nick), ",\"flagged\":false"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.flagged").value(false));
    }

    @Test
    void 티어에_소수가_오면_400이고_저장값이_유지된다() throws Exception {
        String real = unique("실명");
        String nick = unique("닉");
        int id = create(real, "\"TOP\":{\"tier\":3,\"sub\":\"MID\"}", nick);

        send(put(PROFILES + "/" + id), body(real, "\"TOP\":{\"tier\":2.9,\"sub\":\"MID\"}", List.of(nick), ""))
                .andExpect(status().isBadRequest());

        assertThat(laneTierRepository.findByPlayerId((long) id).get(0).laneTier().tier()).isEqualTo(3);
    }

    // ---------- 닉네임 ----------

    @Test
    void 별칭_11개_선수는_티어만_고쳐도_저장되고_12개로_늘리면_400이다() throws Exception {
        String real = unique("실명");
        List<String> nicks = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            nicks.add(unique("n"));
        }
        String created = send(post(PROFILES), body(real, TOP_1_HIGH, nicks, ""))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        int id = JsonPath.read(created, "$.id");
        String eleventh = unique("n");
        send(post("/api/admin/players/" + id + "/aliases"), "{\"nickname\":\"" + eleventh + "\"}")
                .andExpect(status().isCreated());
        nicks.add(eleventh);

        send(put(PROFILES + "/" + id), body(real, "\"TOP\":{\"tier\":2,\"sub\":\"HIGH\"}", nicks, ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aliases.length()").value(11))
                .andExpect(jsonPath("$.lanes.TOP.tier").value(2));

        nicks.add(unique("n"));
        send(put(PROFILES + "/" + id), body(real, TOP_1_HIGH, nicks, ""))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_NICKNAME"));
        assertThat(aliasRepository.countByPlayerId((long) id)).isEqualTo(11);
    }

    @Test
    void 닉네임_교체는_추가와_삭제를_하고_대표가_지워지면_남은_가장_오래된_별칭이_대표가_된다() throws Exception {
        String real = unique("실명");
        String first = unique("Aa");
        String second = unique("bb");
        String third = unique("cc");
        int id = create(real, TOP_1_HIGH, first, second);
        assertThat(playerRepository.findById((long) id).orElseThrow().gameNickname()).isEqualTo(first);

        // 같은 키의 기존 별칭(first)은 표기를 그대로 두고, 대문자만 다른 입력은 같은 닉네임으로 본다.
        send(put(PROFILES + "/" + id), body(real, TOP_1_HIGH, List.of(first.toUpperCase(), third), ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aliases[0].nickname").value(first))
                .andExpect(jsonPath("$.aliases[1].nickname").value(third))
                .andExpect(jsonPath("$.aliases.length()").value(2));
        assertThat(playerRepository.findById((long) id).orElseThrow().gameNickname()).isEqualTo(first);

        send(put(PROFILES + "/" + id), body(real, TOP_1_HIGH, List.of(third), ""))
                .andExpect(status().isOk()).andExpect(jsonPath("$.aliases.length()").value(1));
        assertThat(playerRepository.findById((long) id).orElseThrow().gameNickname()).isEqualTo(third);
        assertThat(playerRepository.findById((long) id).orElseThrow().displayName()).isEqualTo(real);
    }

    @Test
    void 수정에서_닉네임_충돌이면_409이고_실명_변경도_함께_롤백된다() throws Exception {
        String taken = unique("남의");
        create(unique("실명"), TOP_1_HIGH, taken);
        String real = unique("실명");
        String nick = unique("닉");
        int id = create(real, TOP_1_HIGH, nick);

        send(put(PROFILES + "/" + id), body(unique("새실명"), "\"TOP\":{\"tier\":6,\"sub\":\"LOW\"}",
                List.of(nick, taken), "")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALIAS_CONFLICT"));

        assertThat(playerRepository.findById((long) id).orElseThrow().realName()).isEqualTo(real);
        assertThat(aliasRepository.countByPlayerId((long) id)).isEqualTo(1);
        assertThat(laneTierRepository.findByPlayerId((long) id).get(0).laneTier().tier()).isEqualTo(1);
    }

    @Test
    void 수정에서_닉네임이_비면_400이고_없는_선수는_404다() throws Exception {
        int id = create(unique("실명"), TOP_1_HIGH, unique("닉"));

        send(put(PROFILES + "/" + id), body(unique("실명"), TOP_1_HIGH, List.of(), ""))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("NICKNAME_REQUIRED"));
        send(put(PROFILES + "/999999999"), body(unique("실명"), TOP_1_HIGH, List.of(unique("닉")), ""))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PLAYER_NOT_FOUND"));
    }

    // ---------- 미등록 → 등록, 목록 ----------

    @Test
    void 미등록_선수에_실명을_넣으면_등록되고_라인이_0개여도_수정할_수_있다() throws Exception {
        String nick = unique("미등록");
        long id = playerRepository.save(com.lolinhouse.player.Player.unregistered(nick)).id();
        jdbc.update("insert into player_alias (player_id, nickname, nickname_key, created_at) values (?, ?, ?, UTC_TIMESTAMP(6))",
                id, nick, com.lolinhouse.domain.player.NicknameNormalizer.comparisonKey(nick));

        String list = mockMvc.perform(get(PROFILES)).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString();
        assertThat((List<?>) JsonPath.read(list, "$[?(@.id==" + id + " && @.registered==false)]")).hasSize(1);

        String real = unique("실명");
        send(put(PROFILES + "/" + id), body(real, "", List.of(nick), ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registered").value(true))
                .andExpect(jsonPath("$.displayName").value(real));
        assertThat(laneTierRepository.findByPlayerId(id)).isEmpty();
    }

    // ---------- 삭제 ----------

    @Test
    void 기록이_없는_선수는_삭제되고_별칭과_라인_티어도_사라진다() throws Exception {
        int id = create(unique("실명"), TOP_1_HIGH, unique("닉"));

        mockMvc.perform(delete(PROFILES + "/" + id).with(csrf())).andExpect(status().isNoContent());

        assertThat(playerRepository.existsById((long) id)).isFalse();
        assertThat(aliasRepository.countByPlayerId((long) id)).isZero();
        assertThat(laneTierRepository.findByPlayerId((long) id)).isEmpty();
        mockMvc.perform(delete(PROFILES + "/" + id).with(csrf())).andExpect(status().isNotFound());
    }

    @Test
    void 세트_기록이_있는_선수는_409_PLAYER_HAS_RECORDS이고_그대로_남는다() throws Exception {
        int id = create(unique("실명"), TOP_1_HIGH, unique("닉"));
        long session = insertReturningId("insert into inhouse_session (start_date, created_at) values (?, UTC_TIMESTAMP(6))",
                "2099-" + String.format("%02d-%02d", 1 + (int) (Math.random() * 12), 1 + (int) (Math.random() * 28)));
        long duel = insertReturningId("insert into duel (session_id, seq, created_at) values (?, 1, UTC_TIMESTAMP(6))", session);
        long set = insertReturningId("insert into game_set (duel_id, set_no, team1_side, winner_team, top_team, shot_date,"
                + " duration_sec, team1_displayed_kills, team1_displayed_deaths, team1_displayed_assists, team1_displayed_gold,"
                + " team2_displayed_kills, team2_displayed_deaths, team2_displayed_assists, team2_displayed_gold,"
                + " fingerprint, created_at, updated_at) values (?, 1, 'BLUE', 'TEAM1', 'TEAM1', '2026-10-15', 1094,"
                + " 10, 5, 20, 60000, 5, 10, 8, 50000, ?, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))", duel, UUID.randomUUID().toString());
        jdbc.update("insert into game_set_player (game_set_id, player_id, team, position, screen_row, champion_key,"
                + " kills, deaths, assists, cs, gold) values (?, ?, 'TEAM1', 'TOP', 1, 'Ahri', 1, 2, 3, 4, 5)", set, id);

        mockMvc.perform(delete(PROFILES + "/" + id).with(csrf()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PLAYER_HAS_RECORDS"));

        assertThat(playerRepository.existsById((long) id)).isTrue();
        assertThat(laneTierRepository.findByPlayerId((long) id)).hasSize(1);
        jdbc.update("delete from game_set_player where game_set_id = ?", set);
    }

    private long insertReturningId(String sql, Object... args) {
        org.springframework.jdbc.support.GeneratedKeyHolder keys = new org.springframework.jdbc.support.GeneratedKeyHolder();
        jdbc.update(connection -> {
            java.sql.PreparedStatement ps = connection.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
            return ps;
        }, keys);
        return keys.getKey().longValue();
    }
}
