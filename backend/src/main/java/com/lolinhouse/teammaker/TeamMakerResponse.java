package com.lolinhouse.teammaker;

import java.util.List;
import java.util.Map;

import com.lolinhouse.domain.Position;
import com.lolinhouse.domain.tier.TierSub;

/**
 * 역할: 팀 짜기 계산 응답(자동·수동 공통). options는 1~4개이고 blue/red는 라인 순서 슬롯 5개씩, 블루 칸이 A팀이다.
 * scoreMatrix는 참가자 id(JSON에서는 문자열 키) x 5라인 점수표로 옵션과 같은 조회 스냅샷에서 만든다(설계 §11 P2-4).
 */
public record TeamMakerResponse(List<Option> options, Map<Long, Map<Position, MatrixCell>> scoreMatrix) {

    /** 방안 하나. cost = diff * 3 + gap, total = blueSum + redSum. */
    public record Option(List<SlotView> blue, List<SlotView> red, int blueSum, int redSum, int diff, int gap,
            int cost, int total) {
    }

    /** 한 자리. tier·sub가 null이면 티어 없는 라인(점수는 대체 점수). */
    public record SlotView(long playerId, Position position, Integer tier, TierSub sub, int score) {
    }

    /** 점수표 한 칸. 프런트는 교체 후 이 값을 찾아 쓴다. */
    public record MatrixCell(int score, Integer tier, TierSub sub, boolean hasTier) {
    }
}
