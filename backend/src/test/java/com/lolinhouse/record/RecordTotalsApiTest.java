package com.lolinhouse.record;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.lolinhouse.IntegrationTest;
import com.lolinhouse.domain.DuelTeam;
import com.lolinhouse.domain.Side;
import com.lolinhouse.player.PlayerRepository;
import com.lolinhouse.session.DuelRepository;
import com.lolinhouse.session.FearlessMode;
import com.lolinhouse.session.GameSet;
import com.lolinhouse.session.GameSetRepository;
import com.lolinhouse.session.InhouseSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static com.lolinhouse.record.PublicReadFixtures.champs;
import static com.lolinhouse.record.PublicReadFixtures.day;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 역할: GET /api/records 응답의 totals(대결 수·세트 수)가 필터 전체 기준이고 커서·limit에 영향받지 않는지 확인한다. 날짜는 2140년대. */
@IntegrationTest
@AutoConfigureMockMvc
class RecordTotalsApiTest {

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

    PublicReadFixtures fixtures;

    @BeforeEach
    void setUp() {
        fixtures = new PublicReadFixtures(players, sessions, duels, gameSets);
    }

    private GameSet set(long duel, int setNo, List<Long> ids) {
        return fixtures.set(duel, setNo, Side.BLUE, DuelTeam.TEAM1, ids, champs(0, 5), champs(5, 5), champs(10, 10));
    }

    private static String year(int year) {
        return "from=" + year + "-01-01&to=" + year + "-12-31";
    }

    @Test
    void totals는_세션_수와_상관없이_대결_수와_세트_수를_센다() throws Exception {
        int year = 2140;
        List<Long> ids = fixtures.tenPlayers();
        long a = fixtures.duel(fixtures.session(day(year, 1)), 1, FearlessMode.RESET);
        for (int no = 1; no <= 3; no++) {
            set(a, no, ids);
        }
        long sessionB = fixtures.session(day(year, 3));
        long b = fixtures.duel(sessionB, 1, FearlessMode.RESET);
        set(b, 1, ids);
        set(b, 2, ids);
        long c = fixtures.duel(sessionB, 2, FearlessMode.RESET);
        set(c, 1, ids);
        fixtures.duel(sessionB, 3, FearlessMode.RESET);

        // 세트 없는 대결은 대결 수에 안 들어간다. limit=1·before로 페이지가 잘려도 합계는 그대로다
        mockMvc.perform(get("/api/records?" + year(year)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.duels").value(3))
                .andExpect(jsonPath("$.totals.sets").value(6));
        mockMvc.perform(get("/api/records?" + year(year) + "&limit=1"))
                .andExpect(jsonPath("$.sessions.length()").value(1))
                .andExpect(jsonPath("$.totals.duels").value(3))
                .andExpect(jsonPath("$.totals.sets").value(6));
        mockMvc.perform(get("/api/records?" + year(year) + "&before=" + day(year, 3)))
                .andExpect(jsonPath("$.sessions.length()").value(1))
                .andExpect(jsonPath("$.totals.duels").value(3))
                .andExpect(jsonPath("$.totals.sets").value(6));
    }

    @Test
    void totals는_선수_필터와_기간_필터를_따르고_삭제된_세트는_뺀다() throws Exception {
        int year = 2141;
        List<Long> ids = fixtures.tenPlayers();
        List<Long> others = fixtures.tenPlayers();
        List<Long> mixed = new ArrayList<>(others);
        mixed.set(0, ids.get(0));
        long a = fixtures.duel(fixtures.session(day(year, 1)), 1, FearlessMode.RESET);
        set(a, 1, mixed);
        set(a, 2, mixed);
        set(a, 3, others);
        long b = fixtures.duel(fixtures.session(day(year, 5)), 1, FearlessMode.RESET);
        GameSet deleted = set(b, 1, mixed);
        set(b, 2, mixed);
        deleted.markDeleted();
        gameSets.saveAndFlush(deleted);

        mockMvc.perform(get("/api/records?" + year(year) + "&playerId=" + ids.get(0)))
                .andExpect(jsonPath("$.totals.duels").value(2))
                .andExpect(jsonPath("$.totals.sets").value(3));
        mockMvc.perform(get("/api/records?from=" + day(year, 1) + "&to=" + day(year, 2)))
                .andExpect(jsonPath("$.totals.duels").value(1))
                .andExpect(jsonPath("$.totals.sets").value(3));
        mockMvc.perform(get("/api/records?" + year(year) + "&champion=NONE"))
                .andExpect(jsonPath("$.sessions.length()").value(0))
                .andExpect(jsonPath("$.totals.duels").value(0))
                .andExpect(jsonPath("$.totals.sets").value(0));
        LocalDate empty = day(year, 100);
        mockMvc.perform(get("/api/records?from=" + empty + "&to=" + empty))
                .andExpect(jsonPath("$.totals.sets").value(0));
    }
}
