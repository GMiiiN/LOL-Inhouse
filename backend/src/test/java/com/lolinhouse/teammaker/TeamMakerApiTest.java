package com.lolinhouse.teammaker;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.lolinhouse.IntegrationTest;
import com.lolinhouse.TestRandomConfiguration.FixedRandom;
import com.lolinhouse.domain.Position;
import com.lolinhouse.domain.team.Slot;
import com.lolinhouse.domain.team.TeamMakeResult;
import com.lolinhouse.domain.team.TeamMakerPlayer;
import com.lolinhouse.domain.team.TeamOption;
import com.lolinhouse.domain.team.TogetherPair;
import com.lolinhouse.domain.tier.LaneTier;
import com.lolinhouse.domain.tier.ScoreTable;
import com.lolinhouse.domain.tier.TierSub;
import com.lolinhouse.player.Player;
import com.lolinhouse.player.PlayerRepository;
import com.lolinhouse.tier.PlayerLaneTier;
import com.lolinhouse.tier.PlayerLaneTierRepository;
import com.lolinhouse.tier.ScoreSetting;
import com.lolinhouse.tier.ScoreSettingRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 역할: 공개 팀 짜기 API(자동·수동)의 오류 코드 표, 응답 모양, 좌우 교체, 보안(비로그인 200 · CSRF 없으면 403), 남용 방지(동시 계산 · 분당 횟수)를
 * 가짜 TeamEngine으로 검증한다. 도메인 계산 결과는 테스트가 주입하므로 TeamMaker 구현과 무관하다.
 * 공개 POST는 csrf() 후처리기를 쓰고, 테스트마다 고유 접속 주소를 줘서 분당 횟수 한도가 서로 섞이지 않게 한다.
 */
@IntegrationTest
@AutoConfigureMockMvc
class TeamMakerApiTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    PlayerRepository playerRepository;
    @Autowired
    PlayerLaneTierRepository laneTierRepository;
    @Autowired
    ScoreSettingRepository scoreSettingRepository;
    @Autowired
    FixedRandom fixedRandom;

    @MockitoBean
    TeamEngine engine;

    @AfterEach
    void resetRandom() {
        fixedRandom.reset();
    }

    // ---------- 데이터·요청 도우미 ----------

    long newPlayer(Position... lanes) {
        Player player = playerRepository.save(Player.unregistered("tm" + UUID.randomUUID().toString().substring(0, 12)));
        for (int i = 0; i < lanes.length; i++) {
            laneTierRepository.save(PlayerLaneTier.create(player.id(), lanes[i], new LaneTier(1 + i % 6, TierSub.MID)));
        }
        return player.id();
    }

    List<Long> tenPlayers() {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            ids.add(newPlayer(Position.values()));
        }
        return ids;
    }

    static String addr() {
        return "test-" + UUID.randomUUID();
    }

    static RequestPostProcessor from(String address) {
        return request -> {
            request.setRemoteAddr(address);
            return request;
        };
    }

    ResultActions postJson(String path, String body, String address) throws Exception {
        return mockMvc.perform(post(path).with(csrf()).with(from(address))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    ResultActions auto(List<Long> ids, String together) throws Exception {
        return postJson("/api/team-maker/auto", autoBody(ids, together), addr());
    }

    static String autoBody(List<Long> ids, String together) {
        return "{\"participantIds\":" + ids + ",\"together\":" + together + "}";
    }

    static String manualBody(List<Long> ids, String together) {
        StringBuilder picks = new StringBuilder();
        Position[] positions = Position.values();
        for (int i = 0; i < positions.length; i++) {
            if (i > 0) {
                picks.append(',');
            }
            picks.append('"').append(positions[i]).append("\":[").append(ids.get(2 * i)).append(',')
                    .append(ids.get(2 * i + 1)).append(']');
        }
        return "{\"picks\":{" + picks + "},\"together\":" + together + "}";
    }

    ResultActions manual(List<Long> ids, String together) throws Exception {
        return postJson("/api/team-maker/manual", manualBody(ids, together), addr());
    }

    /** 앞 5명이 블루, 뒤 5명이 레드인 방안 4개. i번째 방안은 blueSum 100+i, redSum 200+i로 구별한다. */
    static TeamMakeResult fourOptions(List<Long> ids) {
        List<TeamOption> options = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            List<Slot> blue = new ArrayList<>();
            List<Slot> red = new ArrayList<>();
            for (int p = 0; p < 5; p++) {
                Position position = Position.values()[p];
                blue.add(new Slot(ids.get(p), position, new LaneTier(2, TierSub.HIGH), 70));
                red.add(new Slot(ids.get(5 + p), position, p == 0 ? null : new LaneTier(4, TierSub.LOW), 40));
            }
            options.add(new TeamOption(blue, red, 100 + i, 200 + i, 100 + i, 5, 305 + i, 300 + 2 * i));
        }
        return TeamMakeResult.success(options);
    }

    void engineReturns(List<Long> ids) {
        when(engine.auto(any(), any(), any(), any(), any())).thenReturn(fourOptions(ids));
        when(engine.manual(any(), any(), any(), any())).thenReturn(fourOptions(ids));
    }

    // ---------- 보안 ----------

    @Test
    void 비로그인도_csrf가_있으면_200이다() throws Exception {
        List<Long> ids = tenPlayers();
        engineReturns(ids);
        auto(ids, "[]").andExpect(status().isOk());
    }

    @Test
    void csrf가_없으면_403이다() throws Exception {
        List<Long> ids = tenPlayers();
        mockMvc.perform(post("/api/team-maker/auto").contentType(MediaType.APPLICATION_JSON)
                .content(autoBody(ids, "[]"))).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/team-maker/manual").contentType(MediaType.APPLICATION_JSON)
                .content(manualBody(ids, "[]"))).andExpect(status().isForbidden());
    }

    // ---------- 응답 모양 ----------

    @Test
    void 자동_응답은_방안_4개와_scoreMatrix를_돌려준다() throws Exception {
        List<Long> ids = new ArrayList<>(tenPlayers());
        long noTop = newPlayer(Position.JUNGLE);
        ids.set(9, noTop);
        engineReturns(ids);
        ScoreTable table = scoreSettingRepository.findById(ScoreSetting.SINGLE_ID).orElseThrow().toTable();

        auto(ids, "[]").andExpect(status().isOk())
                .andExpect(jsonPath("$.options.length()").value(4))
                .andExpect(jsonPath("$.options[0].blue.length()").value(5))
                .andExpect(jsonPath("$.options[0].blue[0].playerId").value(ids.get(0)))
                .andExpect(jsonPath("$.options[0].blue[0].position").value("TOP"))
                .andExpect(jsonPath("$.options[0].blue[0].tier").value(2))
                .andExpect(jsonPath("$.options[0].blue[0].sub").value("HIGH"))
                .andExpect(jsonPath("$.options[0].blue[0].score").value(70))
                .andExpect(jsonPath("$.options[0].red[0].tier").doesNotExist())
                .andExpect(jsonPath("$.options[0].red[0].score").value(40))
                .andExpect(jsonPath("$.options[0].blueSum").value(100))
                .andExpect(jsonPath("$.options[0].redSum").value(200))
                .andExpect(jsonPath("$.options[0].diff").value(100))
                .andExpect(jsonPath("$.options[0].gap").value(5))
                .andExpect(jsonPath("$.options[0].cost").value(305))
                .andExpect(jsonPath("$.options[0].total").value(300))
                // 티어 없는 칸은 대체 점수(6티어 하)이고 hasTier=false
                .andExpect(jsonPath("$.scoreMatrix['" + noTop + "'].TOP.hasTier").value(false))
                .andExpect(jsonPath("$.scoreMatrix['" + noTop + "'].TOP.tier").doesNotExist())
                .andExpect(jsonPath("$.scoreMatrix['" + noTop + "'].TOP.score")
                        .value(table.fallbackPoints(Position.TOP)))
                .andExpect(jsonPath("$.scoreMatrix['" + noTop + "'].JUNGLE.hasTier").value(true))
                .andExpect(jsonPath("$.scoreMatrix['" + noTop + "'].JUNGLE.tier").value(1))
                .andExpect(jsonPath("$.scoreMatrix['" + noTop + "'].JUNGLE.sub").value("MID"))
                .andExpect(jsonPath("$.scoreMatrix['" + noTop + "'].JUNGLE.score")
                        .value(table.points(new LaneTier(1, TierSub.MID), Position.JUNGLE)))
                .andExpect(jsonPath("$.scoreMatrix['" + ids.get(0) + "'].SUPPORT.tier").value(5));
    }

    @Test
    void 수동_응답도_같은_모양이다() throws Exception {
        List<Long> ids = tenPlayers();
        engineReturns(ids);
        manual(ids, "[]").andExpect(status().isOk())
                .andExpect(jsonPath("$.options.length()").value(4))
                .andExpect(jsonPath("$.scoreMatrix.length()").value(10));
    }

    @Test
    void 참가자는_클릭한_순서_그대로_도메인에_들어간다() throws Exception {
        List<Long> ids = tenPlayers();
        List<Long> shuffled = new ArrayList<>(ids);
        java.util.Collections.reverse(shuffled);
        engineReturns(shuffled);
        auto(shuffled, "[]").andExpect(status().isOk());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<TeamMakerPlayer>> captor = ArgumentCaptor.forClass(List.class);
        org.mockito.Mockito.verify(engine).auto(captor.capture(), any(), any(), any(), any());
        assertThat(captor.getValue().stream().map(TeamMakerPlayer::id).toList()).isEqualTo(shuffled);
    }

    @Test
    void 순서만_다른_같은_쌍은_하나로_합쳐_도메인에_들어간다() throws Exception {
        List<Long> ids = tenPlayers();
        engineReturns(ids);
        auto(ids, "[[" + ids.get(0) + "," + ids.get(1) + "],[" + ids.get(1) + "," + ids.get(0) + "]]")
                .andExpect(status().isOk());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Set<TogetherPair>> captor = ArgumentCaptor.forClass(Set.class);
        org.mockito.Mockito.verify(engine).auto(any(), captor.capture(), any(), any(), any());
        assertThat(captor.getValue()).containsExactly(new TogetherPair(ids.get(0), ids.get(1)));
    }

    // ---------- apart (꼭 다른 팀) ----------

    ResultActions autoWithApart(List<Long> ids, String together, String apart) throws Exception {
        return postJson("/api/team-maker/auto", "{\"participantIds\":" + ids + ",\"together\":" + together
                + ",\"apart\":" + apart + "}", addr());
    }

    ResultActions manualWithApart(List<Long> ids, String apart) throws Exception {
        String body = manualBody(ids, "[]");
        return postJson("/api/team-maker/manual", body.substring(0, body.length() - 1) + ",\"apart\":" + apart + "}",
                addr());
    }

    @Test
    void apart_쌍이_도메인에_전달된다_순서만_다른_쌍은_합쳐진다() throws Exception {
        List<Long> ids = tenPlayers();
        engineReturns(ids);
        autoWithApart(ids, "[]", "[[" + ids.get(2) + "," + ids.get(3) + "],[" + ids.get(3) + "," + ids.get(2) + "]]")
                .andExpect(status().isOk());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Set<TogetherPair>> captor = ArgumentCaptor.forClass(Set.class);
        org.mockito.Mockito.verify(engine).auto(any(), any(), captor.capture(), any(), any());
        assertThat(captor.getValue()).containsExactly(new TogetherPair(ids.get(2), ids.get(3)));
    }

    @Test
    void apart가_없으면_빈_집합이_전달된다() throws Exception {
        List<Long> ids = tenPlayers();
        engineReturns(ids);
        auto(ids, "[]").andExpect(status().isOk());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Set<TogetherPair>> captor = ArgumentCaptor.forClass(Set.class);
        org.mockito.Mockito.verify(engine).auto(any(), any(), captor.capture(), any(), any());
        assertThat(captor.getValue()).isEmpty();
    }

    @Test
    void apart_5쌍은_통과하고_6쌍은_400_TEAM_PAIR_INVALID() throws Exception {
        List<Long> ids = tenPlayers();
        engineReturns(ids);
        autoWithApart(ids, "[]", pairs(ids, 5)).andExpect(status().isOk());
        autoWithApart(ids, "[]", pairs(ids, 6)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TEAM_PAIR_INVALID"));
    }

    @Test
    void apart에_참가자가_아닌_사람이나_자기_자신이나_한_명짜리_쌍은_400() throws Exception {
        List<Long> ids = tenPlayers();
        autoWithApart(ids, "[]", "[[" + ids.get(0) + ",999999999]]").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TEAM_PAIR_INVALID"));
        autoWithApart(ids, "[]", "[[" + ids.get(0) + "," + ids.get(0) + "]]").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TEAM_PAIR_INVALID"));
        autoWithApart(ids, "[]", "[[" + ids.get(0) + "]]").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TEAM_PAIR_INVALID"));
    }

    @Test
    void together와_같은_쌍을_apart에_넣으면_순서가_달라도_400_TEAM_PAIR_INVALID() throws Exception {
        List<Long> ids = tenPlayers();
        autoWithApart(ids, "[[" + ids.get(0) + "," + ids.get(1) + "]]", "[[" + ids.get(1) + "," + ids.get(0) + "]]")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("TEAM_PAIR_INVALID"));
    }

    @Test
    void 수동에서도_apart가_전달되고_together와_겹치면_400() throws Exception {
        List<Long> ids = tenPlayers();
        engineReturns(ids);
        manualWithApart(ids, "[[" + ids.get(0) + "," + ids.get(2) + "]]").andExpect(status().isOk());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Set<TogetherPair>> captor = ArgumentCaptor.forClass(Set.class);
        org.mockito.Mockito.verify(engine).manual(any(), any(), captor.capture(), any());
        assertThat(captor.getValue()).containsExactly(new TogetherPair(ids.get(0), ids.get(2)));

        postJson("/api/team-maker/manual", manualBody(ids, "[[" + ids.get(0) + "," + ids.get(2) + "]]").replaceFirst("}$",
                ",\"apart\":[[" + ids.get(2) + "," + ids.get(0) + "]]}"), addr())
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("TEAM_PAIR_INVALID"));
    }

    // ---------- 좌우 교체 ----------

    @Test
    void 방안마다_nextBoolean이_true면_블루와_레드가_통째로_바뀐다() throws Exception {
        List<Long> ids = tenPlayers();
        engineReturns(ids);
        fixedRandom.setBooleans(true, false, true, false);

        auto(ids, "[]").andExpect(status().isOk())
                // 0번: 교체됨
                .andExpect(jsonPath("$.options[0].blue[0].playerId").value(ids.get(5)))
                .andExpect(jsonPath("$.options[0].red[0].playerId").value(ids.get(0)))
                .andExpect(jsonPath("$.options[0].blueSum").value(200))
                .andExpect(jsonPath("$.options[0].redSum").value(100))
                .andExpect(jsonPath("$.options[0].diff").value(100))
                // 1번: 그대로
                .andExpect(jsonPath("$.options[1].blue[0].playerId").value(ids.get(0)))
                .andExpect(jsonPath("$.options[1].blueSum").value(101))
                // 2번: 교체됨
                .andExpect(jsonPath("$.options[2].blue[0].playerId").value(ids.get(5)))
                .andExpect(jsonPath("$.options[2].blueSum").value(202))
                // 3번: 그대로
                .andExpect(jsonPath("$.options[3].blue[0].playerId").value(ids.get(0)))
                .andExpect(jsonPath("$.options[3].blueSum").value(103));
    }

    // ---------- 오류 코드 표: 자동 ----------

    @Test
    void 자동_9명이면_400_TEAM_PARTICIPANT_COUNT와_count() throws Exception {
        List<Long> ids = tenPlayers().subList(0, 9);
        auto(ids, "[]").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TEAM_PARTICIPANT_COUNT"))
                .andExpect(jsonPath("$.count").value(9));
    }

    @Test
    void 자동_참가자_목록이_없으면_400_TEAM_PARTICIPANT_COUNT() throws Exception {
        postJson("/api/team-maker/auto", "{}", addr()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TEAM_PARTICIPANT_COUNT"))
                .andExpect(jsonPath("$.count").value(0));
    }

    @Test
    void 자동_같은_사람이_두_번이면_400_TEAM_DUPLICATE_PARTICIPANT() throws Exception {
        List<Long> ids = new ArrayList<>(tenPlayers());
        ids.set(9, ids.get(0));
        auto(ids, "[]").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TEAM_DUPLICATE_PARTICIPANT"))
                .andExpect(jsonPath("$.playerId").value(ids.get(0)));
    }

    @Test
    void 자동_없는_선수면_400_TEAM_PLAYER_NOT_FOUND() throws Exception {
        List<Long> ids = new ArrayList<>(tenPlayers());
        long missing = 999_999_999L;
        ids.set(3, missing);
        auto(ids, "[]").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TEAM_PLAYER_NOT_FOUND"))
                .andExpect(jsonPath("$.playerId").value(missing));
    }

    @Test
    void 쌍에_참가자가_아닌_사람이_있으면_400_TEAM_PAIR_INVALID() throws Exception {
        List<Long> ids = tenPlayers();
        long outsider = newPlayer(Position.values());
        auto(ids, "[[" + ids.get(0) + "," + outsider + "]]").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TEAM_PAIR_INVALID"));
    }

    @Test
    void 자기_자신과의_쌍이면_400_TEAM_PAIR_INVALID() throws Exception {
        List<Long> ids = tenPlayers();
        auto(ids, "[[" + ids.get(0) + "," + ids.get(0) + "]]").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TEAM_PAIR_INVALID"));
    }

    @Test
    void 쌍이_두_사람이_아니면_400_TEAM_PAIR_INVALID() throws Exception {
        List<Long> ids = tenPlayers();
        auto(ids, "[[" + ids.get(0) + "]]").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TEAM_PAIR_INVALID"));
    }

    @Test
    void 쌍이_6개면_400_TEAM_PAIR_INVALID이고_5개는_통과한다() throws Exception {
        List<Long> ids = tenPlayers();
        engineReturns(ids);
        String five = pairs(ids, 5);
        String six = pairs(ids, 6);
        auto(ids, six).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TEAM_PAIR_INVALID"));
        auto(ids, five).andExpect(status().isOk());
    }

    /** 서로 다른 사람끼리의 쌍 n개(순서만 다른 중복이 아니도록 인접한 사람끼리 이어 만든다). */
    static String pairs(List<Long> ids, int n) {
        List<String> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            list.add("[" + ids.get(i) + "," + ids.get((i + 1) % 10) + "]");
        }
        return list.stream().collect(Collectors.joining(",", "[", "]"));
    }

    @Test
    void 자동_결과가_0개면_422_TEAM_NO_VALID_SPLIT() throws Exception {
        List<Long> ids = tenPlayers();
        when(engine.auto(any(), any(), any(), any(), any())).thenReturn(TeamMakeResult.failure(TeamMakeResult.Failure.NO_VALID_SPLIT));
        auto(ids, "[]").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("TEAM_NO_VALID_SPLIT"));
    }

    // ---------- 라인 고정 (자동) ----------

    static String autoBodyFixed(List<Long> ids, String fixedLanes) {
        return "{\"participantIds\":" + ids + ",\"together\":[],\"fixedLanes\":" + fixedLanes + "}";
    }

    @SuppressWarnings("unchecked")
    java.util.Map<Long, Position> capturedFixedLanes() {
        ArgumentCaptor<java.util.Map<Long, Position>> captor = ArgumentCaptor.forClass(java.util.Map.class);
        org.mockito.Mockito.verify(engine).auto(any(), any(), any(), any(), captor.capture());
        return captor.getValue();
    }

    @Test
    void 고정_라인은_도메인에_그대로_전달되고_값이_null인_키는_무시한다() throws Exception {
        List<Long> ids = tenPlayers();
        engineReturns(ids);
        postJson("/api/team-maker/auto", autoBodyFixed(ids,
                "{\"" + ids.get(0) + "\":\"TOP\",\"" + ids.get(1) + "\":\"MID\",\"" + ids.get(2) + "\":null}"), addr())
                .andExpect(status().isOk());
        assertThat(capturedFixedLanes()).containsOnly(
                org.assertj.core.api.Assertions.entry(ids.get(0), Position.TOP),
                org.assertj.core.api.Assertions.entry(ids.get(1), Position.MID));
    }

    @Test
    void fixedLanes를_생략하면_빈_맵이_전달된다() throws Exception {
        List<Long> ids = tenPlayers();
        engineReturns(ids);
        auto(ids, "[]").andExpect(status().isOk());
        assertThat(capturedFixedLanes()).isEmpty();
    }

    @Test
    void 참가자가_아닌_사람을_고정하면_400_TEAM_LANE_FIX_INVALID() throws Exception {
        List<Long> ids = tenPlayers();
        long outsider = newPlayer(Position.values());
        postJson("/api/team-maker/auto", autoBodyFixed(ids, "{\"" + outsider + "\":\"TOP\"}"), addr())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TEAM_LANE_FIX_INVALID"));
    }

    @Test
    void 한_라인에_3명을_고정하면_400_TEAM_LANE_FIX_INVALID와_position_count() throws Exception {
        List<Long> ids = tenPlayers();
        postJson("/api/team-maker/auto", autoBodyFixed(ids, "{\"" + ids.get(0) + "\":\"MID\",\"" + ids.get(1)
                + "\":\"MID\",\"" + ids.get(2) + "\":\"MID\"}"), addr())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TEAM_LANE_FIX_INVALID"))
                .andExpect(jsonPath("$.position").value("MID"))
                .andExpect(jsonPath("$.count").value(3))
                .andExpect(jsonPath("$.detail").value("미드에 3명이 고정돼 있어요. 한 라인은 양 팀 합쳐 2명까지만 고정할 수 있어요."));
    }

    @Test
    void 한_라인에_2명_고정은_허용한다() throws Exception {
        List<Long> ids = tenPlayers();
        engineReturns(ids);
        postJson("/api/team-maker/auto", autoBodyFixed(ids, "{\"" + ids.get(0) + "\":\"MID\",\"" + ids.get(1)
                + "\":\"MID\"}"), addr()).andExpect(status().isOk());
    }

    @Test
    void 고정이_있고_결과가_0개면_422_TEAM_FIXED_LANE_BLOCKED() throws Exception {
        List<Long> ids = tenPlayers();
        when(engine.auto(any(), any(), any(), any(), any())).thenReturn(TeamMakeResult.failure(TeamMakeResult.Failure.NO_VALID_SPLIT));
        postJson("/api/team-maker/auto", autoBodyFixed(ids, "{\"" + ids.get(0) + "\":\"TOP\"}"), addr())
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("TEAM_FIXED_LANE_BLOCKED"));
    }

    @Test
    void 고정이_전부_null이면_결과_0개는_TEAM_NO_VALID_SPLIT이다() throws Exception {
        List<Long> ids = tenPlayers();
        when(engine.auto(any(), any(), any(), any(), any())).thenReturn(TeamMakeResult.failure(TeamMakeResult.Failure.NO_VALID_SPLIT));
        postJson("/api/team-maker/auto", autoBodyFixed(ids, "{\"" + ids.get(0) + "\":null}"), addr())
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("TEAM_NO_VALID_SPLIT"));
    }

    // ---------- 오류 코드 표: 수동 ----------

    @Test
    void 수동_라인이_빠지면_400_TEAM_LANE_PICK_INCOMPLETE와_position() throws Exception {
        List<Long> ids = tenPlayers();
        postJson("/api/team-maker/manual", "{\"picks\":{\"TOP\":[" + ids.get(0) + "," + ids.get(1) + "]},\"together\":[]}",
                addr()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TEAM_LANE_PICK_INCOMPLETE"))
                .andExpect(jsonPath("$.position").value("JUNGLE"));
    }

    @Test
    void 수동_라인에_한_명만_있으면_400_TEAM_LANE_PICK_INCOMPLETE() throws Exception {
        List<Long> ids = tenPlayers();
        String body = manualBody(ids, "[]").replace("\"MID\":[" + ids.get(4) + "," + ids.get(5) + "]",
                "\"MID\":[" + ids.get(4) + "]");
        postJson("/api/team-maker/manual", body, addr()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TEAM_LANE_PICK_INCOMPLETE"))
                .andExpect(jsonPath("$.position").value("MID"));
    }

    @Test
    void 수동_picks가_없으면_400_TEAM_LANE_PICK_INCOMPLETE() throws Exception {
        postJson("/api/team-maker/manual", "{}", addr()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TEAM_LANE_PICK_INCOMPLETE"))
                .andExpect(jsonPath("$.position").value("TOP"));
    }

    @Test
    void 수동_같은_사람이_두_번이면_400_TEAM_DUPLICATE_PARTICIPANT와_playerId() throws Exception {
        List<Long> ids = new ArrayList<>(tenPlayers());
        ids.set(9, ids.get(0));
        manual(ids, "[]").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TEAM_DUPLICATE_PARTICIPANT"))
                .andExpect(jsonPath("$.playerId").value(ids.get(0)));
    }

    @Test
    void 수동_그_라인_티어가_없으면_400_TEAM_LANE_TIER_MISSING() throws Exception {
        List<Long> ids = new ArrayList<>(tenPlayers());
        long noMid = newPlayer(Position.TOP, Position.JUNGLE);
        ids.set(4, noMid); // MID 라인 첫째
        manual(ids, "[]").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TEAM_LANE_TIER_MISSING"))
                .andExpect(jsonPath("$.position").value("MID"))
                .andExpect(jsonPath("$.playerId").value(noMid));
    }

    @Test
    void 수동_없는_선수면_400_TEAM_PLAYER_NOT_FOUND() throws Exception {
        List<Long> ids = new ArrayList<>(tenPlayers());
        ids.set(0, 999_999_999L);
        manual(ids, "[]").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TEAM_PLAYER_NOT_FOUND"));
    }

    @Test
    void 수동_쌍이_6개면_400_TEAM_PAIR_INVALID() throws Exception {
        List<Long> ids = tenPlayers();
        manual(ids, pairs(ids, 6)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TEAM_PAIR_INVALID"));
    }

    @Test
    void 수동_결과가_0개면_422_TEAM_PAIR_UNSATISFIABLE() throws Exception {
        List<Long> ids = tenPlayers();
        when(engine.manual(any(), any(), any(), any()))
                .thenReturn(TeamMakeResult.failure(TeamMakeResult.Failure.PAIR_UNSATISFIABLE));
        manual(ids, "[]").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("TEAM_PAIR_UNSATISFIABLE"));
    }

    // ---------- 남용 방지 ----------

    @Test
    void 동시_계산_3개_중_1개는_503_TEAM_MAKER_BUSY이고_자리가_나면_다시_된다() throws Exception {
        List<Long> ids = tenPlayers();
        CountDownLatch entered = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        when(engine.auto(any(), any(), any(), any(), any())).thenAnswer(invocation -> {
            entered.countDown();
            release.await(10, TimeUnit.SECONDS);
            return fourOptions(ids);
        });

        CompletableFuture<Integer> first = CompletableFuture.supplyAsync(() -> statusOf(ids));
        CompletableFuture<Integer> second = CompletableFuture.supplyAsync(() -> statusOf(ids));
        assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();

        auto(ids, "[]").andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("TEAM_MAKER_BUSY"));

        release.countDown();
        assertThat(first.get(10, TimeUnit.SECONDS)).isEqualTo(200);
        assertThat(second.get(10, TimeUnit.SECONDS)).isEqualTo(200);

        auto(ids, "[]").andExpect(status().isOk());
    }

    private int statusOf(List<Long> ids) {
        try {
            return auto(ids, "[]").andReturn().getResponse().getStatus();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void 같은_주소의_31번째_요청은_429_RATE_LIMITED이고_다른_주소는_통과한다() throws Exception {
        String address = addr();
        String badBody = autoBody(List.of(1L), "[]");
        for (int i = 0; i < 30; i++) {
            postJson("/api/team-maker/auto", badBody, address).andExpect(status().isBadRequest());
        }
        postJson("/api/team-maker/auto", badBody, address).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
        postJson("/api/team-maker/manual", "{}", address).andExpect(status().isTooManyRequests());
        postJson("/api/team-maker/auto", badBody, addr()).andExpect(status().isBadRequest());
    }
}
