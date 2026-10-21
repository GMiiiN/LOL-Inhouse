package com.lolinhouse.stats;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.lolinhouse.domain.Position;
import com.lolinhouse.domain.stats.DuelResult;
import com.lolinhouse.domain.tier.TierSub;
import com.lolinhouse.record.SetView;
import com.lolinhouse.tier.TierListResponse.Trend;

/**
 * 역할: GET /api/players/{id} 응답(선수 프로필). 선수 존재는 경기 기록과 별개로 확인한다(기록이 없으면 빈 통계).
 * laneTiers는 5라인 키가 모두 있고 티어가 없는 라인은 null(점수는 공개하지 않는다). recentSets는 최근 5세트.
 * 챔피언 행에서 기록 목록으로 가는 링크는 /records?player=&champion=&championScope=PLAYER_PICK 이다.
 */
public record PlayerRecordResponse(
        long playerId,
        String displayName,
        boolean registered,
        Position mainPosition,
        Map<Position, LaneTierView> laneTiers,
        int totalSets,
        double participationRate,
        SummaryView summary,
        List<PositionSummary> byPosition,
        List<ChampionSummary> byChampion,
        DuelResults duels,
        List<SetView> recentSets) {

    public record LaneTierView(int tier, TierSub sub, Trend trend) {
    }

    public record PositionSummary(Position position, SummaryView summary, List<PlayerListResponse.TopChampion> topChampions) {
    }

    public record ChampionSummary(String championKey, SummaryView summary) {
    }

    /** 대결별 개인 결과. 합계(wins/draws/losses)와 최근 대결부터의 목록. */
    public record DuelResults(int wins, int draws, int losses, List<Item> items) {
    }

    public record Item(long duelId, LocalDate sessionDate, int seq, int wins, int losses, DuelResult result) {
    }
}
