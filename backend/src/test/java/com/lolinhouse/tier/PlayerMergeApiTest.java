package com.lolinhouse.tier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.lolinhouse.IntegrationTest;
import com.lolinhouse.domain.DuelTeam;
import com.lolinhouse.domain.Side;
import com.lolinhouse.domain.player.NicknameNormalizer;
import com.lolinhouse.player.PlayerAliasRepository;
import com.lolinhouse.player.PlayerRepository;
import com.lolinhouse.record.PublicReadFixtures;
import com.lolinhouse.session.DuelRepository;
import com.lolinhouse.session.FearlessMode;
import com.lolinhouse.session.GameSetRepository;
import com.lolinhouse.session.InhouseSessionRepository;
import org.junit.jupiter.api.BeforeEach;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 역할: 선수 합치기 API(POST /api/admin/players/{sourceId}/merge) 검증. 세션 날짜는 2131년대를 쓰고(세션 시작일은 UNIQUE라 클래스 전체에서 번호를 이어 쓴다),
 * 공개 프로필은 from/to로 그 구간을 읽는다.
 */
@IntegrationTest
@AutoConfigureMockMvc
class PlayerMergeApiTest {

    private static final String RANGE = "?from=2131-01-01&to=2139-12-31";

    @Autowired
    MockMvc mockMvc;
    @Autowired
    PlayerRepository players;
    @Autowired
    PlayerAliasRepository aliases;
    @Autowired
    InhouseSessionRepository sessions;
    @Autowired
    DuelRepository duels;
    @Autowired
    GameSetRepository gameSets;
    @Autowired
    JdbcTemplate jdbc;

    PublicReadFixtures fixtures;
    static final java.util.concurrent.atomic.AtomicInteger SEQ = new java.util.concurrent.atomic.AtomicInteger();

    @BeforeEach
    void setUp() {
        fixtures = new PublicReadFixtures(players, sessions, duels, gameSets);
    }

    /** 선수 10명이 뛴 세트 하나를 만든다. */
    void playSet(List<Long> ids) {
        long duel = fixtures.duel(fixtures.session(PublicReadFixtures.day(2131, SEQ.incrementAndGet())), 1, FearlessMode.RESET);
        fixtures.set(duel, 1, Side.BLUE, DuelTeam.TEAM1, ids, PublicReadFixtures.champs(0, 5),
                PublicReadFixtures.champs(5, 5), PublicReadFixtures.champs(10, 10));
    }

    /** first + 새 선수 9명. */
    List<Long> tenWith(long first) {
        List<Long> ids = new ArrayList<>(fixtures.tenPlayers().subList(1, 10));
        ids.add(0, first);
        return ids;
    }

    long addAlias(long playerId, String nickname) {
        String normalized = NicknameNormalizer.normalize(nickname);
        jdbc.update("insert into player_alias (player_id, nickname, nickname_key, created_at)"
                + " values (?, ?, ?, UTC_TIMESTAMP(6))", playerId, normalized,
                NicknameNormalizer.comparisonKey(normalized));
        return playerId;
    }

    ResultActions merge(long source, long target) throws Exception {
        return mockMvc.perform(post("/api/admin/players/" + source + "/merge").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"targetId\":" + target + "}"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void 세트_기록과_별칭이_target으로_옮겨지고_source는_사라진다() throws Exception {
        String jp = "テスト" + UUID.randomUUID().toString().substring(0, 6);
        long source = addAlias(fixtures.unregisteredPlayer("tmp"), jp);
        long target = fixtures.registeredPlayer("합침대상");
        addAlias(target, "대상닉" + UUID.randomUUID().toString().substring(0, 6));
        playSet(tenWith(source));
        playSet(tenWith(target));
        jdbc.update("insert into player_lane_tier (player_id, position, tier, sub, updated_at)"
                + " values (?, 'TOP', 3, 'MID', UTC_TIMESTAMP(6))", source);

        merge(source, target).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(target))
                .andExpect(jsonPath("$.aliases.length()").value(2));

        mockMvc.perform(get("/api/players/" + target + RANGE)).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSets").value(2));
        mockMvc.perform(get("/api/players/" + source + RANGE)).andExpect(status().isNotFound());
        var found = aliases.findByNicknameKeyIn(List.of(NicknameNormalizer.comparisonKey(jp)));
        assertThat(found).hasSize(1);
        assertThat(found.get(0).playerId()).isEqualTo(target);
        assertThat(jdbc.queryForObject("select count(*) from player_lane_tier where player_id = ?", Integer.class,
                source)).isZero();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void 등록_선수를_source로_하면_400() throws Exception {
        merge(fixtures.registeredPlayer("a"), fixtures.registeredPlayer("b"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MERGE_SOURCE_REGISTERED"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void 같은_세트에_둘_다_있으면_409이고_아무것도_바뀌지_않는다() throws Exception {
        long source = fixtures.unregisteredPlayer("tmp");
        long target = fixtures.registeredPlayer("충돌");
        List<Long> ids = tenWith(source);
        ids.set(1, target);
        playSet(ids);

        merge(source, target).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLAYER_MERGE_CONFLICT"));

        assertThat(players.existsById(source)).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from game_set_player where player_id = ?", Integer.class,
                source)).isEqualTo(1);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void 자기_자신은_400_없는_id는_404() throws Exception {
        long source = fixtures.unregisteredPlayer("tmp");
        long target = fixtures.registeredPlayer("대상");
        merge(source, source).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MERGE_SAME_PLAYER"));
        merge(source, Long.MAX_VALUE).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLAYER_NOT_FOUND"));
        merge(Long.MAX_VALUE, target).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLAYER_NOT_FOUND"));
        assertThat(players.existsById(source)).isTrue();
    }

    @Test
    void 비로그인은_401() throws Exception {
        merge(1, 2).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void CSRF_토큰이_없으면_403() throws Exception {
        mockMvc.perform(post("/api/admin/players/1/merge").contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetId\":2}")).andExpect(status().isForbidden());
    }
}
