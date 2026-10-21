package com.lolinhouse.tier;

import java.util.List;
import java.util.Map;

import com.lolinhouse.domain.Position;
import com.lolinhouse.domain.tier.LastChange;
import com.lolinhouse.domain.tier.TierSub;

/**
 * 역할: GET /api/tier-list 응답(공개). 대상은 등록 선수 전체(라인 티어가 0개일 수 있음), id 오름차순.
 * lanes는 5라인 키가 모두 있고 티어가 없는 라인은 null이다. 회차 정보는 없다(설계 §10 Q2).
 */
public record TierListResponse(ScoreTableResponse scoreTable, List<TierPlayer> players) {

    /** 선수 한 명. displayName은 실명. */
    public record TierPlayer(Long id, String displayName, boolean flagged, Map<Position, LaneView> lanes) {
    }

    /** 한 라인. score는 서버가 현재 점수표로 계산한 값이고 trend는 마지막 변경(없으면 null). */
    public record LaneView(int tier, TierSub sub, int score, Trend trend) {
    }

    /** ▲▼ 표시. 툴팁은 fromTier와 현재 티어를 비교해 프런트가 만든다. */
    public record Trend(LastChange.Direction direction, int fromTier, TierSub fromSub) {
    }
}
