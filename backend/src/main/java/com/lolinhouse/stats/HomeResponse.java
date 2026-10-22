package com.lolinhouse.stats;

import java.util.List;
import java.util.Map;

import com.lolinhouse.domain.Position;
import com.lolinhouse.record.SetView;

/**
 * 역할: GET /api/home 응답(설계 §9 Q3). 최근 3세트와, 라인 5개마다 전체 기간에서 가장 많이 픽된 챔피언(없으면 null, 키는 항상 5라인 모두).
 * 가장 많이 픽된 챔피언의 동률은 승률, 그다음 챔피언 이름(한국어) 순으로 정한다.
 */
public record HomeResponse(List<SetView> recentSets, Map<Position, TopPick> topPicksByPosition) {

    public record TopPick(String championKey, int picks, int wins) {
    }
}
