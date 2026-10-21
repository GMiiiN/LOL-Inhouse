package com.lolinhouse.domain.stats;

import com.lolinhouse.domain.DuelTeam;
import com.lolinhouse.domain.Position;
import com.lolinhouse.domain.stats.ChampionPickBan.BannedChampion;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChampionPickBanTest {

    private static PlayerSetRecord pick(String setId, String champion, boolean win) {
        return new PlayerSetRecord("p" + champion, "d", setId, DuelTeam.TEAM1, Position.MID, champion, win, 1, 1, 1, 100, 5000);
    }

    @Test
    void emptyInputGivesEmptyList() {
        assertThat(ChampionPickBan.compute(List.of(), List.of())).isEmpty();
    }

    @Test
    void pickOnlyAndBanOnlyChampions() {
        var result = ChampionPickBan.compute(
                List.of(pick("s1", "Ahri", true)),
                List.of(new BannedChampion("s1", "Zed")));

        assertThat(result).containsExactly(
                new ChampionPickBan("Ahri", 1, 0, 1, 0, 1),
                new ChampionPickBan("Zed", 0, 1, 0, 0, 1));
    }

    @Test
    void sameChampionAcrossSetsCountsWinsLossesAndBans() {
        var result = ChampionPickBan.compute(
                List.of(pick("s1", "Ahri", true), pick("s2", "Ahri", false), pick("s3", "Ahri", true)),
                List.of(new BannedChampion("s4", "Ahri")));

        assertThat(result).containsExactly(new ChampionPickBan("Ahri", 3, 1, 2, 1, 4));
    }

    @Test
    void pickAndBanInSameSetCountsOnceInSetsWithPickOrBan() {
        var result = ChampionPickBan.compute(
                List.of(pick("s1", "Ahri", true)),
                List.of(new BannedChampion("s1", "Ahri"), new BannedChampion("s2", "Ahri")));

        assertThat(result).containsExactly(new ChampionPickBan("Ahri", 1, 2, 1, 0, 2));
    }

    @Test
    void sortedByChampionKeyAscending() {
        var result = ChampionPickBan.compute(
                List.of(pick("s1", "Zed", true), pick("s1", "Ahri", false)),
                List.of(new BannedChampion("s1", "Lux")));

        assertThat(result).extracting(ChampionPickBan::champion).containsExactly("Ahri", "Lux", "Zed");
    }
}
