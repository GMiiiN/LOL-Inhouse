package com.lolinhouse.domain.tier;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.lolinhouse.domain.Position;

/**
 * 역할: 점수표(티어별 기본 점수 6칸 + 우열 보정 3칸 + 라인 보정 5칸). 점수 = base[티어] + sub[우열] + lane[라인].
 * 칸은 항상 14개가 다 차 있고 값은 정수 -999~999다. 기본값 DEFAULT는 V4 시드와 같아야 한다.
 */
public record ScoreTable(List<Integer> base, Map<TierSub, Integer> sub, Map<Position, Integer> lane) {

    public static final int MIN_VALUE = -999;
    public static final int MAX_VALUE = 999;

    /** 아티팩트 기본 점수표(설계 §1.1). */
    public static final ScoreTable DEFAULT = new ScoreTable(
            List.of(60, 52, 44, 36, 28, 20),
            Map.of(TierSub.HIGH, 3, TierSub.MID, 0, TierSub.LOW, -3),
            Map.of(Position.TOP, 0, Position.JUNGLE, 0, Position.MID, 0, Position.ADC, -4, Position.SUPPORT, -3));

    public ScoreTable {
        if (base.size() != LaneTier.MAX_TIER) {
            throw new IllegalArgumentException("기본 점수는 6칸이어야 합니다.");
        }
        for (TierSub s : TierSub.values()) {
            if (!sub.containsKey(s)) {
                throw new IllegalArgumentException("우열 점수가 빠졌습니다: " + s);
            }
        }
        for (Position p : Position.values()) {
            if (!lane.containsKey(p)) {
                throw new IllegalArgumentException("라인 점수가 빠졌습니다: " + p);
            }
        }
        base = List.copyOf(base);
        sub = new EnumMap<>(sub);
        lane = new EnumMap<>(lane);
        base.forEach(ScoreTable::requireInRange);
        sub.values().forEach(ScoreTable::requireInRange);
        lane.values().forEach(ScoreTable::requireInRange);
    }

    /** 티어 1~6의 기본 점수(라인 보정 전의 base 칸). */
    public int baseOf(int tier) {
        return base.get(tier - 1);
    }

    /** 점수(v, k) = base[v.tier] + sub[v.sub] + lane[k]. */
    public int points(LaneTier tier, Position position) {
        return baseOf(tier.tier()) + sub.get(tier.sub()) + lane.get(position);
    }

    /** 라인 티어가 없는 칸의 대체 점수 = 6티어 하 점수(설계 §1.3). */
    public int fallbackPoints(Position position) {
        return points(new LaneTier(LaneTier.MAX_TIER, TierSub.LOW), position);
    }

    private static void requireInRange(int value) {
        if (value < MIN_VALUE || value > MAX_VALUE) {
            throw new IllegalArgumentException("점수는 " + MIN_VALUE + "~" + MAX_VALUE + " 정수여야 합니다: " + value);
        }
    }
}
