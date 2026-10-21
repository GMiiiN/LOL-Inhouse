package com.lolinhouse.stats;

import java.util.List;

import com.lolinhouse.domain.stats.RankingKey;

/**
 * 역할: GET /api/rankings 응답. position은 "ALL" 또는 Position 이름, 순서는 서버 정렬 그대로(순위 없는 사람이 뒤).
 * rank가 null이면 표본 미달(도메인의 UNRANKED 0을 null로 바꾼 값)이다. minSets는 5.
 */
public record RankingResponse(String position, RankingKey sort, int minSets, int totalSets, List<Entry> entries) {

    public record Entry(long playerId, String displayName, boolean registered, Integer rank, SummaryView summary) {
    }
}
