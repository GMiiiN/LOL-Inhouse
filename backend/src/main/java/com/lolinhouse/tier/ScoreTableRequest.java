package com.lolinhouse.tier;

import java.util.Map;

import com.lolinhouse.domain.Position;
import com.lolinhouse.domain.tier.TierSub;

/**
 * 역할: PUT /api/admin/score-table 요청(응답 모양에서 isDefault만 뺀 것). 14칸이 모두 있어야 하고 정수 -999~999다.
 * 칸 누락·범위 밖·정수 아님은 서비스가 400 INVALID_SCORE_TABLE로 거절한다(값이 null일 수 있어 Integer).
 */
public record ScoreTableRequest(
        Map<Integer, Integer> base,
        Map<TierSub, Integer> sub,
        Map<Position, Integer> lane) {
}
