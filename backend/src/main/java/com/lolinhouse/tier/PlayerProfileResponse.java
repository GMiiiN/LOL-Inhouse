package com.lolinhouse.tier;

import java.util.List;
import java.util.Map;

import com.lolinhouse.domain.Position;
import com.lolinhouse.domain.tier.TierSub;
import com.lolinhouse.player.PlayerResponse.AliasResponse;

/**
 * 역할: 선수 프로필 응답(GET 목록·POST 201·PUT 200). 미등록 선수 포함. lanes는 5라인 키가 모두 있고 티어 없는 라인은 null.
 */
public record PlayerProfileResponse(
        Long id,
        String displayName,
        String realName,
        boolean registered,
        boolean flagged,
        Position mainPosition,
        List<AliasResponse> aliases,
        Map<Position, LaneValue> lanes) {

    /** 라인 티어 값(편집기 초기값). */
    public record LaneValue(int tier, TierSub sub) {
    }
}
