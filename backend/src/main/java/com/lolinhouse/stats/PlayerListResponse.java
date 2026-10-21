package com.lolinhouse.stats;

import java.util.List;
import java.util.Map;

import com.lolinhouse.domain.Position;

/**
 * 역할: GET /api/players 응답. 대상 = 등록 선수 전체 + 기간 안에 뛴 미등록 선수. 기록이 없으면 summary.sets = 0.
 * positions는 뛴 라인별 세트 수(뛰지 않은 라인 키는 없음), topChampions는 많이 쓴 챔피언 최대 5개.
 */
public record PlayerListResponse(int totalSets, List<Item> players) {

    public record Item(
            long playerId,
            String displayName,
            boolean registered,
            Position mainPosition,
            SummaryView summary,
            Map<Position, Integer> positions,
            List<TopChampion> topChampions,
            int championCount) {
    }

    public record TopChampion(String championKey, int sets) {
    }
}
