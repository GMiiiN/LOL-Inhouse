package com.lolinhouse.tier;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import com.lolinhouse.IntegrationTest;
import com.lolinhouse.domain.DuelTeam;
import com.lolinhouse.domain.Position;
import com.lolinhouse.domain.Side;
import com.lolinhouse.domain.tier.LaneTier;
import com.lolinhouse.domain.tier.TierSub;
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

import static com.lolinhouse.record.PublicReadFixtures.champs;
import static com.lolinhouse.record.PublicReadFixtures.day;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 역할: GET /api/admin/tier-recommendations(담당자 전용)와 tier_changed_on 설정 규칙의 통합 검증.
 * DB가 공유되고 추천은 전체 기록을 읽으므로, 결과는 자기 선수 것만 골라 본다. 날짜는 2147~2149년.
 * 1팀 선수(기록이 라인 평균 이상)가 3전 전승이면 항상 위 추천, 2팀 선수가 3전 전패면 항상 아래 추천이다.
 */
@IntegrationTest
@AutoConfigureMockMvc
class TierRecommendationApiTest {

    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Asia/Seoul"));
    private static final String PROFILES = "/api/admin/player-profiles";

    @Autowired
    MockMvc mockMvc;
    @Autowired
    PlayerRepository players;
    @Autowired
    InhouseSessionRepository sessions;
    @Autowired
    DuelRepository duels;
    @Autowired
    GameSetRepository gameSets;
    @Autowired
    PlayerLaneTierRepository laneTiers;
    @Autowired
    JdbcTemplate jdbc;

    PublicReadFixtures fixtures;

    @BeforeEach
    void setUp() {
        fixtures = new PublicReadFixtures(players, sessions, duels, gameSets);
    }

    /** ids[0..4] = TEAM1, [5..9] = TEAM2. 한 대결에서 3세트를 모두 TEAM1이 이긴다. */
    private List<Long> threeSets(int year, int dayNo) {
        List<Long> ids = fixtures.tenPlayers();
        long duel = fixtures.duel(fixtures.session(day(year, dayNo)), 1, FearlessMode.RESET);
        for (int no = 1; no <= 3; no++) {
            fixtures.set(duel, no, Side.BLUE, DuelTeam.TEAM1, ids, champs(0, 5), champs(5, 5), champs(10, 10));
        }
        return ids;
    }

    private PlayerLaneTier lane(long playerId, int tier, TierSub sub, LocalDate changedOn) {
        PlayerLaneTier lane = PlayerLaneTier.create(playerId, Position.TOP, new LaneTier(tier, sub));
        lane.markTierChangedOn(changedOn);
        return laneTiers.saveAndFlush(lane);
    }

    private String recommendations() throws Exception {
        return mockMvc.perform(get("/api/admin/tier-recommendations")).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString();
    }

    private static List<Object> mine(String body, long playerId) {
        return JsonPath.read(body, "$[?(@.playerId == " + playerId + ")]");
    }

    @Test
    void 비로그인은_401이다() throws Exception {
        mockMvc.perform(get("/api/admin/tier-recommendations")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void 전승은_한_단계_위_전패는_한_단계_아래로_추천한다() throws Exception {
        List<Long> ids = threeSets(2147, 1);
        lane(ids.get(0), 3, TierSub.MID, null);
        lane(ids.get(5), 3, TierSub.LOW, null);

        String body = recommendations();

        assertThat(mine(body, ids.get(0))).hasSize(1);
        String up = "$[?(@.playerId == " + ids.get(0) + ")]";
        assertThat((List<String>) JsonPath.read(body, up + ".direction")).containsExactly("UP");
        assertThat((List<String>) JsonPath.read(body, up + ".position")).containsExactly("TOP");
        assertThat((List<Integer>) JsonPath.read(body, up + ".games")).containsExactly(3);
        assertThat((List<Integer>) JsonPath.read(body, up + ".wins")).containsExactly(3);
        assertThat((List<Integer>) JsonPath.read(body, up + ".from.tier")).containsExactly(3);
        assertThat((List<String>) JsonPath.read(body, up + ".from.sub")).containsExactly("MID");
        assertThat((List<String>) JsonPath.read(body, up + ".to.sub")).containsExactly("HIGH");
        assertThat((List<String>) JsonPath.read(body, up + ".displayName")).hasSize(1);
        assertThat(JsonPath.<List<Number>>read(body, up + ".kda")).hasSize(1);
        assertThat(JsonPath.<List<Number>>read(body, up + ".laneAvgKda")).hasSize(1);
        assertThat(JsonPath.<List<Number>>read(body, up + ".score").get(0).doubleValue()).isGreaterThanOrEqualTo(0.35);

        String down = "$[?(@.playerId == " + ids.get(5) + ")]";
        assertThat((List<String>) JsonPath.read(body, down + ".direction")).containsExactly("DOWN");
        assertThat((List<Integer>) JsonPath.read(body, down + ".to.tier")).containsExactly(4);
        assertThat((List<String>) JsonPath.read(body, down + ".to.sub")).containsExactly("HIGH");
        assertThat(JsonPath.<List<Number>>read(body, down + ".score").get(0).doubleValue()).isLessThanOrEqualTo(-0.35);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void 전체_목록은_점수_절댓값_내림차순이다() throws Exception {
        List<Long> ids = threeSets(2147, 2);
        lane(ids.get(0), 3, TierSub.MID, null);
        lane(ids.get(5), 3, TierSub.MID, null);

        List<Number> raw = JsonPath.read(recommendations(), "$[*].score");
        List<Double> scores = raw.stream().map(Number::doubleValue).toList();

        assertThat(scores).isNotEmpty().isSortedAccordingTo(
                java.util.Comparator.comparingDouble((Double s) -> Math.abs(s)).reversed());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void 기준일이_세션_시작일과_같은_날이면_그_기록은_세지_않는다() throws Exception {
        List<Long> ids = threeSets(2148, 1);
        lane(ids.get(0), 3, TierSub.MID, day(2148, 1));

        assertThat(mine(recommendations(), ids.get(0))).isEmpty();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void 기준일이_세션_전날이면_포함된다() throws Exception {
        List<Long> ids = threeSets(2148, 10);
        lane(ids.get(0), 4, TierSub.HIGH, day(2148, 9));

        assertThat(mine(recommendations(), ids.get(0))).hasSize(1);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void 경계_티어는_추천하지_않는다() throws Exception {
        List<Long> ids = threeSets(2148, 20);
        lane(ids.get(0), 1, TierSub.HIGH, null);
        lane(ids.get(5), 6, TierSub.LOW, null);

        String body = recommendations();

        assertThat(mine(body, ids.get(0))).isEmpty();
        assertThat(mine(body, ids.get(5))).isEmpty();
    }

    // ---------- tier_changed_on 설정 규칙 ----------

    private String profileBody(String realName, String nick, String lanes, String extra) {
        return "{\"realName\":\"" + realName + "\",\"mainPosition\":null,\"lanes\":{" + lanes + "},\"nicknames\":[\""
                + nick + "\"]" + extra + "}";
    }

    private LocalDate changedOn(int playerId, String position) {
        java.sql.Date date = jdbc.queryForObject(
                "select tier_changed_on from player_lane_tier where player_id = ? and position = ?",
                java.sql.Date.class, playerId, position);
        return date == null ? null : date.toLocalDate();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void 티어가_실제로_바뀐_저장과_새_라인만_오늘로_기록하고_recordChange_false는_건드리지_않는다() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String real = "추천" + suffix;
        String nick = "rec" + suffix;
        String top3 = "\"TOP\":{\"tier\":3,\"sub\":\"MID\"}";

        String created = mockMvc.perform(post(PROFILES).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(profileBody(real, nick, top3, ""))).andExpect(status().isCreated()).andReturn().getResponse()
                .getContentAsString();
        int id = JsonPath.read(created, "$.id");
        assertThat(changedOn(id, "TOP")).as("새 선수의 새 라인").isEqualTo(TODAY);

        jdbc.update("update player_lane_tier set tier_changed_on = ? where player_id = ?", java.sql.Date.valueOf(day(2149, 1)),
                id);
        save(id, real, nick, top3, "");
        assertThat(changedOn(id, "TOP")).as("같은 값 재저장").isEqualTo(day(2149, 1));

        save(id, real, nick, "\"TOP\":{\"tier\":4,\"sub\":\"MID\"}", ",\"recordChange\":false");
        assertThat(changedOn(id, "TOP")).as("recordChange=false 값 변경").isEqualTo(day(2149, 1));

        save(id, real, nick, "\"TOP\":{\"tier\":4,\"sub\":\"MID\"},\"MID\":{\"tier\":2,\"sub\":\"LOW\"}",
                ",\"recordChange\":false");
        assertThat(changedOn(id, "MID")).as("recordChange=false 새 라인").isNull();

        save(id, real, nick, "\"TOP\":{\"tier\":3,\"sub\":\"HIGH\"},\"MID\":{\"tier\":2,\"sub\":\"LOW\"}", "");
        assertThat(changedOn(id, "TOP")).as("값 변경").isEqualTo(TODAY);
        assertThat(changedOn(id, "MID")).as("바뀌지 않은 라인").isNull();

        save(id, real, nick, "\"TOP\":{\"tier\":3,\"sub\":\"HIGH\"},\"MID\":{\"tier\":2,\"sub\":\"LOW\"},"
                + "\"ADC\":{\"tier\":5,\"sub\":\"MID\"}", "");
        assertThat(changedOn(id, "ADC")).as("recordChange=true 새 라인").isEqualTo(TODAY);
    }

    private void save(int id, String real, String nick, String lanes, String extra) throws Exception {
        mockMvc.perform(put(PROFILES + "/" + id).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(profileBody(real, nick, lanes, extra))).andExpect(status().isOk());
    }
}
