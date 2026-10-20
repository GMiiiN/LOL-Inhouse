package com.lolinhouse.registration;

import java.util.List;
import java.util.Map;

import com.jayway.jsonpath.JsonPath;
import com.lolinhouse.IntegrationTest;
import com.lolinhouse.domain.Side;
import com.lolinhouse.ingest.job.ExtractionJobRepository;
import com.lolinhouse.player.PlayerService;
import com.lolinhouse.registration.RegistrationFixtures.Req;
import com.lolinhouse.registration.RegistrationTestData.Game;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;

import static com.lolinhouse.registration.RegistrationFixtures.champion;
import static com.lolinhouse.registration.RegistrationFixtures.newPlayer;
import static com.lolinhouse.registration.RegistrationFixtures.unresolved;
import static com.lolinhouse.registration.RegistrationFixtures.withAlias;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 역할: 세트 등록 API(registration-context, draft, sets/check, sets)의 상태 코드·응답 모양·저장 결과를 실제 MySQL로 검증한다.
 * 추출 작업은 ExtractionResult JSON을 직접 INSERT한 SUCCEEDED 행을 쓴다. 챔피언 id는 가짜 Data Dragon의 Champion00~19.
 */
@IntegrationTest
@AutoConfigureMockMvc
@WithMockUser(roles = "ADMIN")
class SetRegistrationApiTest {

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

    RegistrationTestData data;

    @BeforeEach
    void setUp() {
        data = new RegistrationTestData(sessions, playerService, jobs, json);
    }

    ResultActions postJson(String path, Req request) throws Exception {
        return mockMvc.perform(post(path).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(request.build())));
    }

    ResultActions register(long duelId, Req request) throws Exception {
        return postJson("/api/admin/duels/" + duelId + "/sets", request);
    }

    ResultActions check(long duelId, Req request) throws Exception {
        return postJson("/api/admin/duels/" + duelId + "/sets/check", request);
    }

    long count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    /** 새 대결에 1세트를 저장하고 setId를 돌려준다. */
    long registerFirstSet(long duelId, long[] top, long[] bottom, Game game) throws Exception {
        long jobId = data.succeededJob(duelId);
        String body = register(duelId, data.request(jobId, top, bottom, game)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.setId")).longValue();
    }

    // ----- 저장 -----

    @Test
    @WithAnonymousUser
    void 로그인하지_않으면_401이다() throws Exception {
        mockMvc.perform(get("/api/admin/duels/1/registration-context")).andExpect(status().isUnauthorized());
    }

    @Test
    void 첫_세트를_저장하면_201이고_선수_밴_이미지_작업_연결이_모두_저장된다() throws Exception {
        long duelId = data.newFirstDuel();
        long[] top = data.newPlayers(5);
        long[] bottom = data.newPlayers(5);
        long jobId = data.succeededJob(duelId);
        Req request = data.request(jobId, top, bottom, Game.random());

        String body = register(duelId, request)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.duelId").value(duelId))
                .andExpect(jsonPath("$.setNo").value(1))
                .andExpect(jsonPath("$.topTeam").value("TEAM1"))
                .andExpect(jsonPath("$.createdPlayers").isEmpty())
                .andExpect(jsonPath("$.addedAliases").isEmpty())
                .andReturn().getResponse().getContentAsString();

        long setId = ((Number) JsonPath.read(body, "$.setId")).longValue();
        assertThat(count("select count(*) from game_set_player where game_set_id = ?", setId)).isEqualTo(10);
        assertThat(count("select count(*) from game_set_ban where game_set_id = ?", setId)).isEqualTo(10);
        // 블루(홀수 턴)가 위쪽 팀: 위쪽 밴 Champion10~14가 B1~B5(turn 1,3,5,7,9)
        assertThat(jdbc.queryForObject("select champion_key from game_set_ban where game_set_id = ? and turn = 1",
                String.class, setId)).isEqualTo(champion(10));
        assertThat(jdbc.queryForObject("select champion_key from game_set_ban where game_set_id = ? and turn = 2",
                String.class, setId)).isEqualTo(champion(15));
        assertThat(jdbc.queryForObject("select side from game_set_ban where game_set_id = ? and turn = 2",
                String.class, setId)).isEqualTo("RED");
        Map<String, Object> job = jdbc.queryForMap("select game_set_id, image_hash, image_key from extraction_job where id = ?",
                jobId);
        assertThat(((Number) job.get("game_set_id")).longValue()).isEqualTo(setId);
        Map<String, Object> set = jdbc.queryForMap("select image_hash, image_key, team1_side, winner_team, top_team "
                + "from game_set where id = ?", setId);
        assertThat(set.get("image_hash")).isEqualTo(job.get("image_hash"));
        assertThat(set.get("image_key")).isEqualTo(job.get("image_key"));
        assertThat(set.get("team1_side")).isEqualTo("BLUE");
        assertThat(set.get("winner_team")).isEqualTo("TEAM1"); // 위쪽 팀 WIN
        assertThat(jdbc.queryForObject("select screen_row from game_set_player where game_set_id = ? and player_id = ?",
                Integer.class, setId, top[2])).isEqualTo(3);
    }

    @Test
    void 두번째_세트는_직전_A팀과_겹침으로_판별하고_진영을_계산한다() throws Exception {
        long duelId = data.newFirstDuel();
        long[] teamA = data.newPlayers(5);
        long[] teamB = data.newPlayers(5);
        registerFirstSet(duelId, teamA, teamB, Game.random());

        // 2세트: A팀이 위쪽, 레드(A팀은 2세트에 레드가 규칙)
        Req second = RegistrationTestData.withOtherChampions(data.request(data.succeededJob(duelId), teamA, teamB,
                Game.random()));
        second.topSide = Side.RED;
        register(duelId, second).andExpect(status().isCreated())
                .andExpect(jsonPath("$.setNo").value(2))
                .andExpect(jsonPath("$.topTeam").value("TEAM1"))
                .andExpect(jsonPath("$.warnings").isEmpty());
        assertThat(count("select count(*) from game_set where duel_id = ? and team1_side = 'RED' and set_no = 2 "
                + "and top_team = 'TEAM1'", duelId)).isEqualTo(1);

    }

    @Test
    void 위쪽_팀이_직전_B팀이면_B팀으로_저장된다() throws Exception {
        long duelId = data.newFirstDuel();
        long[] teamA = data.newPlayers(5);
        long[] teamB = data.newPlayers(5);
        registerFirstSet(duelId, teamA, teamB, Game.random());

        // B팀이 위쪽, 블루(B팀은 2세트에 블루)
        Req second = RegistrationTestData.withOtherChampions(data.request(data.succeededJob(duelId), teamB, teamA,
                Game.random()));
        second.topSide = Side.BLUE;
        register(duelId, second).andExpect(status().isCreated())
                .andExpect(jsonPath("$.topTeam").value("TEAM2"));
        assertThat(count("select count(*) from game_set where duel_id = ? and set_no = 2 and team1_side = 'RED'",
                duelId)).isEqualTo(1);
    }

    @Test
    void 피어리스를_어기면_422이고_아무것도_저장되지_않는다() throws Exception {
        long duelId = data.newFirstDuel();
        long[] teamA = data.newPlayers(5);
        long[] teamB = data.newPlayers(5);
        registerFirstSet(duelId, teamA, teamB, Game.random());
        long jobId = data.succeededJob(duelId);
        Req second = data.request(jobId, teamA, teamB, Game.random()); // 같은 픽 00~09

        register(duelId, second).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("SET_RULES_VIOLATED"))
                .andExpect(jsonPath("$.violations[0]").value(org.hamcrest.Matchers.containsString("피어리스")))
                .andExpect(jsonPath("$.warnings").isArray());
        assertThat(count("select count(*) from game_set where duel_id = ?", duelId)).isEqualTo(1);
        assertThat(count("select count(*) from extraction_job where id = ? and game_set_id is null", jobId))
                .isEqualTo(1);
    }

    @Test
    void 같은_경기를_다른_캡처로_같은_대결에_올리면_409_DUPLICATE_SET이고_피어리스_오류보다_먼저다() throws Exception {
        long duelId = data.newFirstDuel();
        long[] teamA = data.newPlayers(5);
        long[] teamB = data.newPlayers(5);
        Game game = Game.random();
        long firstSetId = registerFirstSet(duelId, teamA, teamB, game);

        // 반대편에서 캡처한 같은 경기: 위쪽/아래쪽이 뒤바뀐 같은 숫자. 픽도 같아 피어리스 위반이기도 하지만 중복이 먼저다
        Req otherCapture = data.request(data.succeededJob(duelId), teamB, teamA, game);
        for (int i = 0; i < 5; i++) {
            int index = i;
            otherCapture.topRow(i, row -> RegistrationFixtures.withChampion(row, champion(5 + index)));
            otherCapture.bottomRow(i, row -> RegistrationFixtures.withChampion(row, champion(index)));
        }
        register(duelId, otherCapture).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_SET"))
                .andExpect(jsonPath("$.existingSetId").value(firstSetId))
                .andExpect(jsonPath("$.existingDuelId").value(duelId))
                .andExpect(jsonPath("$.existingSetNo").value(1));
    }

    @Test
    void 같은_경기를_다른_대결에_올려도_409이고_기존_대결을_알려준다() throws Exception {
        long sessionId = data.newSession();
        long duelA = data.newDuel(sessionId, FearlessMode.RESET);
        long duelB = data.newDuel(sessionId, FearlessMode.RESET);
        long[] teamA = data.newPlayers(5);
        long[] teamB = data.newPlayers(5);
        Game game = Game.random();
        long firstSetId = registerFirstSet(duelA, teamA, teamB, game);

        register(duelB, data.request(data.succeededJob(duelB), teamB, teamA, game))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_SET"))
                .andExpect(jsonPath("$.existingSetId").value(firstSetId))
                .andExpect(jsonPath("$.existingDuelId").value(duelA));
    }

    @Test
    void 같은_작업을_두번_저장하면_409_EXTRACTION_ALREADY_SAVED다() throws Exception {
        long duelId = data.newFirstDuel();
        long[] top = data.newPlayers(5);
        long[] bottom = data.newPlayers(5);
        long jobId = data.succeededJob(duelId);
        Req request = data.request(jobId, top, bottom, Game.random());
        String body = register(duelId, request).andExpect(status().isCreated()).andReturn().getResponse()
                .getContentAsString();
        int setId = JsonPath.read(body, "$.setId");

        register(duelId, request).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EXTRACTION_ALREADY_SAVED"))
                .andExpect(jsonPath("$.savedSetId").value(setId));
    }

    @Test
    void 다른_대결의_작업이면_400_EXTRACTION_DUEL_MISMATCH다() throws Exception {
        long duelId = data.newFirstDuel();
        long otherDuel = data.newFirstDuel();
        Req request = data.request(data.succeededJob(otherDuel), data.newPlayers(5), data.newPlayers(5), Game.random());

        register(duelId, request).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EXTRACTION_DUEL_MISMATCH"));
    }

    @Test
    void 끝나지_않은_작업이나_없는_작업은_각각_409_404다() throws Exception {
        long duelId = data.newFirstDuel();
        long[] top = data.newPlayers(5);
        long[] bottom = data.newPlayers(5);

        register(duelId, data.request(data.queuedJob(duelId), top, bottom, Game.random()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EXTRACTION_NOT_READY"));
        register(duelId, data.request(987_654_321L, top, bottom, Game.random()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("EXTRACTION_NOT_FOUND"));
        register(987_654_321L, data.request(1L, top, bottom, Game.random()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("DUEL_NOT_FOUND"));
    }

    // ----- 선수 확정 -----

    @Test
    void addAlias가_true인_행만_별칭이_생기고_자동_연결_행은_생기지_않는다() throws Exception {
        long duelId = data.newFirstDuel();
        long[] top = data.newPlayers(5);
        long[] bottom = data.newPlayers(5);
        Req request = data.request(data.succeededJob(duelId), top, bottom, Game.random());
        String newNickname = "별칭" + System.nanoTime();
        request.topRow(0, row -> withAlias(row, newNickname));

        register(duelId, request).andExpect(status().isCreated())
                .andExpect(jsonPath("$.addedAliases[0].playerId").value(top[0]))
                .andExpect(jsonPath("$.addedAliases[0].nickname").value(newNickname));

        assertThat(count("select count(*) from player_alias where player_id = ?", top[0])).isEqualTo(2);
        assertThat(count("select count(*) from player_alias where player_id = ?", top[1])).isEqualTo(1);
    }

    @Test
    void 이미_가진_닉네임으로_addAlias하면_별칭이_늘지_않는다() throws Exception {
        long duelId = data.newFirstDuel();
        long[] top = data.newPlayers(5);
        long[] bottom = data.newPlayers(5);
        String ownNickname = jdbc.queryForObject("select nickname from player_alias where player_id = ?",
                String.class, top[0]);
        Req request = data.request(data.succeededJob(duelId), top, bottom, Game.random());
        request.topRow(0, row -> withAlias(row, ownNickname));

        register(duelId, request).andExpect(status().isCreated()).andExpect(jsonPath("$.addedAliases").isEmpty());
        assertThat(count("select count(*) from player_alias where player_id = ?", top[0])).isEqualTo(1);
    }

    @Test
    void 후보_상태_그대로_저장하면_422이고_NEW를_고르면_미등록_선수가_생긴다() throws Exception {
        long duelId = data.newFirstDuel();
        long[] top = data.newPlayers(5);
        long[] bottom = data.newPlayers(5);
        long jobId = data.succeededJob(duelId);
        String nickname = "신규" + System.nanoTime();
        Req request = data.request(jobId, top, bottom, Game.random());
        request.topRow(2, row -> unresolved(row));

        register(duelId, request).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.violations[0]").value(org.hamcrest.Matchers.containsString("골라야")));
        assertThat(count("select count(*) from game_set where duel_id = ?", duelId)).isZero();

        request.topRow(2, row -> newPlayer(row, nickname));
        String body = register(duelId, request).andExpect(status().isCreated())
                .andExpect(jsonPath("$.createdPlayers[0].nickname").value(nickname))
                .andReturn().getResponse().getContentAsString();
        long newId = ((Number) JsonPath.read(body, "$.createdPlayers[0].playerId")).longValue();
        assertThat(count("select count(*) from player where id = ? and real_name is null", newId)).isEqualTo(1);
        assertThat(count("select count(*) from player_alias where player_id = ?", newId)).isEqualTo(1);
        assertThat(count("select count(*) from game_set_player where player_id = ?", newId)).isEqualTo(1);
    }

    @Test
    void 별칭_충돌이_나면_409이고_먼저_만든_새_선수도_세트도_남지_않는다() throws Exception {
        long duelId = data.newFirstDuel();
        long[] top = data.newPlayers(5);
        long[] bottom = data.newPlayers(5);
        long[] others = data.newPlayers(1);
        String othersNickname = jdbc.queryForObject("select nickname from player_alias where player_id = ?",
                String.class, others[0]);
        String freshNickname = "롤백" + System.nanoTime();
        long before = count("select count(*) from player");

        Req request = data.request(data.succeededJob(duelId), top, bottom, Game.random());
        request.topRow(0, row -> newPlayer(row, freshNickname));          // 먼저 만들어지는 새 선수
        request.topRow(1, row -> withAlias(row, othersNickname));         // 다른 선수의 별칭이라 충돌

        register(duelId, request).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALIAS_CONFLICT"));

        assertThat(count("select count(*) from player")).isEqualTo(before);
        assertThat(count("select count(*) from player_alias where nickname = ?", freshNickname)).isZero();
        assertThat(count("select count(*) from game_set where duel_id = ?", duelId)).isZero();
    }

    @Test
    void 새_선수_닉네임이_이미_별칭이면_422_위반이다() throws Exception {
        long duelId = data.newFirstDuel();
        long[] top = data.newPlayers(5);
        long[] bottom = data.newPlayers(5);
        String taken = jdbc.queryForObject("select nickname from player_alias where player_id = ?", String.class,
                top[3]);
        Req request = data.request(data.succeededJob(duelId), top, bottom, Game.random());
        request.bottomRow(0, row -> newPlayer(row, taken));

        register(duelId, request).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.violations").isArray());
    }

    // ----- 이상한 입력 -----

    @Test
    void 이상한_경기_시간이나_빈_밴은_500이_아니라_422다() throws Exception {
        long duelId = data.newFirstDuel();
        long[] top = data.newPlayers(5);
        long[] bottom = data.newPlayers(5);
        for (String duration : new String[] {"00:00", "5:7"}) {
            Req request = data.request(data.succeededJob(duelId), top, bottom, new Game("3500-01-01", duration));
            register(duelId, request).andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.violations[0]").value(org.hamcrest.Matchers.containsString("경기 시간")));
        }
        Req emptyBan = data.request(data.succeededJob(duelId), top, bottom, Game.random());
        emptyBan.top.bans.set(0, null);
        register(duelId, emptyBan).andExpect(status().isUnprocessableContent());
        assertThat(count("select count(*) from game_set where duel_id = ?", duelId)).isZero();
    }

    @Test
    void 형태가_틀린_요청은_400_VALIDATION_FAILED다() throws Exception {
        long duelId = data.newFirstDuel();
        Req request = data.request(data.succeededJob(duelId), data.newPlayers(5), data.newPlayers(5), Game.random());
        request.top.rows.remove(4);
        check(duelId, request).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        Req aliasWithoutPlayer = data.request(data.succeededJob(duelId), data.newPlayers(5), data.newPlayers(5),
                Game.random());
        aliasWithoutPlayer.topRow(0, row -> withAlias(newPlayer(row, "아무개"), "아무개"));
        register(duelId, aliasWithoutPlayer).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    // ----- check -----

    @Test
    void check는_부작용이_없고_평가_결과를_돌려준다() throws Exception {
        long duelId = data.newFirstDuel();
        long[] top = data.newPlayers(5);
        long[] bottom = data.newPlayers(5);
        long jobId = data.succeededJob(duelId);
        Req request = data.request(jobId, top, bottom, Game.random());
        request.topRow(0, row -> newPlayer(row, "체크전용" + System.nanoTime()));
        request.topRow(1, row -> withAlias(row, "체크별칭" + System.nanoTime()));
        long playersBefore = count("select count(*) from player");
        long aliasesBefore = count("select count(*) from player_alias");
        long setsBefore = count("select count(*) from game_set");
        long jobsLinkedBefore = count("select count(*) from extraction_job where game_set_id is not null");

        check(duelId, request).andExpect(status().isOk())
                .andExpect(jsonPath("$.setNo").value(1))
                .andExpect(jsonPath("$.topTeam").value("TEAM1"))
                .andExpect(jsonPath("$.detection.method").value("FIRST_SET"))
                .andExpect(jsonPath("$.team1Side").value("BLUE"))
                .andExpect(jsonPath("$.fingerprint").isString())
                .andExpect(jsonPath("$.duplicateSet").value((Object) null))
                .andExpect(jsonPath("$.errors").isEmpty())
                .andExpect(jsonPath("$.teamChange.kind").value("NONE"));

        assertThat(count("select count(*) from player")).isEqualTo(playersBefore);
        assertThat(count("select count(*) from player_alias")).isEqualTo(aliasesBefore);
        assertThat(count("select count(*) from game_set")).isEqualTo(setsBefore);
        assertThat(count("select count(*) from extraction_job where game_set_id is not null"))
                .isEqualTo(jobsLinkedBefore);
        // 저장은 그대로 가능하다
        register(duelId, request).andExpect(status().isCreated());
    }

    @Test
    void check는_중복_지문이면_duplicateSet을_채운다() throws Exception {
        long duelId = data.newFirstDuel();
        long[] teamA = data.newPlayers(5);
        long[] teamB = data.newPlayers(5);
        Game game = Game.random();
        long firstSetId = registerFirstSet(duelId, teamA, teamB, game);

        check(duelId, data.request(data.succeededJob(duelId), teamB, teamA, game)).andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicateSet.setId").value(firstSetId))
                .andExpect(jsonPath("$.duplicateSet.duelId").value(duelId))
                .andExpect(jsonPath("$.duplicateSet.setNo").value(1));
    }

    // ----- 맥락·초안 -----

    @Test
    void 등록_맥락은_다음_세트_번호와_기본_진영과_제한_챔피언과_등록된_세트를_돌려준다() throws Exception {
        long duelId = data.newFirstDuel();
        mockMvc.perform(get("/api/admin/duels/" + duelId + "/registration-context"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nextSetNo").value(1))
                .andExpect(jsonPath("$.defaultTopSide").value((Object) null))
                .andExpect(jsonPath("$.restrictedChampions").isEmpty())
                .andExpect(jsonPath("$.fearlessMode").value("RESET"));

        long setId = registerFirstSet(duelId, data.newPlayers(5), data.newPlayers(5), Game.random());

        String body = mockMvc.perform(get("/api/admin/duels/" + duelId + "/registration-context"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nextSetNo").value(2))
                .andExpect(jsonPath("$.defaultTopSide").value("RED")) // 직전 위쪽 팀 A팀 → 2세트 레드
                .andExpect(jsonPath("$.registeredSets[0].id").value(setId))
                .andExpect(jsonPath("$.registeredSets[0].team1Side").value("BLUE"))
                .andReturn().getResponse().getContentAsString();
        List<String> restricted = JsonPath.read(body, "$.restrictedChampions");
        assertThat(restricted).hasSize(10).contains(champion(0), champion(9));
        mockMvc.perform(get("/api/admin/duels/987654321/registration-context")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DUEL_NOT_FOUND"));
    }

    @Test
    void 이어가기_대결의_제한_챔피언은_마지막_초기화_이후_전부다() throws Exception {
        long sessionId = data.newSession();
        long duel1 = data.newDuel(sessionId, FearlessMode.RESET);
        long[] teamA = data.newPlayers(5);
        long[] teamB = data.newPlayers(5);
        registerFirstSet(duel1, teamA, teamB, Game.random()); // 픽 00~09
        long duel2 = data.newDuel(sessionId, FearlessMode.CONTINUE);
        // 이어가기 대결 첫 세트에서 duel1의 픽(00~09)을 다시 고르면 위반
        register(duel2, data.request(data.succeededJob(duel2), teamA, teamB, Game.random()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.violations[0]").value(org.hamcrest.Matchers.containsString("피어리스")));
        // 밴이었던 챔피언(10~19 중 duel1 밴)을 픽하는 것은 허용
        register(duel2, RegistrationTestData.withOtherChampions(
                data.request(data.succeededJob(duel2), teamA, teamB, Game.random())))
                .andExpect(status().isCreated());
    }

    @Test
    void 이어가기_체인이_RESET에서_끊기는지_맥락으로_확인한다() throws Exception {
        long sessionId = data.newSession();
        long duel1 = data.newDuel(sessionId, FearlessMode.RESET);
        long[] teamA = data.newPlayers(5);
        long[] teamB = data.newPlayers(5);
        registerFirstSet(duel1, teamA, teamB, Game.random()); // 픽 00~09
        long duel2 = data.newDuel(sessionId, FearlessMode.CONTINUE);
        long duel3 = data.newDuel(sessionId, FearlessMode.CONTINUE);
        long duel4 = data.newDuel(sessionId, FearlessMode.RESET);

        for (long continued : new long[] {duel2, duel3}) {
            String body = mockMvc.perform(get("/api/admin/duels/" + continued + "/registration-context"))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertThat((List<String>) JsonPath.read(body, "$.restrictedChampions")).hasSize(10);
        }
        mockMvc.perform(get("/api/admin/duels/" + duel4 + "/registration-context"))
                .andExpect(jsonPath("$.restrictedChampions").isEmpty());
    }

    @Test
    void 초안은_형식대로_내려오고_저장하면_409_EXTRACTION_ALREADY_SAVED다() throws Exception {
        long duelId = data.newFirstDuel();
        long jobId = data.succeededJob(duelId);

        mockMvc.perform(get("/api/admin/extractions/" + jobId + "/draft"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value(jobId))
                .andExpect(jsonPath("$.duelId").value(duelId))
                .andExpect(jsonPath("$.top.rows.length()").value(5))
                .andExpect(jsonPath("$.top.rows[0].position").value("ADC"))
                .andExpect(jsonPath("$.top.rows[0].match.type").exists())
                .andExpect(jsonPath("$.top.rows[0].champion.candidates.length()").value(3))
                .andExpect(jsonPath("$.top.bans.length()").value(5))
                .andExpect(jsonPath("$.defaults.top.rows[0].resolution").exists())
                .andExpect(jsonPath("$.defaults.top.bans[0]").value(champion(10)))
                .andExpect(jsonPath("$.evaluation.setNo").value(1));

        mockMvc.perform(get("/api/admin/extractions/" + data.queuedJob(duelId) + "/draft"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EXTRACTION_NOT_READY"));
        mockMvc.perform(get("/api/admin/extractions/987654321/draft")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EXTRACTION_NOT_FOUND"));

        // 초안의 기본값에 새 선수 행을 채워 그대로 저장하면 이 작업은 저장된 것으로 본다
        Req request = data.request(jobId, data.newPlayers(5), data.newPlayers(5), Game.random());
        register(duelId, request).andExpect(status().isCreated());
        mockMvc.perform(get("/api/admin/extractions/" + jobId + "/draft")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EXTRACTION_ALREADY_SAVED"))
                .andExpect(jsonPath("$.savedSetId").isNumber());
    }

    // ----- 새 대결로 등록 -----

    @Test
    void 새_대결로_등록하면_대결이_만들어지고_작업이_옮겨지고_세트_1번으로_저장된다() throws Exception {
        long sessionId = data.newSession();
        long duelId = data.newDuel(sessionId, FearlessMode.RESET);
        long[] teamA = data.newPlayers(5);
        long[] teamB = data.newPlayers(5);
        registerFirstSet(duelId, teamA, teamB, Game.random());

        // 팀이 재구성된 세트: 같은 픽(00~09)도 초기화(RESET)한 새 대결이면 통과하고, 레드가 위쪽이어도 1세트는 블루=A팀
        long jobId = data.succeededJob(duelId);
        Req request = data.request(jobId, teamA, teamB, Game.random());
        request.newDuel = new NewDuelOption(FearlessMode.RESET);
        request.topSide = Side.RED;

        String body = register(duelId, request).andExpect(status().isCreated())
                .andExpect(jsonPath("$.setNo").value(1))
                .andExpect(jsonPath("$.topTeam").value("TEAM2"))
                .andReturn().getResponse().getContentAsString();

        long newDuelId = ((Number) JsonPath.read(body, "$.duelId")).longValue();
        assertThat(newDuelId).isNotEqualTo(duelId);
        Map<String, Object> duel = jdbc.queryForMap("select session_id, seq, fearless_mode from duel where id = ?",
                newDuelId);
        assertThat(((Number) duel.get("session_id")).longValue()).isEqualTo(sessionId);
        assertThat(((Number) duel.get("seq")).intValue()).isEqualTo(2);
        assertThat(duel.get("fearless_mode")).isEqualTo("RESET");
        assertThat(jdbc.queryForObject("select duel_id from extraction_job where id = ?", Long.class, jobId))
                .isEqualTo(newDuelId);
        assertThat(count("select count(*) from game_set where duel_id = ? and set_no = 1 and team1_side = 'BLUE'",
                newDuelId)).isEqualTo(1);
        assertThat(count("select count(*) from game_set where duel_id = ?", duelId)).isEqualTo(1);
    }

    @Test
    void 새_대결을_이어가기로_등록하면_이전_대결의_픽이_제한된다() throws Exception {
        long sessionId = data.newSession();
        long duelId = data.newDuel(sessionId, FearlessMode.RESET);
        long[] teamA = data.newPlayers(5);
        long[] teamB = data.newPlayers(5);
        registerFirstSet(duelId, teamA, teamB, Game.random());

        Req request = data.request(data.succeededJob(duelId), teamA, teamB, Game.random()); // 같은 픽 00~09
        request.newDuel = new NewDuelOption(FearlessMode.CONTINUE);

        check(duelId, request).andExpect(status().isOk()).andExpect(jsonPath("$.setNo").value(1))
                .andExpect(jsonPath("$.errors[0]").value(org.hamcrest.Matchers.containsString("피어리스")));
        long duelsBefore = count("select count(*) from duel where session_id = ?", sessionId);
        register(duelId, request).andExpect(status().isUnprocessableContent());
        // 실패하면 새 대결도 되돌아간다
        assertThat(count("select count(*) from duel where session_id = ?", sessionId)).isEqualTo(duelsBefore);

        Req otherPicks = RegistrationTestData.withOtherChampions(
                data.request(data.succeededJob(duelId), teamA, teamB, Game.random()));
        otherPicks.newDuel = new NewDuelOption(FearlessMode.CONTINUE);
        register(duelId, otherPicks).andExpect(status().isCreated());
        assertThat(count("select count(*) from duel where session_id = ? and fearless_mode = 'CONTINUE'", sessionId))
                .isEqualTo(1);
    }

    @Test
    void check에_newDuel을_담으면_새_대결_기준으로_평가한다() throws Exception {
        long duelId = data.newFirstDuel();
        long[] teamA = data.newPlayers(5);
        long[] teamB = data.newPlayers(5);
        registerFirstSet(duelId, teamA, teamB, Game.random());
        Req request = RegistrationTestData.withOtherChampions(
                data.request(data.succeededJob(duelId), teamA, teamB, Game.random()));

        check(duelId, request).andExpect(jsonPath("$.setNo").value(2))
                .andExpect(jsonPath("$.teamChange.kind").value("NONE"));

        request.newDuel = new NewDuelOption(FearlessMode.RESET);
        long duelsBefore = count("select count(*) from duel");
        check(duelId, request).andExpect(jsonPath("$.setNo").value(1))
                .andExpect(jsonPath("$.detection.method").value("FIRST_SET"));
        assertThat(count("select count(*) from duel")).isEqualTo(duelsBefore);
    }
}
