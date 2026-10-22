package com.lolinhouse.domain.team;

import com.lolinhouse.domain.tier.LaneTier;
import com.lolinhouse.domain.tier.TierSub;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 역할: 수동 모드 티어 단계 차이로 티어 없는 사람의 추정 티어를 정하는 규칙(한 명 없음 위/아래·자르기, 둘 다 없음, 비슷함, 둘 다 있음).
 */
class TierGapEstimatorTest {

    private static LaneTier t(int tier, TierSub sub) {
        return new LaneTier(tier, sub);
    }

    @Test
    void 한_명_없고_없는_사람이_더_잘하면_상대_티어에서_n을_빼고_sub는_상대와_같다() {
        LaneTier[] out = TierGapEstimator.estimate(null, t(4, TierSub.HIGH), new TierGap(0, 2));
        assertThat(out).containsExactly(t(2, TierSub.HIGH), t(4, TierSub.HIGH));
    }

    @Test
    void 한_명_없고_없는_사람이_못하면_상대_티어에_n을_더한다_둘째가_없어도_같다() {
        LaneTier[] out = TierGapEstimator.estimate(t(2, TierSub.LOW), null, new TierGap(0, 3));
        assertThat(out).containsExactly(t(2, TierSub.LOW), t(5, TierSub.LOW));
    }

    @Test
    void 한_명_없을_때_범위를_넘으면_1과_6으로_자른다() {
        assertThat(TierGapEstimator.estimate(null, t(2, TierSub.MID), new TierGap(0, 5))[0]).isEqualTo(t(1, TierSub.MID));
        assertThat(TierGapEstimator.estimate(null, t(5, TierSub.MID), new TierGap(1, 5))[0]).isEqualTo(t(6, TierSub.MID));
    }

    @Test
    void 한_명_없고_비슷함이면_상대_티어_그대로다() {
        LaneTier[] out = TierGapEstimator.estimate(null, t(3, TierSub.MID), new TierGap(null, 0));
        assertThat(out[0]).isEqualTo(t(3, TierSub.MID));
    }

    @Test
    void 둘_다_없으면_못하는_쪽_6티어_MID_잘하는_쪽_6_빼기_n티어_MID() {
        assertThat(TierGapEstimator.estimate(null, null, new TierGap(1, 2)))
                .containsExactly(t(6, TierSub.MID), t(4, TierSub.MID));
        assertThat(TierGapEstimator.estimate(null, null, new TierGap(0, 5)))
                .containsExactly(t(1, TierSub.MID), t(6, TierSub.MID));
    }

    @Test
    void 둘_다_없고_비슷함이면_둘_다_6티어_MID() {
        assertThat(TierGapEstimator.estimate(null, null, new TierGap(null, 3)))
                .containsExactly(t(6, TierSub.MID), t(6, TierSub.MID));
    }

    @Test
    void 둘_다_있으면_입력을_그대로_두고_gap을_무시한다() {
        LaneTier[] out = TierGapEstimator.estimate(t(1, TierSub.HIGH), t(5, TierSub.LOW), new TierGap(0, 5));
        assertThat(out).containsExactly(t(1, TierSub.HIGH), t(5, TierSub.LOW));
    }

    @Test
    void 입력_규칙_stronger는_0_1_null_tiers는_0에서_5() {
        assertThat(TierGapEstimator.isValid(new TierGap(null, 0))).isTrue();
        assertThat(TierGapEstimator.isValid(new TierGap(1, 5))).isTrue();
        assertThat(TierGapEstimator.isValid(new TierGap(2, 1))).isFalse();
        assertThat(TierGapEstimator.isValid(new TierGap(0, 6))).isFalse();
        assertThat(TierGapEstimator.isValid(new TierGap(0, -1))).isFalse();
        assertThat(TierGapEstimator.isValid(null)).isFalse();
    }
}
