package com.lolinhouse.stats;

import java.util.List;

import com.jayway.jsonpath.JsonPath;
import com.lolinhouse.IntegrationTest;
import com.lolinhouse.domain.DuelTeam;
import com.lolinhouse.domain.Side;
import com.lolinhouse.player.PlayerRepository;
import com.lolinhouse.record.PublicReadFixtures;
import com.lolinhouse.session.DuelRepository;
import com.lolinhouse.session.FearlessMode;
import com.lolinhouse.session.GameSetRepository;
import com.lolinhouse.session.InhouseSessionRepository;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static com.lolinhouse.record.PublicJsonAssertions.assertNoForbiddenKeysInJson;
import static com.lolinhouse.record.PublicReadFixtures.champs;
import static com.lolinhouse.record.PublicReadFixtures.day;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 역할: GET /api/duos 통합 검증(공개 접근, 최소 3세트, 각 3쌍, 겹침 없음, 기간, 고정 쿼리 수). 날짜는 2143~2145년. */
@IntegrationTest
@AutoConfigureMockMvc
class DuoApiTest {

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
    EntityManagerFactory entityManagerFactory;

    PublicReadFixtures fixtures;

    @BeforeEach
    void setUp() {
        fixtures = new PublicReadFixtures(players, sessions, duels, gameSets);
    }

    private void sets(long duel, int fromNo, int toNo, DuelTeam winner, List<Long> ids) {
        for (int no = fromNo; no <= toNo; no++) {
            fixtures.set(duel, no, Side.BLUE, winner, ids, champs(0, 5), champs(5, 5), champs(10, 10));
        }
    }

    private String duos(String query) throws Exception {
        String body = mockMvc.perform(get("/api/duos?" + query)).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString();
        assertNoForbiddenKeysInJson(body);
        return body;
    }

    private static String year(int year) {
        return "from=" + year + "-01-01&to=" + year + "-12-31";
    }

    @Test
    void 로그인_없이_열리고_3세트_미만이면_빈_목록이다() throws Exception {
        int year = 2143;
        List<Long> ids = fixtures.tenPlayers();
        long duel = fixtures.duel(fixtures.session(day(year, 1)), 1, FearlessMode.RESET);
        sets(duel, 1, 2, DuelTeam.TEAM1, ids);

        String body = duos(year(year));

        assertThat((Integer) JsonPath.read(body, "$.minSets")).isEqualTo(3);
        assertThat((List<?>) JsonPath.read(body, "$.best")).isEmpty();
        assertThat((List<?>) JsonPath.read(body, "$.worst")).isEmpty();
    }

    @Test
    void 최고는_이긴_팀의_쌍_최악은_진_팀의_쌍이고_각각_3쌍까지이며_겹치지_않는다() throws Exception {
        int year = 2144;
        List<Long> ids = fixtures.tenPlayers();
        long duel = fixtures.duel(fixtures.session(day(year, 1)), 1, FearlessMode.RESET);
        sets(duel, 1, 3, DuelTeam.TEAM1, ids);

        String body = duos(year(year));

        List<Double> bestWinRates = JsonPath.read(body, "$.best[*].winRate");
        assertThat(bestWinRates).hasSize(3).containsOnly(1.0);
        assertThat((List<?>) JsonPath.read(body, "$.worst")).hasSize(3);
        assertThat((List<Double>) JsonPath.read(body, "$.worst[*].winRate")).containsOnly(0.0);
        assertThat((List<Integer>) JsonPath.read(body, "$.best[*].sets")).containsOnly(3);
        assertThat((List<Integer>) JsonPath.read(body, "$.best[*].wins")).containsOnly(3);
        assertThat((List<Integer>) JsonPath.read(body, "$.worst[*].losses")).containsOnly(3);
        List<Long> team1 = ids.subList(0, 5);
        List<Long> team2 = ids.subList(5, 10);
        for (int i = 0; i < 3; i++) {
            long first = ((Number) JsonPath.read(body, "$.best[" + i + "].players[0].playerId")).longValue();
            long second = ((Number) JsonPath.read(body, "$.best[" + i + "].players[1].playerId")).longValue();
            assertThat(first).isLessThan(second);
            assertThat(team1).contains(first, second);
            long wFirst = ((Number) JsonPath.read(body, "$.worst[" + i + "].players[0].playerId")).longValue();
            assertThat(team2).contains(wFirst);
        }
        assertThat((Boolean) JsonPath.read(body, "$.best[0].players[0].registered")).isTrue();
        assertThat((String) JsonPath.read(body, "$.best[0].players[0].displayName")).isNotBlank();
    }

    @Test
    void 기간_밖의_세트는_세지_않는다() throws Exception {
        int year = 2145;
        List<Long> ids = fixtures.tenPlayers();
        long inside = fixtures.duel(fixtures.session(day(year, 1)), 1, FearlessMode.RESET);
        sets(inside, 1, 2, DuelTeam.TEAM1, ids);
        long outside = fixtures.duel(fixtures.session(day(year, 100)), 1, FearlessMode.RESET);
        sets(outside, 1, 1, DuelTeam.TEAM1, ids);

        String all = duos(year(year));
        String windowed = duos("from=" + day(year, 0) + "&to=" + day(year, 10));

        assertThat((List<?>) JsonPath.read(all, "$.best")).isNotEmpty();
        assertThat((List<?>) JsonPath.read(windowed, "$.best")).isEmpty();
        mockMvc.perform(get("/api/duos?from=2145-02-01&to=2145-01-01")).andExpect(status().isBadRequest());
    }

    @Test
    void 쿼리_수는_세트_수와_상관없이_같다() throws Exception {
        List<Long> ids = fixtures.tenPlayers();
        long duel = fixtures.duel(fixtures.session(day(2146, 1)), 1, FearlessMode.RESET);
        sets(duel, 1, 3, DuelTeam.TEAM1, ids);
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);

        long small = count("/api/duos?" + year(2146), statistics);
        sets(duel, 4, 30, DuelTeam.TEAM2, ids);
        long large = count("/api/duos?" + year(2146), statistics);

        assertThat(large).isEqualTo(small);
    }

    private long count(String url, Statistics statistics) throws Exception {
        statistics.clear();
        mockMvc.perform(get(url)).andExpect(status().isOk());
        return statistics.getPrepareStatementCount();
    }
}
