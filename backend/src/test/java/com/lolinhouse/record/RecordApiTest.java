package com.lolinhouse.record;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.lolinhouse.IntegrationTest;
import com.lolinhouse.domain.DuelTeam;
import com.lolinhouse.domain.Side;
import com.lolinhouse.ingest.image.ImageStore;
import com.lolinhouse.player.PlayerRepository;
import com.lolinhouse.session.DuelRepository;
import com.lolinhouse.session.FearlessMode;
import com.lolinhouse.session.GameSet;
import com.lolinhouse.session.GameSet.DisplayedTotals;
import com.lolinhouse.session.GameSetRepository;
import com.lolinhouse.session.InhouseSessionRepository;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static com.lolinhouse.record.PublicReadFixtures.champ;
import static com.lolinhouse.record.PublicReadFixtures.champs;
import static com.lolinhouse.record.PublicReadFixtures.day;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 역할: 공개 기록 API(/api/records, /api/duels/{id}, /api/sets/{id})와 담당자 원본 이미지 API의 통합 검증.
 * 테스트 DB는 공유되므로 테스트마다 2110~2119년 중 하나를 골라 from/to로 그 해만 읽는다.
 */
@IntegrationTest
@AutoConfigureMockMvc
class RecordApiTest {

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
    ImageStore imageStore;
    @Autowired
    EntityManagerFactory entityManagerFactory;

    PublicReadFixtures fixtures;
    List<Long> ids;

    @BeforeEach
    void setUp() {
        fixtures = new PublicReadFixtures(players, sessions, duels, gameSets);
        ids = fixtures.tenPlayers();
    }

    private GameSet quickSet(long duelId, int setNo, DuelTeam winner) {
        return quickSet(duelId, setNo, winner, ids);
    }

    private GameSet quickSet(long duelId, int setNo, DuelTeam winner, List<Long> playerIds) {
        return quickSet(duelId, setNo, winner, playerIds, setNo * 20);
    }

    /** 픽은 C{base}~C{base+9}, 밴은 C{base+10}~C{base+19}. */
    private GameSet quickSet(long duelId, int setNo, DuelTeam winner, List<Long> playerIds, int base) {
        return fixtures.set(duelId, setNo, Side.BLUE, winner, playerIds, champs(base, 5), champs(base + 5, 5),
                champs(base + 10, 10));
    }

    private static String yearQuery(int year) {
        return "from=" + year + "-01-01&to=" + year + "-12-31";
    }

    private String body(String url) throws Exception {
        MvcResult result = mockMvc.perform(get(url)).andExpect(status().isOk()).andReturn();
        String json = result.getResponse().getContentAsString();
        PublicJsonAssertions.assertNoForbiddenKeysInJson(json);
        return json;
    }

    @Test
    void 필터가_걸려도_대결_점수는_전체_세트_값이고_PLAYER_PICK과_ANY가_구분된다() throws Exception {
        int year = 2110;
        long duel = fixtures.duel(fixtures.session(day(year, 5)), 1, FearlessMode.RESET);
        long target = ids.get(0);
        // 세트1: C00 없음, 세트2: C00은 밴만(본인은 뛰었음), 세트3: 다른 선수(ids[5])가 C00 픽, 세트4: 본인이 C00 픽
        quickSet(duel, 1, DuelTeam.TEAM1);
        fixtures.set(duel, 2, Side.BLUE, DuelTeam.TEAM2, ids, champs(40, 5), champs(45, 5), withFirst(champ(0), champs(50, 9)));
        fixtures.set(duel, 3, Side.BLUE, DuelTeam.TEAM1, ids, champs(60, 5), withFirst(champ(0), champs(65, 4)),
                champs(70, 10));
        fixtures.set(duel, 4, Side.BLUE, DuelTeam.TEAM1, ids, withFirst(champ(0), champs(80, 4)), champs(85, 5),
                champs(90, 10));

        String any = body("/api/records?" + yearQuery(year) + "&playerId=" + target + "&champion=C00");
        String pick = body("/api/records?" + yearQuery(year) + "&playerId=" + target
                + "&champion=C00&championScope=PLAYER_PICK");

        // ANY: 세트2(밴), 세트3(다른 선수 픽), 세트4(본인 픽) = 3개 / PLAYER_PICK: 세트4만
        assertThat(setNos(any)).containsExactly(2, 3, 4);
        assertThat(setNos(pick)).containsExactly(4);
        // 대결 점수는 필터와 무관: TEAM1 3승(1,3,4) TEAM2 1승(2), 전체 4세트
        mockMvc.perform(get("/api/records?" + yearQuery(year) + "&playerId=" + target
                + "&champion=C00&championScope=PLAYER_PICK"))
                .andExpect(jsonPath("$.sessions[0].duels[0].score.TEAM1").value(3))
                .andExpect(jsonPath("$.sessions[0].duels[0].score.TEAM2").value(1))
                .andExpect(jsonPath("$.sessions[0].duels[0].totalSets").value(4))
                .andExpect(jsonPath("$.nextBefore").doesNotExist());
    }

    private static List<String> withFirst(String first, List<String> rest) {
        List<String> all = new ArrayList<>();
        all.add(first);
        all.addAll(rest);
        return all;
    }

    private List<Integer> setNos(String json) throws Exception {
        var root = new tools.jackson.databind.json.JsonMapper().readTree(json);
        List<Integer> nos = new ArrayList<>();
        for (var session : root.get("sessions")) {
            for (var duel : session.get("duels")) {
                for (var set : duel.get("sets")) {
                    nos.add(set.get("setNo").asInt());
                }
            }
        }
        return nos;
    }

    @Test
    void 묶음_커서는_시작일_내림차순이고_마지막_페이지의_nextBefore는_null이다() throws Exception {
        int year = 2111;
        for (int n = 1; n <= 3; n++) {
            long duel = fixtures.duel(fixtures.session(day(year, n)), 1, FearlessMode.RESET);
            quickSet(duel, 1, DuelTeam.TEAM1);
        }

        mockMvc.perform(get("/api/records?" + yearQuery(year) + "&limit=2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessions.length()").value(2))
                .andExpect(jsonPath("$.sessions[0].startDate").value(day(year, 3).toString()))
                .andExpect(jsonPath("$.sessions[1].startDate").value(day(year, 2).toString()))
                .andExpect(jsonPath("$.nextBefore").value(day(year, 2).toString()));
        mockMvc.perform(get("/api/records?" + yearQuery(year) + "&limit=2&before=" + day(year, 2)))
                .andExpect(jsonPath("$.sessions.length()").value(1))
                .andExpect(jsonPath("$.sessions[0].startDate").value(day(year, 1).toString()))
                .andExpect(jsonPath("$.nextBefore").doesNotExist());
        mockMvc.perform(get("/api/records?" + yearQuery(year) + "&limit=3"))
                .andExpect(jsonPath("$.sessions.length()").value(3))
                .andExpect(jsonPath("$.nextBefore").doesNotExist());
    }

    @Test
    void 기간은_시작일_기준이라_결과창_날짜와_상관없고_조건에_맞는_세트가_없는_묶음은_빠진다() throws Exception {
        int year = 2112;
        // 픽스처 세트의 결과창 날짜는 2090년대라 시작일 기준이 아니면 이 기간에 잡히지 않는다
        LocalDate start = day(year, 10);
        long duel = fixtures.duel(fixtures.session(start), 1, FearlessMode.RESET);
        quickSet(duel, 1, DuelTeam.TEAM1);

        mockMvc.perform(get("/api/records?from=" + start + "&to=" + start))
                .andExpect(jsonPath("$.sessions.length()").value(1))
                .andExpect(jsonPath("$.sessions[0].duels[0].sets[0].sessionDate").value(start.toString()));
        mockMvc.perform(get("/api/records?from=" + start.plusDays(1) + "&to=" + start.plusDays(5)))
                .andExpect(jsonPath("$.sessions.length()").value(0));
        mockMvc.perform(get("/api/records?" + yearQuery(year) + "&champion=NONE"))
                .andExpect(jsonPath("$.sessions.length()").value(0));
    }

    @Test
    void 삭제된_세트는_기록_점수_세트_상세_제한에서_모두_빠진다() throws Exception {
        int year = 2113;
        long duel = fixtures.duel(fixtures.session(day(year, 1)), 1, FearlessMode.RESET);
        GameSet first = quickSet(duel, 1, DuelTeam.TEAM1);
        GameSet second = quickSet(duel, 2, DuelTeam.TEAM1);
        GameSet third = quickSet(duel, 3, DuelTeam.TEAM2);
        second.markDeleted();
        gameSets.saveAndFlush(second);

        mockMvc.perform(get("/api/records?" + yearQuery(year)))
                .andExpect(jsonPath("$.sessions[0].duels[0].sets.length()").value(2))
                .andExpect(jsonPath("$.sessions[0].duels[0].totalSets").value(2))
                .andExpect(jsonPath("$.sessions[0].duels[0].score.TEAM1").value(1))
                .andExpect(jsonPath("$.sessions[0].duels[0].score.TEAM2").value(1))
                // 세트3의 제한은 삭제된 세트2의 픽을 빼고 세트1의 픽 10개뿐
                .andExpect(jsonPath("$.sessions[0].duels[0].sets[1].fearlessRestricted.length()").value(10));
        mockMvc.perform(get("/api/sets/" + second.id()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SET_NOT_FOUND"));
        mockMvc.perform(get("/api/sets/" + third.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.setNo").value(3))
                .andExpect(jsonPath("$.fearlessRestricted.length()").value(10));
        mockMvc.perform(get("/api/sets/" + first.id())).andExpect(status().isOk());
        mockMvc.perform(get("/api/sets/" + Long.MAX_VALUE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SET_NOT_FOUND"));
        mockMvc.perform(get("/api/duels/" + duel))
                .andExpect(jsonPath("$.totalSets").value(2))
                .andExpect(jsonPath("$.sets.length()").value(2));
    }

    @Test
    void 대결_상세는_이어온_제한과_이번_픽을_나누고_맞라인_교환은_팀을_둘로_보여_준다() throws Exception {
        int year = 2114;
        long session = fixtures.session(day(year, 1));
        long duel1 = fixtures.duel(session, 1, FearlessMode.RESET);
        long duel2 = fixtures.duel(session, 2, FearlessMode.CONTINUE);
        quickSet(duel1, 1, DuelTeam.TEAM1, ids, 100);
        quickSet(duel2, 1, DuelTeam.TEAM1);
        List<Long> swapped = new ArrayList<>(ids.subList(5, 10));
        swapped.addAll(ids.subList(0, 5));
        quickSet(duel2, 2, DuelTeam.TEAM1, swapped);

        String json = body("/api/duels/" + duel2);
        var root = new tools.jackson.databind.json.JsonMapper().readTree(json);

        assertThat(root.get("seq").asInt()).isEqualTo(2);
        assertThat(root.get("fearlessMode").asText()).isEqualTo("CONTINUE");
        assertThat(root.get("sessionDate").asText()).isEqualTo(day(year, 1).toString());
        assertThat(root.get("score").get("TEAM1").asInt()).isEqualTo(2);
        assertThat(root.get("fearless").get("inherited").size()).isEqualTo(10);
        assertThat(root.get("fearless").get("picked").size()).isEqualTo(20);
        assertThat(root.get("sets").get(0).get("setNo").asInt()).isEqualTo(1);
        assertThat(root.get("sets").get(1).get("fearlessRestricted").size()).isEqualTo(20);
        assertThat(root.get("personal").size()).isEqualTo(10);
        var exchanged = root.get("personal").get(0);
        // 첫 선수(ids[0])는 세트1에 TEAM1(승), 세트2에 TEAM2(패)로 뛰어 1승 1패 = DRAW
        assertThat(exchanged.get("playerId").asLong()).isEqualTo(ids.get(0));
        assertThat(exchanged.get("teams").size()).isEqualTo(2);
        assertThat(exchanged.get("wins").asInt()).isEqualTo(1);
        assertThat(exchanged.get("losses").asInt()).isEqualTo(1);
        assertThat(exchanged.get("result").asText()).isEqualTo("DRAW");

        mockMvc.perform(get("/api/duels/" + duel1))
                .andExpect(jsonPath("$.fearless.inherited.length()").value(0))
                .andExpect(jsonPath("$.fearless.picked.length()").value(10));
        mockMvc.perform(get("/api/duels/" + Long.MAX_VALUE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DUEL_NOT_FOUND"));
    }

    @Test
    void 쿼리_수는_세트_수와_상관없이_같다() throws Exception {
        int year = 2115;
        long duel = fixtures.duel(fixtures.session(day(year, 1)), 1, FearlessMode.RESET);
        for (int no = 1; no <= 3; no++) {
            quickSet(duel, no, DuelTeam.TEAM1);
        }
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);

        long recordsThree = countStatements("/api/records?" + yearQuery(year), statistics);
        long duelThree = countStatements("/api/duels/" + duel, statistics);
        for (int no = 4; no <= 30; no++) {
            quickSet(duel, no, DuelTeam.TEAM2);
        }
        long recordsThirty = countStatements("/api/records?" + yearQuery(year), statistics);
        long duelThirty = countStatements("/api/duels/" + duel, statistics);

        assertThat(recordsThirty).isEqualTo(recordsThree);
        assertThat(duelThirty).isEqualTo(duelThree);
        mockMvc.perform(get("/api/records?" + yearQuery(year)))
                .andExpect(jsonPath("$.sessions[0].duels[0].sets.length()").value(30));
    }

    private long countStatements(String url, Statistics statistics) throws Exception {
        statistics.clear();
        mockMvc.perform(get(url)).andExpect(status().isOk());
        return statistics.getPrepareStatementCount();
    }

    @Test
    void 잘못된_쿼리는_400_INVALID_QUERY다() throws Exception {
        for (String query : new String[] {"from=2026-02-01&to=2026-01-01", "limit=0", "limit=21", "limit=abc",
                "from=2026-13-40", "from=hello", "before=oops", "playerId=abc", "championScope=NOPE"}) {
            mockMvc.perform(get("/api/records?" + query))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_QUERY"));
        }
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void 원본_이미지는_담당자에게_바이트와_Content_Type으로_나가고_없으면_404다() throws Exception {
        long duel = fixtures.duel(fixtures.session(day(2116, 1)), 1, FearlessMode.RESET);
        byte[] bytes = new byte[] {(byte) 0x89, 'P', 'N', 'G', 1, 2, 3};
        String hash = java.util.UUID.randomUUID().toString().replace("-", "")
                + java.util.UUID.randomUUID().toString().replace("-", "");
        String key = imageStore.put(hash, "image/png", bytes);
        GameSet withImage = gameSets.saveAndFlush(GameSet.create(duel, 1, Side.BLUE, DuelTeam.TEAM1, DuelTeam.TEAM1,
                day(2116, 2), 777, new DisplayedTotals(1, 2, 3, 4), new DisplayedTotals(5, 6, 7, 8), hash, key));
        GameSet manual = quickSet(duel, 2, DuelTeam.TEAM1);

        mockMvc.perform(get("/api/admin/sets/" + withImage.id() + "/image"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(content().bytes(bytes));
        mockMvc.perform(get("/api/admin/sets/" + manual.id() + "/image"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SET_IMAGE_NOT_FOUND"));
        mockMvc.perform(get("/api/admin/sets/" + Long.MAX_VALUE + "/image"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SET_IMAGE_NOT_FOUND"));

        withImage.markDeleted();
        gameSets.saveAndFlush(withImage);
        mockMvc.perform(get("/api/admin/sets/" + withImage.id() + "/image")).andExpect(status().isOk())
                .andExpect(content().bytes(bytes));
    }

    @Test
    void 원본_이미지는_비로그인이면_401이고_공개_세트_응답에_이미지_정보가_없다() throws Exception {
        long duel = fixtures.duel(fixtures.session(day(2117, 1)), 1, FearlessMode.RESET);
        GameSet set = quickSet(duel, 1, DuelTeam.TEAM1);

        mockMvc.perform(get("/api/admin/sets/" + set.id() + "/image")).andExpect(status().isUnauthorized());
        body("/api/sets/" + set.id());
    }
}
