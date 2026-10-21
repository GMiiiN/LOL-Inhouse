package com.lolinhouse.domain.stats;

import com.lolinhouse.domain.DuelTeam;
import com.lolinhouse.domain.Position;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PlayTotalsTest {

    private static PlayerSetRecord rec(int cs, int gold) {
        return new PlayerSetRecord("p", "d", "s", DuelTeam.TEAM1, Position.MID, "Ahri", true, 1, 1, 1, cs, gold);
    }

    @Test
    void sumsCsAndGold() {
        var totals = PlayTotals.of(List.of(rec(200, 10000), rec(150, 8000), rec(0, 500)));

        assertThat(totals).isEqualTo(new PlayTotals(350, 18500));
    }

    @Test
    void emptyIsZero() {
        assertThat(PlayTotals.of(List.of())).isEqualTo(new PlayTotals(0, 0));
    }

    @Test
    void sumsAboveIntRangeWithoutOverflow() {
        var totals = PlayTotals.of(List.of(rec(1, Integer.MAX_VALUE), rec(1, Integer.MAX_VALUE)));

        assertThat(totals.gold()).isEqualTo(2L * Integer.MAX_VALUE);
    }
}
