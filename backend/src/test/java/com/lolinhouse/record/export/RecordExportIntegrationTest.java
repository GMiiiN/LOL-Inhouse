package com.lolinhouse.record.export;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import com.lolinhouse.IntegrationTest;
import com.lolinhouse.champion.CatalogSnapshot;
import com.lolinhouse.champion.Champion;
import com.lolinhouse.champion.ChampionCatalog;
import com.lolinhouse.domain.DuelTeam;
import com.lolinhouse.domain.Side;
import com.lolinhouse.player.PlayerRepository;
import com.lolinhouse.record.PublicReadFixtures;
import com.lolinhouse.session.DuelRepository;
import com.lolinhouse.session.FearlessMode;
import com.lolinhouse.session.GameSet;
import com.lolinhouse.session.GameSetRepository;
import com.lolinhouse.session.InhouseSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 역할: CSV 내보내기 엔드포인트의 필터, 삭제 세트 제외, 응답 헤더, 로그인 없이 접근을 확인한다. 날짜는 2130년대만 쓴다. */
@IntegrationTest
@AutoConfigureMockMvc
class RecordExportIntegrationTest {

    private static final int YEAR = 2130;
    private static final AtomicInteger SHIFT = new AtomicInteger();

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

    @MockitoBean
    ChampionCatalog catalog;

    LocalDate day1;
    LocalDate day2;
    GameSet s1;
    GameSet s2;
    GameSet deleted;

    @BeforeEach
    void setUp() {
        when(catalog.current()).thenReturn(Optional.of(new CatalogSnapshot("16.20.1",
                List.of(new Champion("C00", "1", "이름영")), (crop, ban) -> null)));
        PublicReadFixtures fixtures = new PublicReadFixtures(players, sessions, duels, gameSets);
        int shift = SHIFT.getAndAdd(1000);
        day1 = PublicReadFixtures.day(YEAR, 10 + shift);
        day2 = PublicReadFixtures.day(YEAR, 20 + shift);
        List<Long> ids = fixtures.tenPlayers();
        long duel1 = fixtures.duel(fixtures.session(day1), 1, FearlessMode.RESET);
        long duel2 = fixtures.duel(fixtures.session(day2), 1, FearlessMode.RESET);
        s1 = fixtures.set(duel1, 1, Side.BLUE, DuelTeam.TEAM1, ids, PublicReadFixtures.champs(0, 5),
                PublicReadFixtures.champs(5, 5), PublicReadFixtures.champs(50, 10));
        s2 = fixtures.set(duel2, 1, Side.RED, DuelTeam.TEAM2, ids, PublicReadFixtures.champs(10, 5),
                PublicReadFixtures.champs(15, 5), PublicReadFixtures.champs(60, 10));
        deleted = fixtures.set(duel2, 2, Side.BLUE, DuelTeam.TEAM1, ids, PublicReadFixtures.champs(20, 5),
                PublicReadFixtures.champs(25, 5), PublicReadFixtures.champs(70, 10));
        deleted.markDeleted();
        gameSets.saveAndFlush(deleted);
    }

    private String csv(String query) throws Exception {
        byte[] bytes = mockMvc.perform(get("/api/records/export?" + query)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static String[] lines(String csv) {
        return csv.substring(1).split("\r\n");
    }

    @Test
    void 로그인_없이_받고_헤더가_맞고_삭제된_세트는_빠진다() throws Exception {
        var result = mockMvc.perform(get("/api/records/export?from=" + day1 + "&to=" + day2))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv; charset=UTF-8"))
                .andReturn();
        String disposition = result.getResponse().getHeader("Content-Disposition");
        assertThat(disposition).startsWith("attachment").containsPattern("filename=\"?lol-inhouse-records-\\d{8}\\.csv\"?");

        String[] lines = lines(new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8));

        assertThat(lines).hasSize(1 + 20);
        assertThat(String.join("\n", lines)).doesNotContain("C20").doesNotContain("C70");
        assertThat(lines[1]).startsWith(day1 + ",1," ).contains(",이름영,C00,");
    }

    @Test
    void 챔피언_필터는_픽_또는_밴이_있는_세트만_남긴다() throws Exception {
        String[] picked = lines(csv("from=" + day1 + "&to=" + day2 + "&champion=C00"));
        assertThat(picked).hasSize(11);
        assertThat(picked[1]).startsWith(day1.toString());

        String[] banned = lines(csv("from=" + day1 + "&to=" + day2 + "&champion=C60"));
        assertThat(banned).hasSize(11);
        assertThat(banned[1]).startsWith(day2.toString());
    }

    @Test
    void 기간과_잘못된_값() throws Exception {
        assertThat(lines(csv("from=" + day2 + "&to=" + day2))).hasSize(11);
        assertThat(lines(csv("from=" + day1.plusDays(1) + "&to=" + day2.minusDays(1)))).hasSize(1);
        mockMvc.perform(get("/api/records/export?from=" + day2 + "&to=" + day1)).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/records/export?championScope=NOPE")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/records/export?from=abc")).andExpect(status().isBadRequest());
    }
}
