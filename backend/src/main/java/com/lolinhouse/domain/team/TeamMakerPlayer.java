package com.lolinhouse.domain.team;

import java.util.EnumMap;
import java.util.Map;

import com.lolinhouse.domain.Position;
import com.lolinhouse.domain.tier.LaneTier;

/**
 * 역할: 팀 짜기 계산에 들어가는 사람 한 명. lanes에 없는 라인 = 그 라인 티어 없음.
 */
public record TeamMakerPlayer(long id, Map<Position, LaneTier> lanes) {

    public TeamMakerPlayer {
        lanes = lanes.isEmpty() ? new EnumMap<>(Position.class) : new EnumMap<>(lanes);
    }

    /** 그 라인 티어. 없으면 null. */
    public LaneTier tierOf(Position position) {
        return lanes.get(position);
    }
}
