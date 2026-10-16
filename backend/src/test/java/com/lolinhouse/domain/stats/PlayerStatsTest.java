package com.lolinhouse.domain.stats;

import com.lolinhouse.domain.DuelTeam;
import com.lolinhouse.domain.Position;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.lolinhouse.domain.stats.StatsFixtures.concat;
import static com.lolinhouse.domain.stats.StatsFixtures.games;
import static com.lolinhouse.domain.stats.StatsFixtures.record;
import static com.lolinhouse.domain.stats.StatsFixtures.recordForTeam;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class PlayerStatsTest {

    // ---------- 누적 KDA · 승률 ----------

    @Test
    void overallUsesCumulativeKdaAndSetWinRate() {
        List<PlayerSetRecord> records = List.of(
                record("p1", "d1", "s1", Position.MID, "Ahri", true, 10, 2, 5),
                record("p1", "d1", "s2", Position.MID, "Ahri", false, 2, 8, 4),
                record("p1", "d2", "s3", Position.MID, "Syndra", true, 7, 3, 9),
                record("p2", "d1", "s1", Position.TOP, "Garen", false, 1, 9, 2));

        StatSummary overall = PlayerStats.overall(records, "p1");

        assertThat(overall.sets()).isEqualTo(3);
        assertThat(overall.wins()).isEqualTo(2);
        assertThat(overall.losses()).isEqualTo(1);
        assertThat(overall.winRate()).isCloseTo(2.0 / 3, within(1e-9));
        assertThat(overall.kda()).isEqualTo(new Kda(19, 13, 18));
        assertThat(overall.kda().ratio()).isCloseTo(37.0 / 13, within(1e-9));
    }

    @Test
    void playerWithoutDeathsHasPerfectKda() {
        List<PlayerSetRecord> records = List.of(
                record("p1", "d1", "s1", Position.SUPPORT, "Lulu", true, 1, 0, 14),
                record("p1", "d1", "s2", Position.SUPPORT, "Lulu", true, 0, 0, 11));

        assertThat(PlayerStats.overall(records, "p1").kda().isPerfect()).isTrue();
    }

    @Test
    void playerWithNoRecordsHasZeroSetsAndZeroWinRate() {
        StatSummary overall = PlayerStats.overall(List.of(), "p1");

        assertThat(overall.sets()).isZero();
        assertThat(overall.winRate()).isZero();
    }

    // ---------- 참여율 ----------

    @Test
    void participationIsPlayersSetsOverAllSetsInPeriod() {
        List<PlayerSetRecord> records = concat(
                games("p1", Position.MID, 10, 6, 5, 3, 7),
                games("p2", Position.TOP, 25, 10, 4, 4, 4));

        assertThat(PlayerStats.participationRate(records, "p1", 25)).isCloseTo(0.4, within(1e-9));
    }

    @Test
    void participationWithZeroTotalSetsIsZero() {
        assertThat(PlayerStats.participationRate(List.of(), "p1", 0)).isZero();
    }

    @Test
    void participationAboveOneIsRejected() {
        List<PlayerSetRecord> records = games("p1", Position.MID, 3, 1, 1, 1, 1);

        assertThatThrownBy(() -> PlayerStats.participationRate(records, "p1", 2))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---------- 포지션별 (유저플로우 12-15: 미드 6 · 원딜 3 · 서폿 1) ----------

    @Test
    void midSixAdcThreeSupportOneSplitsIntoOverallTenAndPerPosition() {
        List<PlayerSetRecord> records = concat(
                games("p1", Position.MID, 6, 4, 6, 3, 8),
                games("p1", Position.ADC, 3, 1, 9, 5, 6),
                games("p1", Position.SUPPORT, 1, 0, 0, 4, 12),
                games("p2", Position.TOP, 10, 5, 3, 3, 3));

        assertThat(PlayerStats.overall(records, "p1").sets()).isEqualTo(10);
        assertThat(PlayerStats.overall(records, "p1").wins()).isEqualTo(5);

        var byPosition = PlayerStats.byPosition(records, "p1");

        assertThat(byPosition.keySet()).containsExactly(Position.MID, Position.ADC, Position.SUPPORT);
        assertThat(byPosition.get(Position.MID).sets()).isEqualTo(6);
        assertThat(byPosition.get(Position.MID).wins()).isEqualTo(4);
        assertThat(byPosition.get(Position.MID).winRate()).isCloseTo(4.0 / 6, within(1e-9));
        assertThat(byPosition.get(Position.MID).kda()).isEqualTo(new Kda(36, 18, 48));
        assertThat(byPosition.get(Position.ADC).sets()).isEqualTo(3);
        assertThat(byPosition.get(Position.ADC).losses()).isEqualTo(2);
        assertThat(byPosition.get(Position.SUPPORT).sets()).isEqualTo(1);
        assertThat(byPosition.get(Position.SUPPORT).winRate()).isZero();
    }

    @Test
    void positionsNeverPlayedAreAbsent() {
        List<PlayerSetRecord> records = games("p1", Position.MID, 2, 1, 1, 1, 1);

        assertThat(PlayerStats.byPosition(records, "p1")).containsOnlyKeys(Position.MID);
    }

    // ---------- 챔피언별 ----------

    @Test
    void championsAreOrderedByUsageDescendingWithTieBrokenByName() {
        List<PlayerSetRecord> records = List.of(
                record("p1", "d1", "s1", Position.MID, "Syndra", true, 5, 1, 5),
                record("p1", "d1", "s2", Position.MID, "Ahri", true, 3, 2, 4),
                record("p1", "d1", "s3", Position.MID, "Ahri", false, 1, 6, 2),
                record("p1", "d2", "s4", Position.MID, "Ahri", true, 8, 2, 6),
                record("p1", "d2", "s5", Position.ADC, "Jinx", false, 2, 5, 3),
                record("p2", "d2", "s5", Position.TOP, "Garen", true, 4, 4, 4));

        List<ChampionStats> champions = PlayerStats.byChampion(records, "p1");

        assertThat(champions).extracting(ChampionStats::champion).containsExactly("Ahri", "Jinx", "Syndra");
        StatSummary ahri = champions.get(0).summary();
        assertThat(ahri.sets()).isEqualTo(3);
        assertThat(ahri.wins()).isEqualTo(2);
        assertThat(ahri.losses()).isEqualTo(1);
        assertThat(ahri.winRate()).isCloseTo(2.0 / 3, within(1e-9));
        assertThat(ahri.kda()).isEqualTo(new Kda(12, 10, 12));
    }

    // ---------- 대결 개인 결과 ----------

    @Test
    void oneWinOneLossInDuelIsDrawWithFiftyPercent() {
        List<PlayerSetRecord> records = List.of(
                record("p1", "d1", "s1", Position.MID, "Ahri", true, 5, 2, 5),
                record("p1", "d1", "s2", Position.MID, "Ahri", false, 2, 6, 3));

        DuelPersonalResult result = PlayerStats.duelResult(records, "p1", "d1");

        assertThat(result.result()).isEqualTo(DuelResult.DRAW);
        assertThat(result.wins()).isEqualTo(1);
        assertThat(result.losses()).isEqualTo(1);
        assertThat(result.setWinRate()).isEqualTo(0.5);
    }

    @Test
    void moreWinsThanLossesIsWinAndFewerIsLoss() {
        List<PlayerSetRecord> records = List.of(
                record("p1", "d1", "s1", Position.MID, "Ahri", true, 5, 2, 5),
                record("p1", "d1", "s2", Position.MID, "Ahri", true, 4, 2, 6),
                record("p1", "d1", "s3", Position.MID, "Ahri", false, 1, 7, 3),
                record("p2", "d1", "s1", Position.TOP, "Garen", false, 1, 7, 3),
                record("p2", "d1", "s2", Position.TOP, "Garen", false, 2, 7, 3),
                record("p2", "d1", "s3", Position.TOP, "Garen", true, 6, 1, 9));

        assertThat(PlayerStats.duelResult(records, "p1", "d1").result()).isEqualTo(DuelResult.WIN);
        assertThat(PlayerStats.duelResult(records, "p2", "d1").result()).isEqualTo(DuelResult.LOSS);
    }

    @Test
    void duelResultCountsOnlySetsThePlayerPlayedInThatDuel() {
        // p1은 d1의 3세트 중 s1, s3만 뛰었다(s2는 교대로 빠짐) + d2 기록은 d1에 섞이면 안 된다.
        List<PlayerSetRecord> records = new ArrayList<>(List.of(
                record("p1", "d1", "s1", Position.MID, "Ahri", true, 5, 2, 5),
                record("p1", "d1", "s3", Position.MID, "Ahri", false, 1, 7, 3),
                record("p3", "d1", "s2", Position.MID, "Ahri", true, 5, 2, 5),
                record("p1", "d2", "s9", Position.MID, "Ahri", false, 0, 9, 1)));

        DuelPersonalResult result = PlayerStats.duelResult(records, "p1", "d1");

        assertThat(result.wins()).isEqualTo(1);
        assertThat(result.losses()).isEqualTo(1);
        assertThat(result.result()).isEqualTo(DuelResult.DRAW);
    }

    // ---------- 같은 라인 팀 교환 (유저플로우 §12-11) ----------

    @Test
    void resultsAfterSameLaneSwapComeFromSetsActuallyPlayedForEachTeam() {
        // s1·s2는 p1=1팀, p2=2팀. 교환 후 s3·s4는 p1=2팀, p2=1팀. 이전 세트는 옛 팀 기록 그대로 남는다.
        List<PlayerSetRecord> records = List.of(
                recordForTeam("p1", "d1", "s1", DuelTeam.TEAM1, Position.MID, "Ahri", true, 5, 2, 5),
                recordForTeam("p1", "d1", "s2", DuelTeam.TEAM1, Position.MID, "Ahri", false, 1, 6, 2),
                recordForTeam("p1", "d1", "s3", DuelTeam.TEAM2, Position.MID, "Ahri", true, 6, 1, 8),
                recordForTeam("p1", "d1", "s4", DuelTeam.TEAM2, Position.MID, "Ahri", true, 4, 2, 7),
                recordForTeam("p2", "d1", "s1", DuelTeam.TEAM2, Position.MID, "Syndra", false, 2, 5, 3),
                recordForTeam("p2", "d1", "s2", DuelTeam.TEAM2, Position.MID, "Syndra", true, 7, 2, 6),
                recordForTeam("p2", "d1", "s3", DuelTeam.TEAM1, Position.MID, "Syndra", false, 1, 8, 2),
                recordForTeam("p2", "d1", "s4", DuelTeam.TEAM1, Position.MID, "Syndra", false, 3, 6, 4));

        StatSummary p1 = PlayerStats.overall(records, "p1");
        assertThat(p1.sets()).isEqualTo(4);
        assertThat(p1.wins()).isEqualTo(3);
        assertThat(p1.losses()).isEqualTo(1);

        DuelPersonalResult p1Duel = PlayerStats.duelResult(records, "p1", "d1");
        assertThat(p1Duel.wins()).isEqualTo(3);
        assertThat(p1Duel.losses()).isEqualTo(1);
        assertThat(p1Duel.result()).isEqualTo(DuelResult.WIN);

        DuelPersonalResult p2Duel = PlayerStats.duelResult(records, "p2", "d1");
        assertThat(p2Duel.wins()).isEqualTo(1);
        assertThat(p2Duel.losses()).isEqualTo(3);
        assertThat(p2Duel.result()).isEqualTo(DuelResult.LOSS);
    }

    @Test
    void duelResultForDuelWithoutPlayerIsRejected() {
        List<PlayerSetRecord> records = games("p1", Position.MID, 2, 1, 1, 1, 1);

        assertThatThrownBy(() -> PlayerStats.duelResult(records, "p9", "d-p1"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
