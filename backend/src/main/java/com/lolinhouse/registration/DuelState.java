package com.lolinhouse.registration;

import java.util.Map;
import java.util.Set;

import com.lolinhouse.domain.DuelTeam;
import com.lolinhouse.domain.Position;

/**
 * 역할: 평가에 필요한 대결의 현재 상태 스냅샷(DB를 읽어 만든 값 묶음, SetEvaluator는 이것만 본다).
 * previousSet은 직전 유효 세트이고 첫 세트(또는 새 대결)면 null. restrictedChampions는 이어가기 체인 전체의 픽.
 */
public record DuelState(Long duelId, int nextSetNo, PreviousSet previousSet, Set<String> restrictedChampions) {

    /** 직전 세트: 위쪽 팀(알면)과 팀별 선수 id → 포지션. */
    public record PreviousSet(Long setId, int setNo, DuelTeam topTeam, Map<String, Position> team1,
            Map<String, Position> team2) {
    }

    public DuelState {
        restrictedChampions = Set.copyOf(restrictedChampions);
    }

    /** 새 대결(세트 1, 직전 세트 없음) 상태. 제한 챔피언은 선택한 피어리스 방식이 정한다. */
    public static DuelState firstSet(Long duelId, Set<String> restrictedChampions) {
        return new DuelState(duelId, 1, null, restrictedChampions);
    }
}
