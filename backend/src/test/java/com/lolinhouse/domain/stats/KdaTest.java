package com.lolinhouse.domain.stats;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class KdaTest {

    @Test
    void ratioIsKillsPlusAssistsOverDeaths() {
        Kda kda = new Kda(19, 13, 18);

        assertThat(kda.isPerfect()).isFalse();
        assertThat(kda.ratio()).isCloseTo(37.0 / 13, within(1e-9));
    }

    @Test
    void zeroDeathsIsPerfectNotInfinity() {
        Kda kda = new Kda(8, 0, 5);

        assertThat(kda.isPerfect()).isTrue();
    }

    @Test
    void ratioOfPerfectKdaIsRefused() {
        assertThatThrownBy(() -> new Kda(8, 0, 5).ratio()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void sumAddsTotalsNotRatios() {
        Kda total = new Kda(10, 2, 5).plus(new Kda(2, 8, 4));

        assertThat(total).isEqualTo(new Kda(12, 10, 9));
        // 판별 포인트: 판당 KDA의 평균((15/2 + 6/8) / 2 = 4.125)이 아니라 누적 21/10 = 2.1
        assertThat(total.ratio()).isCloseTo(2.1, within(1e-9));
    }

    @Test
    void ratioDoesNotOverflowForHugeTotals() {
        Kda kda = new Kda(Integer.MAX_VALUE, 1, 1);

        assertThat(kda.ratio()).isCloseTo(Integer.MAX_VALUE + 1.0, within(1e-3));
    }

    @Test
    void summingTwoHugeKdasDoesNotOverflow() {
        Kda total = new Kda(Integer.MAX_VALUE, 1, 0).plus(new Kda(Integer.MAX_VALUE, 1, 0));

        assertThat(total.kills()).isEqualTo(2L * Integer.MAX_VALUE);
        assertThat(total.ratio()).isPositive();
    }
}
