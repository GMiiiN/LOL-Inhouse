package com.lolinhouse.domain.team;

import com.lolinhouse.domain.Position;
import com.lolinhouse.domain.tier.LaneTier;

/**
 * 역할: 한 팀의 한 자리(사람 + 맡은 라인 + 그 라인 티어 + 점수). tier가 null이면 티어 없는 라인이고 점수는 대체 점수다.
 */
public record Slot(long playerId, Position position, LaneTier tier, int score) {
}
