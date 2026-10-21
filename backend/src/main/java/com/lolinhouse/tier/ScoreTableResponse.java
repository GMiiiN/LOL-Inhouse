package com.lolinhouse.tier;

import java.util.LinkedHashMap;
import java.util.Map;

import com.lolinhouse.domain.Position;
import com.lolinhouse.domain.tier.ScoreTable;
import com.lolinhouse.domain.tier.TierSub;

/**
 * 역할: 점수표 응답(GET /api/score-table, 티어리스트 응답 안, PUT/reset 응답). 프런트 tierTypes.ts의 ScoreTableResponse와 필드가 같다.
 * base 키는 티어 1~6(JSON에서는 문자열 "1"~"6"), isDefault는 DEFAULT와 모든 칸이 같은지다.
 */
public record ScoreTableResponse(
        Map<Integer, Integer> base,
        Map<TierSub, Integer> sub,
        Map<Position, Integer> lane,
        boolean isDefault) {

    public static ScoreTableResponse from(ScoreTable table) {
        Map<Integer, Integer> base = new LinkedHashMap<>();
        for (int tier = 1; tier <= 6; tier++) {
            base.put(tier, table.baseOf(tier));
        }
        Map<TierSub, Integer> sub = new LinkedHashMap<>();
        for (TierSub s : TierSub.values()) {
            sub.put(s, table.sub().get(s));
        }
        Map<Position, Integer> lane = new LinkedHashMap<>();
        for (Position p : Position.values()) {
            lane.put(p, table.lane().get(p));
        }
        return new ScoreTableResponse(base, sub, lane, table.equals(ScoreTable.DEFAULT));
    }
}
