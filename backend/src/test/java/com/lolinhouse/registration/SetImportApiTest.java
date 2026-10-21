package com.lolinhouse.registration;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.jayway.jsonpath.JsonPath;
import com.lolinhouse.IntegrationTest;
import com.lolinhouse.champion.CatalogSnapshot;
import com.lolinhouse.champion.Champion;
import com.lolinhouse.champion.ChampionCatalog;
import com.lolinhouse.domain.Position;
import com.lolinhouse.ingest.job.ExtractionJobRepository;
import com.lolinhouse.player.PlayerService;
import com.lolinhouse.registration.RegistrationTestData.Game;
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

import static com.lolinhouse.registration.RegistrationFixtures.champion;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 역할: 결과창 없이 세트를 가져오는 API(POST /api/admin/duels/{id}/sets/import, ADR 014)를 실제 MySQL로 검증한다.
 * 3세트 연속 피어리스를 통과시키려고 챔피언 카탈로그를 40개로 바꾼다. 날짜는 무작위(3000년대)로 다른 테스트와 겹치지 않는다.
 */
@IntegrationTest
@AutoConfigureMockMvc
@WithMockUser(roles = "ADMIN")
class SetImportApiTest {

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
    ExtractionJobRepository jobs;

    @MockitoBean
    ChampionCatalog catalog;

    RegistrationTestData data;

    @BeforeEach
    void setUp() {
        List<Champion> champions = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            champions.add(new Champion(champion(i), String.valueOf(i + 1), "챔피언" + i));
        }
        when(catalog.current()).thenReturn(Optional.of(new CatalogSnapshot("16.20.1", champions, (crop, ban) -> null)));
        data = new RegistrationTestData(sessions, playerService, jobs, json);
    }

    /** blue·red 5명씩. 픽은 pickBase부터 10개(블루 먼저), 밴은 banBase부터 10개(블루 B1~B5, 레드 R1~R5). */
    Map<String, Object> body(String date, Integer durationSec, String winner, long[] blue, long[] red, int pickBase,
            int banBase) {
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

    ResultActions importSet(long duelId, Map<String, Object> body) throws Exception {
        return mockMvc.perform(post("/api/admin/duels/" + duelId + "/sets/import").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }

    long count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    @Test
    void 세트_세_개를_이어서_가져오면_번호와_팀과_진영이_판정되고_이미지는_비어_있다() throws Exception {
        long duelId = data.newFirstDuel();
        long[] a = data.newPlayers(5);
        long[] b = data.newPlayers(5);
        String date = Game.random().shotDate();

        // 1세트: 블루=A팀(1팀)
        importSet(duelId, body(date, null, "BLUE", a, b, 0, 30)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.setNo").value(1)).andExpect(jsonPath("$.duelId").value(duelId))
                .andExpect(jsonPath("$.topTeam").value("TEAM1"));
        // 2세트: A팀이 레드로 옮겨 감 → 블루는 B팀(2팀)
        String second = importSet(duelId, body(date, null, "RED", b, a, 10, 30)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.setNo").value(2)).andExpect(jsonPath("$.topTeam").value("TEAM2"))
                .andReturn().getResponse().getContentAsString();
        // 3세트: A팀이 다시 블루
        importSet(duelId, body(date, null, "BLUE", a, b, 20, 30)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.setNo").value(3)).andExpect(jsonPath("$.topTeam").value("TEAM1"));

        long secondId = ((Number) JsonPath.read(second, "$.setId")).longValue();
        Map<String, Object> row = jdbc.queryForMap("select team1_side, winner_team, top_team, duration_sec, "
                + "image_hash, image_key from game_set where id = ?", secondId);
        assertThat(row.get("team1_side")).isEqualTo("RED");
        assertThat(row.get("winner_team")).isEqualTo("TEAM1"); // 레드 승 = A팀 승
        assertThat(row.get("duration_sec")).isNull();
        assertThat(row.get("image_hash")).isNull();
        assertThat(row.get("image_key")).isNull();
        assertThat(count("select count(*) from game_set_ban where game_set_id = ?", secondId)).isEqualTo(10);
        assertThat(jdbc.queryForObject("select champion_key from game_set_ban where game_set_id = ? and turn = 2",
                String.class, secondId)).isEqualTo(champion(35));
    }

    @Test
    void 경기_시간이_있으면_초로_저장되고_지문에_들어간다() throws Exception {
        long duelId = data.newFirstDuel();
        String date = Game.random().shotDate();
        String body = importSet(duelId, body(date, 1094, "BLUE", data.newPlayers(5), data.newPlayers(5), 0, 20))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();

        long setId = ((Number) JsonPath.read(body, "$.setId")).longValue();
        Map<String, Object> row = jdbc.queryForMap("select duration_sec, fingerprint from game_set where id = ?", setId);
        assertThat(row.get("duration_sec")).isEqualTo(1094);
        assertThat((String) row.get("fingerprint")).startsWith(date + "|1094|");
    }

    @Test
    void 시간을_모르는_세트는_공개_화면에서_durationSec이_null이고_CSV_경기_시간은_빈칸이다() throws Exception {
        long duelId = data.newFirstDuel();
        String date = Game.random().shotDate();
        String body = importSet(duelId, body(date, null, "BLUE", data.newPlayers(5), data.newPlayers(5), 0, 20))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long setId = ((Number) JsonPath.read(body, "$.setId")).longValue();
        assertThat(jdbc.queryForObject("select fingerprint from game_set where id = ?", String.class, setId))
                .startsWith(date + "|?|");

        mockMvc.perform(get("/api/sets/" + setId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.durationSec").doesNotExist())
                .andExpect(jsonPath("$.shotDate").value(date));

        // 기간 필터는 내전 묶음 시작일 기준이다
        String sessionDate = jdbc.queryForObject("select s.start_date from inhouse_session s join duel d "
                + "on d.session_id = s.id where d.id = ?", String.class, duelId);
        byte[] bytes = mockMvc.perform(get("/api/records/export?from=" + sessionDate + "&to=" + sessionDate))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        String[] lines = new String(bytes, StandardCharsets.UTF_8).substring(1).split("\r\n");
        // 열: 시작일,대결 번호,대결 id,세트 번호,세트 id,결과창 날짜,경기 시간(초),...
        String[] cells = lines[1].split(",", -1);
        assertThat(cells[5]).isEqualTo(date);
        assertThat(cells[6]).isEmpty();
    }

    @Test
    void 같은_데이터를_다시_가져오면_409_DUPLICATE_SET이고_저장되지_않는다() throws Exception {
        long duelId = data.newFirstDuel();
        long[] a = data.newPlayers(5);
        long[] b = data.newPlayers(5);
        String date = Game.random().shotDate();
        importSet(duelId, body(date, null, "BLUE", a, b, 0, 20)).andExpect(status().isCreated());

        // 같은 대결에 다시(2세트로 들어가려 함)
        importSet(duelId, body(date, null, "BLUE", a, b, 0, 20)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_SET"));
        // 다른 새 대결에서 같은 경기를 가져와도 지문이 같아 막힌다
        long otherDuel = data.newFirstDuel();
        importSet(otherDuel, body(date, null, "BLUE", a, b, 0, 20)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_SET"));
        assertThat(count("select count(*) from game_set where duel_id in (?, ?)", duelId, otherDuel)).isEqualTo(1);
    }

    @Test
    void 규칙을_어기면_422이고_저장되지_않는다() throws Exception {
        long duelId = data.newFirstDuel();
        long[] a = data.newPlayers(5);
        long[] b = data.newPlayers(5);
        String date = Game.random().shotDate();

        // 같은 선수가 두 번
        long[] dup = a.clone();
        dup[1] = b[0];
        importSet(duelId, body(date, null, "BLUE", dup, b, 0, 20)).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("SET_RULES_VIOLATED"));
        // 카탈로그에 없는 챔피언
        Map<String, Object> unknown = body(date, null, "BLUE", a, b, 0, 20);
        ((Map<String, Object>) ((List<?>) unknown.get("players")).get(0)).put("championKey", "NoSuchChamp");
        importSet(duelId, unknown).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.violations[0]").value(org.hamcrest.Matchers.containsString("알 수 없는")));
        // 팀 안에서 포지션 중복
        Map<String, Object> samePosition = body(date, null, "BLUE", a, b, 0, 20);
        ((Map<String, Object>) ((List<?>) samePosition.get("players")).get(2)).put("position", Position.TOP.name());
        importSet(duelId, samePosition).andExpect(status().isUnprocessableContent());
        // 밴 중복
        Map<String, Object> dupBan = body(date, null, "BLUE", a, b, 0, 20);
        dupBan.put("bans", Map.of("BLUE", List.of(champion(20), champion(20), champion(22), champion(23),
                champion(24)), "RED", List.of(champion(25), champion(26), champion(27), champion(28), champion(29))));
        importSet(duelId, dupBan).andExpect(status().isUnprocessableContent());
        // 존재하지 않는 선수
        long[] ghost = a.clone();
        ghost[0] = 999_999_999L;
        importSet(duelId, body(date, null, "BLUE", ghost, b, 0, 20)).andExpect(status().isUnprocessableContent());
        // 피어리스: 1세트 저장 뒤 같은 픽으로 2세트
        importSet(duelId, body(date, null, "BLUE", a, b, 0, 20)).andExpect(status().isCreated());
        importSet(duelId, body(Game.random().shotDate(), null, "RED", b, a, 0, 30))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.violations[0]").value(org.hamcrest.Matchers.containsString("피어리스")));
        assertThat(count("select count(*) from game_set where duel_id = ?", duelId)).isEqualTo(1);
    }

    @Test
    void 진영마다_선수_5명이_아니면_400이다() throws Exception {
        long duelId = data.newFirstDuel();
        Map<String, Object> body = body(Game.random().shotDate(), null, "BLUE", data.newPlayers(5),
                data.newPlayers(5), 0, 20);
        ((Map<String, Object>) ((List<?>) body.get("players")).get(1)).put("side", "BLUE");

        importSet(duelId, body).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void 없는_대결이면_404이다() throws Exception {
        importSet(999_999_999L, body(Game.random().shotDate(), null, "BLUE", data.newPlayers(5), data.newPlayers(5),
                0, 20)).andExpect(status().isNotFound());
    }

    @Test
    @WithAnonymousUser
    void 로그인하지_않으면_401이다() throws Exception {
        importSet(1L, Map.of()).andExpect(status().isUnauthorized());
    }

    @Test
    void CSRF_토큰이_없으면_403이다() throws Exception {
        mockMvc.perform(post("/api/admin/duels/1/sets/import").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void 결과창_등록_경로는_여전히_경기_시간이_필수다() throws Exception {
        long duelId = data.newFirstDuel();
        long[] top = data.newPlayers(5);
        long[] bottom = data.newPlayers(5);
        Game noDuration = new Game(Game.random().shotDate(), "");
        var request = data.request(data.succeededJob(duelId), top, bottom, noDuration);

        mockMvc.perform(post("/api/admin/duels/" + duelId + "/sets").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request.build())))
                .andExpect(status().isUnprocessableContent());
        assertThat(count("select count(*) from game_set where duel_id = ?", duelId)).isZero();
    }
}
