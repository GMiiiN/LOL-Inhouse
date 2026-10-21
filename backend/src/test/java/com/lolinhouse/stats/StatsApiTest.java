package com.lolinhouse.stats;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.lolinhouse.IntegrationTest;
import com.lolinhouse.domain.DuelTeam;
import com.lolinhouse.domain.Position;
import com.lolinhouse.domain.Side;
import com.lolinhouse.domain.stats.DuelResult;
import com.lolinhouse.domain.tier.LaneTier;
import com.lolinhouse.domain.tier.LastChange;
import com.lolinhouse.domain.tier.TierSub;
import com.lolinhouse.domain.stats.RankingKey;
import com.lolinhouse.player.PlayerRepository;
import com.lolinhouse.record.PublicReadFixtures;
import com.lolinhouse.session.DuelRepository;
import com.lolinhouse.session.FearlessMode;
import com.lolinhouse.session.GameSet;
import com.lolinhouse.session.GameSetRepository;
import com.lolinhouse.session.InhouseSessionRepository;
import com.lolinhouse.tier.PlayerLaneTier;
import com.lolinhouse.tier.PlayerLaneTierRepository;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static com.lolinhouse.record.PublicJsonAssertions.assertNoForbiddenKeysInJson;
import static com.lolinhouse.record.PublicReadFixtures.champs;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 역할: 공개 통계 API(선수 목록·프로필, 랭킹, 챔피언 통계, 홈)의 통합 검증. 날짜는 2120~2129년 구간만 쓰고,
 * 테스트마다 자기 6일짜리 창(base ~ base+5)을 받아 from/to로 그 창만 읽는다(DB를 다른 테스트와 공유하므로).
 * 홈만 기간 필터가 없어서 가장 늦은 2129년 날짜를 쓴다.
 */
@IntegrationTest
@AutoConfigureMockMvc
class StatsApiTest {

    private static final AtomicInteger SLOT = new AtomicInteger();
    private static final List<String> BANS = champs(90, 10);

    @Autowired
    MockMvc mockMvc;
    @Autowired
    PlayerRecordService playerService;
    @Autowired
    RankingService rankingService;
    @Autowired
    ChampionStatsService championStatsService;
    @Autowired
    HomeService homeService;
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
    EntityManagerFactory entityManagerFactory;

    PublicReadFixtures fixtures;
    LocalDate base;

    @BeforeEach
    void setUp() {
        fixtures = new PublicReadFixtures(players, sessions, duels, gameSets);
        newWindow();
    }

    /** 다른 테스트·다른 창과 겹치지 않는 50일 칸에서 6일짜리 창을 새로 받는다(바깥 날짜 실험용 여유 포함). */
    void newWindow() {
        int slot = SLOT.getAndAdd(50);
        base = PublicReadFixtures.day(2120 + slot / 300, slot % 300);
    }

    LocalDate to() {
        return base.plusDays(5);
    }

    long session(int dayOffset) {
        return fixtures.session(base.plusDays(dayOffset));
    }

    GameSet set(long duel, int setNo, DuelTeam winner, List<Long> ids, int team1ChampStart) {
        return fixtures.set(duel, setNo, Side.BLUE, winner, ids, champs(team1ChampStart, 5),
                champs(team1ChampStart + 5, 5), BANS);
    }

    List<Long> idsWith(Long first) {
        List<Long> ids = new ArrayList<>(fixtures.tenPlayers());
        if (first != null) {
            ids.set(0, first);
        }
        return ids;
    }

    String json(String url) throws Exception {
        MvcResult result = mockMvc.perform(get(url)).andExpect(status().isOk()).andReturn();
        String body = result.getResponse().getContentAsString();
        assertNoForbiddenKeysInJson(body);
        return body;
    }

    // ---------- 선수 목록 ----------

    @Test
    void 선수_목록은_기록_없는_등록_선수와_기간_내_미등록_참여자를_모두_담는다() {
        long unregistered = fixtures.unregisteredPlayer("외부인");
        long noRecord = fixtures.registeredPlayer("무기록");
        List<Long> ids = new ArrayList<>(fixtures.tenPlayers());
        ids.set(9, unregistered);
        long duel = fixtures.duel(session(0), 1, FearlessMode.RESET);
        set(duel, 1, DuelTeam.TEAM1, ids, 0);

        PlayerListResponse response = playerService.list(base, to());

        assertThat(response.totalSets()).isEqualTo(1);
        PlayerListResponse.Item outsider = item(response, unregistered);
        assertThat(outsider.registered()).isFalse();
        assertThat(outsider.summary().sets()).isEqualTo(1);
        assertThat(outsider.summary().wins()).isZero();
        assertThat(outsider.positions()).containsOnlyKeys(Position.SUPPORT);
        PlayerListResponse.Item idle = item(response, noRecord);
        assertThat(idle.registered()).isTrue();
        assertThat(idle.summary().sets()).isZero();
        assertThat(idle.summary().kda().ratio()).isNull();
        assertThat(idle.topChampions()).isEmpty();
        PlayerListResponse.Item first = item(response, ids.get(0));
        assertThat(first.summary().sets()).isEqualTo(1);
        assertThat(first.summary().cs()).isEqualTo(200);
        assertThat(first.summary().gold()).isEqualTo(10000);
        assertThat(first.topChampions()).hasSize(1);
        assertThat(first.championCount()).isEqualTo(1);
    }

    @Test
    void 세트가_전혀_없는_기간에도_등록_선수는_목록에_있고_미등록_선수는_없다() throws Exception {
        long noRecord = fixtures.registeredPlayer("무기록");
        long unregistered = fixtures.unregisteredPlayer("외부인");

        PlayerListResponse response = playerService.list(base, to());

        assertThat(response.totalSets()).isZero();
        assertThat(item(response, noRecord).summary().sets()).isZero();
        assertThat(response.players()).noneMatch(p -> p.playerId() == unregistered);
        assertThat(response.players()).allMatch(p -> p.summary().sets() == 0 && p.registered());
        json("/api/players?from=" + base + "&to=" + to());
    }

    @Test
    void 선수_목록은_경기_수_내림차순으로_정렬한다() {
        long duel = fixtures.duel(session(0), 1, FearlessMode.RESET);
        List<Long> ids = fixtures.tenPlayers();
        List<Long> second = idsWith(ids.get(0));
        set(duel, 1, DuelTeam.TEAM1, ids, 0);
        set(duel, 2, DuelTeam.TEAM1, second, 0);

        PlayerListResponse response = playerService.list(base, to());

        List<Integer> counts = response.players().stream().map(p -> p.summary().sets()).toList();
        assertThat(counts).isSortedAccordingTo(java.util.Comparator.reverseOrder());
        assertThat(response.players().get(0).playerId()).isEqualTo(ids.get(0));
    }

    private static PlayerListResponse.Item item(PlayerListResponse response, long id) {
        return response.players().stream().filter(p -> p.playerId() == id).findFirst().orElseThrow();
    }

    // ---------- 선수 프로필 ----------

    @Test
    void 없는_선수_id는_404_PLAYER_NOT_FOUND다() throws Exception {
        mockMvc.perform(get("/api/players/999999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLAYER_NOT_FOUND"));
    }

    @Test
    void 기록이_없는_선수는_빈_통계를_돌려주고_라인_티어_키는_다섯_개다() throws Exception {
        long id = fixtures.registeredPlayer("무기록");
        long duel = fixtures.duel(session(0), 1, FearlessMode.RESET);
        set(duel, 1, DuelTeam.TEAM1, fixtures.tenPlayers(), 0);

        PlayerRecordResponse response = playerService.profile(id, base, to());

        assertThat(response.registered()).isTrue();
        assertThat(response.totalSets()).isEqualTo(1);
        assertThat(response.participationRate()).isZero();
        assertThat(response.summary().sets()).isZero();
        assertThat(response.summary().kda().ratio()).isNull();
        assertThat(response.byPosition()).isEmpty();
        assertThat(response.byChampion()).isEmpty();
        assertThat(response.duels().items()).isEmpty();
        assertThat(response.recentSets()).isEmpty();
        assertThat(response.laneTiers()).containsOnlyKeys(Position.values());
        assertThat(response.laneTiers().values()).containsOnlyNulls();
        json("/api/players/" + id + "?from=" + base + "&to=" + to());
    }

    @Test
    void 프로필은_요약_라인_챔피언_대결_최근경기를_계산하고_삭제_세트와_기간_밖을_뺀다() throws Exception {
        List<Long> others = fixtures.tenPlayers();
        long p0 = others.get(0);
        List<Long> withoutP0 = idsWith(null);
        long sessionA = session(0);
        long duelA1 = fixtures.duel(sessionA, 1, FearlessMode.RESET);
        long duelA2 = fixtures.duel(sessionA, 2, FearlessMode.CONTINUE);
        long sessionB = session(2);
        long duelB1 = fixtures.duel(sessionB, 1, FearlessMode.RESET);
        GameSet a1s1 = set(duelA1, 1, DuelTeam.TEAM1, others, 0);
        GameSet a1s2 = set(duelA1, 2, DuelTeam.TEAM2, others, 10);
        GameSet a2s1 = set(duelA2, 1, DuelTeam.TEAM1, others, 0);
        GameSet b1s1 = set(duelB1, 1, DuelTeam.TEAM1, others, 0);
        GameSet b1s2 = set(duelB1, 2, DuelTeam.TEAM1, withoutP0, 0);
        GameSet deleted = set(duelB1, 3, DuelTeam.TEAM1, others, 40);
        deleted.markDeleted();
        gameSets.saveAndFlush(deleted);
        long outsideSession = fixtures.session(base.plusDays(40));
        set(fixtures.duel(outsideSession, 1, FearlessMode.RESET), 1, DuelTeam.TEAM1, others, 0);
        PlayerLaneTier lane = PlayerLaneTier.create(p0, Position.TOP, new LaneTier(2, TierSub.HIGH));
        lane.changeTo(new LaneTier(1, TierSub.MID));
        laneTiers.save(lane);

        PlayerRecordResponse profile = playerService.profile(p0, base, to());

        assertThat(profile.totalSets()).isEqualTo(5);
        assertThat(profile.participationRate()).isEqualTo(4 / 5.0);
        assertThat(profile.summary().sets()).isEqualTo(4);
        assertThat(profile.summary().wins()).isEqualTo(3);
        assertThat(profile.summary().losses()).isEqualTo(1);
        assertThat(profile.summary().kda().kills()).isEqualTo(16);
        assertThat(profile.summary().kda().deaths()).isEqualTo(8);
        assertThat(profile.summary().kda().assists()).isEqualTo(24);
        assertThat(profile.summary().kda().ratio()).isEqualTo(5.0);
        assertThat(profile.summary().cs()).isEqualTo(800);
        assertThat(profile.byPosition()).hasSize(1);
        assertThat(profile.byPosition().get(0).position()).isEqualTo(Position.TOP);
        assertThat(profile.byPosition().get(0).topChampions()).extracting("championKey", "sets")
                .containsExactly(tuple("C00", 3), tuple("C10", 1));
        assertThat(profile.byChampion()).extracting(PlayerRecordResponse.ChampionSummary::championKey)
                .containsExactly("C00", "C10");
        assertThat(profile.byChampion().get(0).summary().sets()).isEqualTo(3);
        assertThat(profile.byChampion().get(0).summary().wins()).isEqualTo(3);

        assertThat(profile.duels().wins()).isEqualTo(2);
        assertThat(profile.duels().draws()).isEqualTo(1);
        assertThat(profile.duels().losses()).isZero();
        assertThat(profile.duels().items()).extracting(PlayerRecordResponse.Item::duelId)
                .containsExactly(duelB1, duelA2, duelA1);
        assertThat(profile.duels().items().get(2).result()).isEqualTo(DuelResult.DRAW);
        assertThat(profile.duels().items().get(0).sessionDate()).isEqualTo(base.plusDays(2));

        assertThat(profile.recentSets()).extracting(s -> s.setId())
                .containsExactly(b1s1.id(), a2s1.id(), a1s2.id(), a1s1.id());
        assertThat(profile.laneTiers().get(Position.TOP).tier()).isEqualTo(1);
        assertThat(profile.laneTiers().get(Position.TOP).sub()).isEqualTo(TierSub.MID);
        assertThat(profile.laneTiers().get(Position.TOP).trend().direction()).isEqualTo(LastChange.Direction.UP);
        assertThat(profile.laneTiers().get(Position.TOP).trend().fromTier()).isEqualTo(2);
        assertThat(profile.laneTiers().get(Position.MID)).isNull();
        assertThat(b1s2.id()).isNotIn(profile.recentSets().stream().map(s -> s.setId()).toList());

        String body = json("/api/players/" + p0 + "?from=" + base + "&to=" + to());
        assertThat(body).contains("\"recentSets\"").doesNotContain("\"realName\"");
    }

    @Test
    void 최근_5경기는_다섯_세트까지만_담는다() {
        List<Long> ids = fixtures.tenPlayers();
        long duel = fixtures.duel(session(0), 1, FearlessMode.RESET);
        for (int no = 1; no <= 7; no++) {
            set(duel, no, DuelTeam.TEAM1, ids, 0);
        }

        PlayerRecordResponse profile = playerService.profile(ids.get(0), base, to());

        assertThat(profile.summary().sets()).isEqualTo(7);
        assertThat(profile.recentSets()).hasSize(5);
        assertThat(profile.recentSets().get(0).setNo()).isEqualTo(7);
    }

    // ---------- 랭킹 ----------

    private record RankingScenario(List<Long> ids, long late) {
    }

    /** 1팀 승리만 7세트(대결1 5세트 + 대결2 2세트). 0번 선수는 대결2에서 late로 바뀌어 5세트, late는 2세트. */
    private RankingScenario rankingScenario() {
        List<Long> ids = fixtures.tenPlayers();
        long late = fixtures.registeredPlayer("신입");
        long sessionId = session(0);
        long duel1 = fixtures.duel(sessionId, 1, FearlessMode.RESET);
        long duel2 = fixtures.duel(sessionId, 2, FearlessMode.RESET);
        for (int no = 1; no <= 5; no++) {
            set(duel1, no, DuelTeam.TEAM1, ids, 0);
        }
        List<Long> replaced = new ArrayList<>(ids);
        replaced.set(0, late);
        for (int no = 1; no <= 2; no++) {
            set(duel2, no, DuelTeam.TEAM1, replaced, 0);
        }
        return new RankingScenario(ids, late);
    }

    @Test
    void 랭킹은_5세트_미만을_rank_null로_뒤에_두고_동률은_공동_순위다() throws Exception {
        RankingScenario s = rankingScenario();

        RankingResponse response = rankingService.rank(null, RankingKey.WIN_RATE, base, to());

        assertThat(response.position()).isEqualTo("ALL");
        assertThat(response.minSets()).isEqualTo(5);
        assertThat(response.totalSets()).isEqualTo(7);
        assertThat(response.entries()).extracting(RankingResponse.Entry::rank)
                .containsExactly(1, 1, 1, 1, 5, 6, 6, 6, 6, 6, null);
        RankingResponse.Entry unranked = response.entries().get(10);
        assertThat(unranked.playerId()).isEqualTo(s.late());
        assertThat(unranked.summary().sets()).isEqualTo(2);
        assertThat(response.entries().get(4).playerId()).isEqualTo(s.ids().get(0));

        mockMvc.perform(get("/api/rankings?from=" + base + "&to=" + to()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries[10].rank").value(nullValue()))
                .andExpect(jsonPath("$.entries[0].rank").value(1))
                .andExpect(jsonPath("$.sort").value("WIN_RATE"));
        json("/api/rankings?from=" + base + "&to=" + to());
    }

    @Test
    void 포지션_탭은_그_포지션에서_5세트_이상이어야_순위가_있다() throws Exception {
        RankingScenario s = rankingScenario();

        RankingResponse top = rankingService.rank(Position.TOP, RankingKey.WIN_RATE, base, to());

        assertThat(top.position()).isEqualTo("TOP");
        assertThat(top.entries()).extracting(RankingResponse.Entry::playerId)
                .containsExactly(s.ids().get(0), s.ids().get(5), s.late());
        assertThat(top.entries()).extracting(RankingResponse.Entry::rank).containsExactly(1, 2, null);
        assertThat(top.entries().get(0).summary().sets()).isEqualTo(5);
        assertThat(rankingService.rank(Position.JUNGLE, RankingKey.WIN_RATE, base, to()).entries())
                .extracting(RankingResponse.Entry::rank).doesNotContain(0);
        json("/api/rankings?position=TOP&from=" + base + "&to=" + to());
    }

    @Test
    void 경기_수_정렬은_세트가_많은_선수가_앞에_온다() {
        RankingScenario s = rankingScenario();

        RankingResponse response = rankingService.rank(null, RankingKey.SETS, base, to());

        assertThat(response.sort()).isEqualTo(RankingKey.SETS);
        assertThat(response.entries()).extracting(RankingResponse.Entry::rank)
                .containsExactly(1, 1, 1, 1, 5, 5, 5, 5, 5, 10, null);
        assertThat(response.entries().get(9).playerId()).isEqualTo(s.ids().get(0));
    }

    @Test
    void 잘못된_쿼리는_400_INVALID_QUERY다() throws Exception {
        for (String url : new String[] {"/api/rankings?position=MIDDLE", "/api/rankings?sort=BEST",
                "/api/rankings?from=2026-02-01&to=2026-01-01", "/api/players?from=내일", "/api/players/1?to=2026-13-01",
                "/api/champion-stats?from=2026-02-01&to=2026-01-01"}) {
            mockMvc.perform(get(url)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_QUERY"));
        }
    }

    // ---------- 챔피언 통계 ----------

    @Test
    void 챔피언_통계는_픽_밴_승패와_세트_수를_세고_픽과_밴이_겹쳐도_세트는_한_번만_센다() throws Exception {
        List<Long> ids = fixtures.tenPlayers();
        long duel = fixtures.duel(session(0), 1, FearlessMode.RESET);
        fixtures.set(duel, 1, Side.BLUE, DuelTeam.TEAM1, ids, champs(0, 5), champs(5, 5), champs(50, 10));
        List<String> overlapBans = new ArrayList<>(champs(60, 10));
        overlapBans.set(0, "C00");
        fixtures.set(duel, 2, Side.BLUE, DuelTeam.TEAM2, ids, champs(0, 5), champs(5, 5), overlapBans);
        GameSet deleted = fixtures.set(duel, 3, Side.BLUE, DuelTeam.TEAM1, ids, champs(70, 5), champs(75, 5),
                champs(80, 10));
        deleted.markDeleted();
        gameSets.saveAndFlush(deleted);

        ChampionStatsResponse response = championStatsService.get(base, to());

        assertThat(response.totalSets()).isEqualTo(2);
        ChampionStatsResponse.Item c00 = champion(response, "C00");
        assertThat(c00).isEqualTo(new ChampionStatsResponse.Item("C00", 2, 1, 1, 1, 2));
        assertThat(champion(response, "C05")).isEqualTo(new ChampionStatsResponse.Item("C05", 2, 0, 1, 1, 2));
        assertThat(champion(response, "C50")).isEqualTo(new ChampionStatsResponse.Item("C50", 0, 1, 0, 0, 1));
        assertThat(response.champions()).noneMatch(c -> c.championKey().equals("C70"));
        json("/api/champion-stats?from=" + base + "&to=" + to());
    }

    private static ChampionStatsResponse.Item champion(ChampionStatsResponse response, String key) {
        return response.champions().stream().filter(c -> c.championKey().equals(key)).findFirst().orElseThrow();
    }

    // ---------- 홈 ----------

    @Test
    void 홈은_최근_5세트를_최근순으로_담고_라인_키_다섯_개를_항상_준다() throws Exception {
        List<Long> ids = fixtures.tenPlayers();
        long duel = fixtures.duel(fixtures.session(PublicReadFixtures.day(2129, 300)), 1, FearlessMode.RESET);
        for (int no = 1; no <= 6; no++) {
            set(duel, no, DuelTeam.TEAM1, ids, 0);
        }

        HomeResponse home = homeService.get();

        // 홈은 기간 필터가 없고 DB를 다른 테스트와 공유하므로 정확한 세트 목록 대신 개수와 정렬을 본다
        assertThat(home.recentSets()).hasSize(5);
        assertThat(home.recentSets()).isSortedAccordingTo(java.util.Comparator
                .comparing((com.lolinhouse.record.SetView s) -> s.sessionDate())
                .thenComparingInt(com.lolinhouse.record.SetView::duelSeq)
                .thenComparingInt(com.lolinhouse.record.SetView::setNo).reversed());
        assertThat(home.topPicksByPosition()).containsOnlyKeys(Position.values());
        assertThat(home.topPicksByPosition().get(Position.TOP).picks()).isPositive();
        json("/api/home");
        mockMvc.perform(get("/api/home")).andExpect(jsonPath("$.recentSets.length()").value(5))
                .andExpect(jsonPath("$.topPicksByPosition.SUPPORT").exists());
    }

    // ---------- N+1 ----------

    @Test
    void 프로필_쿼리_수는_세트가_3개일_때와_30개일_때_같다() {
        long small = countProfileQueries(3);
        long large = countProfileQueries(30);

        assertThat(large).isEqualTo(small);
    }

    @Test
    void 랭킹_쿼리_수는_세트가_3개일_때와_30개일_때_같다() {
        long small = countRankingQueries(3);
        long large = countRankingQueries(30);

        assertThat(large).isEqualTo(small);
    }

    private long countProfileQueries(int setCount) {
        newWindow();
        List<Long> ids = buildSets(setCount);
        Statistics statistics = statistics();
        statistics.clear();
        PlayerRecordResponse profile = playerService.profile(ids.get(0), base, to());
        long count = statistics.getPrepareStatementCount();
        assertThat(profile.summary().sets()).isEqualTo(setCount);
        assertThat(profile.recentSets()).hasSize(Math.min(5, setCount));
        return count;
    }

    private long countRankingQueries(int setCount) {
        newWindow();
        buildSets(setCount);
        Statistics statistics = statistics();
        statistics.clear();
        RankingResponse response = rankingService.rank(null, RankingKey.WIN_RATE, base, to());
        long count = statistics.getPrepareStatementCount();
        assertThat(response.totalSets()).isEqualTo(setCount);
        return count;
    }

    /** 이 테스트의 창에 세트를 setCount개 만든다(대결마다 5세트, 묶음은 3일에 걸쳐). */
    private List<Long> buildSets(int setCount) {
        List<Long> ids = fixtures.tenPlayers();
        int made = 0;
        int seq = 1;
        long sessionId = session(0);
        int day = 0;
        while (made < setCount) {
            if (seq > 3) {
                seq = 1;
                sessionId = session(++day);
            }
            long duel = fixtures.duel(sessionId, seq++, FearlessMode.RESET);
            for (int no = 1; no <= 5 && made < setCount; no++, made++) {
                set(duel, no, DuelTeam.TEAM1, ids, 0);
            }
        }
        return ids;
    }

    private Statistics statistics() {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        return statistics;
    }
}
