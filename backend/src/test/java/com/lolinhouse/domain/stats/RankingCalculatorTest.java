package com.lolinhouse.domain.stats;

import com.lolinhouse.domain.Position;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.lolinhouse.domain.stats.StatsFixtures.concat;
import static com.lolinhouse.domain.stats.StatsFixtures.games;
import static com.lolinhouse.domain.stats.StatsFixtures.record;
import static org.assertj.core.api.Assertions.assertThat;

class RankingCalculatorTest {

    private static final int MIN_SETS = 5;

    private static List<String> order(List<RankingEntry> ranking) {
        return ranking.stream().map(RankingEntry::playerId).toList();
    }

    private static List<Integer> ranks(List<RankingEntry> ranking) {
        return ranking.stream().map(RankingEntry::rank).toList();
    }

    @Test
    void defaultKeyIsWinRateDescending() {
        List<PlayerSetRecord> records = concat(
                games("p1", Position.MID, 10, 5, 5, 3, 5),   // 50%
                games("p2", Position.TOP, 10, 8, 5, 3, 5),   // 80%
                games("p3", Position.ADC, 10, 6, 5, 3, 5));  // 60%

        var ranking = RankingCalculator.overall(records, MIN_SETS);

        assertThat(order(ranking)).containsExactly("p2", "p3", "p1");
        assertThat(ranks(ranking)).containsExactly(1, 2, 3);
    }

    @Test
    void playersBelowMinimumSampleAreListedLastWithoutRank() {
        List<PlayerSetRecord> records = concat(
                games("p1", Position.MID, 10, 5, 5, 3, 5),    // 50%, 기준 충족
                games("p2", Position.TOP, 4, 4, 9, 1, 9));    // 100%지만 4세트 < 5

        var ranking = RankingCalculator.overall(records, MIN_SETS);

        assertThat(order(ranking)).containsExactly("p1", "p2");
        assertThat(ranking.get(0).isRanked()).isTrue();
        assertThat(ranking.get(0).rank()).isEqualTo(1);
        assertThat(ranking.get(1).isRanked()).isFalse();
        assertThat(ranking.get(1).summary().sets()).isEqualTo(4);
    }

    @Test
    void minimumSampleIsAParameter() {
        List<PlayerSetRecord> records = games("p1", Position.MID, 4, 2, 5, 3, 5);

        assertThat(RankingCalculator.overall(records, 4).get(0).isRanked()).isTrue();
        assertThat(RankingCalculator.overall(records, 5).get(0).isRanked()).isFalse();
    }

    @Test
    void equalWinRateIsBrokenByMoreSets() {
        List<PlayerSetRecord> records = concat(
                games("p1", Position.MID, 10, 5, 5, 3, 5),   // 50%, 10세트
                games("p2", Position.TOP, 20, 10, 5, 3, 5)); // 50%, 20세트

        assertThat(order(RankingCalculator.overall(records, MIN_SETS))).containsExactly("p2", "p1");
    }

    @Test
    void equalWinRateAndSetsIsBrokenByKda() {
        List<PlayerSetRecord> records = concat(
                games("p1", Position.MID, 10, 5, 4, 4, 4),   // (4+4)/4 = 2.0
                games("p2", Position.TOP, 10, 5, 6, 4, 6));  // (6+6)/4 = 3.0

        assertThat(order(RankingCalculator.overall(records, MIN_SETS))).containsExactly("p2", "p1");
    }

    @Test
    void fullTiesShareRankAndNextRankSkips() {
        List<PlayerSetRecord> records = concat(
                games("p1", Position.MID, 10, 8, 5, 3, 5),
                games("p2", Position.TOP, 10, 8, 5, 3, 5),   // p1과 승률·세트·KDA 모두 같음
                games("p3", Position.ADC, 10, 4, 5, 3, 5));

        var ranking = RankingCalculator.overall(records, MIN_SETS);

        assertThat(ranks(ranking)).containsExactly(1, 1, 3);
    }

    @Test
    void kdaKeyOrdersByKdaFirstThenDefaultTieBreaks() {
        List<PlayerSetRecord> records = concat(
                games("p1", Position.MID, 10, 9, 3, 3, 3),   // 승률 90%, KDA 2.0
                games("p2", Position.TOP, 10, 4, 8, 2, 8),   // 승률 40%, KDA 8.0
                games("p3", Position.ADC, 10, 6, 8, 2, 8));  // 승률 60%, KDA 8.0 -> p2와 KDA 동률, 승률로 p3 우선

        var ranking = RankingCalculator.overall(records, MIN_SETS, RankingKey.KDA);

        assertThat(order(ranking)).containsExactly("p3", "p2", "p1");
        assertThat(ranks(ranking)).containsExactly(1, 2, 3);
    }

    @Test
    void setsKeyOrdersByPlayedSetsFirst() {
        List<PlayerSetRecord> records = concat(
                games("p1", Position.MID, 12, 3, 5, 3, 5),   // 25%
                games("p2", Position.TOP, 30, 15, 5, 3, 5),  // 50%
                games("p3", Position.ADC, 8, 8, 5, 3, 5));   // 100%

        var ranking = RankingCalculator.overall(records, MIN_SETS, RankingKey.SETS);

        assertThat(order(ranking)).containsExactly("p2", "p1", "p3");
    }

    // ---------- PERFECT 처리 (사용자 결정 2026-10-16: 정렬값 = (K+A) / max(D, 1)) ----------

    @Test
    void perfectKdaWithHugeKillsAndAssistsRanksAboveNumericKda() {
        List<PlayerSetRecord> records = concat(
                games("p1", Position.MID, 10, 5, 20, 1, 20),   // 누적 400 / 10 = 40.0
                games("p2", Position.SUPPORT, 6, 3, 1, 0, 9)); // 누적 60 / 0 -> PERFECT, 정렬값 60

        var ranking = RankingCalculator.overall(records, MIN_SETS, RankingKey.KDA);

        assertThat(order(ranking)).containsExactly("p2", "p1");
    }

    @Test
    void perfectKdaWithSmallTotalsDoesNotJumpAboveHigherNumericKda() {
        List<PlayerSetRecord> records = concat(
                games("p1", Position.MID, 10, 5, 20, 1, 20),   // 40.0
                games("p2", Position.SUPPORT, 6, 3, 0, 0, 1)); // 누적 6 / 0 -> PERFECT, 정렬값 6

        var ranking = RankingCalculator.overall(records, MIN_SETS, RankingKey.KDA);

        assertThat(order(ranking)).containsExactly("p1", "p2");
        assertThat(ranking.get(1).summary().kda().isPerfect()).isTrue();  // 표시는 여전히 PERFECT
    }

    @Test
    void perfectIsSortedLikeOneDeathWithSameKillsAndAssists() {
        List<PlayerSetRecord> records = concat(
                games("p1", Position.MID, 6, 3, 5, 0, 5),                       // K+A 60, D 0 -> 정렬값 60
                games("p2", Position.TOP, 5, 3, 10, 0, 0),                      // K 50
                List.of(record("p2", "d9", "extra", Position.TOP, "Garen", false, 6, 1, 4))); // 합계 K+A 60, D 1

        var ranking = RankingCalculator.overall(records, MIN_SETS, RankingKey.KDA);

        assertThat(ranks(ranking)).containsExactly(1, 1);
    }

    @Test
    void perfectKdaTieBreakUsesSortValueWhenWinRateAndSetsAreEqual() {
        List<PlayerSetRecord> records = concat(
                games("p1", Position.MID, 10, 5, 20, 1, 20),     // 40.0
                games("p2", Position.SUPPORT, 10, 5, 0, 0, 2));  // PERFECT, 정렬값 20

        assertThat(order(RankingCalculator.overall(records, MIN_SETS))).containsExactly("p1", "p2");
    }

    // ---------- 최소 표본 기본값 (사용자 결정: 단위 = 세트, 기본 5) ----------

    @Test
    void defaultMinimumSampleIsFiveSets() {
        assertThat(RankingCalculator.DEFAULT_MIN_SETS).isEqualTo(5);

        List<PlayerSetRecord> records = concat(
                games("p1", Position.MID, 5, 3, 5, 3, 5),
                games("p2", Position.MID, 4, 4, 5, 3, 5));

        var overall = RankingCalculator.overall(records);
        assertThat(overall.get(0).playerId()).isEqualTo("p1");
        assertThat(overall.get(0).isRanked()).isTrue();
        assertThat(overall.get(1).isRanked()).isFalse();

        var midRanking = RankingCalculator.byPosition(records, Position.MID);
        assertThat(midRanking.get(0).isRanked()).isTrue();
        assertThat(midRanking.get(1).isRanked()).isFalse();
    }


    // ---------- 포지션 랭킹 ----------

    @Test
    void positionRankingUsesOnlySetsPlayedInThatPosition() {
        List<PlayerSetRecord> records = concat(
                games("p1", Position.MID, 6, 2, 5, 3, 5),      // MID 6세트 33%
                games("p1", Position.ADC, 10, 10, 9, 1, 9),    // ADC 기록은 MID 랭킹에 섞이면 안 됨
                games("p2", Position.MID, 5, 4, 5, 3, 5),      // MID 5세트 80%
                games("p3", Position.MID, 3, 3, 5, 3, 5));     // MID 3세트 -> 기준 미달

        var ranking = RankingCalculator.byPosition(records, Position.MID, MIN_SETS);

        assertThat(order(ranking)).containsExactly("p2", "p1", "p3");
        assertThat(ranks(ranking)).containsExactly(1, 2, RankingEntry.UNRANKED);
        assertThat(ranking.get(1).summary().sets()).isEqualTo(6);
        assertThat(ranking.get(1).summary().wins()).isEqualTo(2);
    }

    @Test
    void playersWhoNeverPlayedThePositionAreNotListed() {
        List<PlayerSetRecord> records = concat(
                games("p1", Position.MID, 6, 3, 5, 3, 5),
                games("p2", Position.TOP, 6, 3, 5, 3, 5));

        assertThat(order(RankingCalculator.byPosition(records, Position.TOP, MIN_SETS))).containsExactly("p2");
    }

    @Test
    void emptyRecordsGiveEmptyRanking() {
        assertThat(RankingCalculator.overall(List.of(), MIN_SETS)).isEmpty();
    }
}
