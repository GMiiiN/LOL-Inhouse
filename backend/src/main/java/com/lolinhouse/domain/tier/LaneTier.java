package com.lolinhouse.domain.tier;

import java.util.Objects;

/**
 * 역할: 한 라인의 티어(1이 최상 ~ 6)와 우열. rank()는 "더 잘하는 쪽이 큰 수"이며 변동 방향 판정에만 쓴다.
 */
public record LaneTier(int tier, TierSub sub) {

    public static final int MIN_TIER = 1;
    public static final int MAX_TIER = 6;

    public LaneTier {
        if (tier < MIN_TIER || tier > MAX_TIER) {
            throw new IllegalArgumentException("티어는 1~6이어야 합니다: " + tier);
        }
        Objects.requireNonNull(sub, "sub");
    }

    /** (7 - 티어) * 3 + 우열 보정. 1티어 상 = 20, 6티어 하 = 3. */
    public int rank() {
        return (7 - tier) * 3 + sub.rankBonus();
    }
}
