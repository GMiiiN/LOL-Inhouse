package com.lolinhouse.record;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.lolinhouse.domain.DuelTeam;
import com.lolinhouse.session.FearlessMode;

/**
 * 역할: GET /api/records 응답. 묶음(시작일 내림차순) → 대결(seq 오름차순) → 세트(setNo 오름차순)이고 sets에는 필터에 맞는 세트만 담긴다.
 * 대결의 score·totalSets는 필터와 관계없는 그 대결의 유효 세트 전체 값이다. totals는 현재 필터 전체 기준이다. nextBefore가 null이면 마지막 페이지(다음 요청의 before 값).
 */
public record RecordPageResponse(List<SessionBlock> sessions, LocalDate nextBefore, Totals totals) {

    /** 필터·기간에 맞는 전체 대결 수(맞는 세트가 1개 이상인 대결)와 세트 수. 커서·limit과 무관하다. */
    public record Totals(int duels, int sets) {
    }

    public record SessionBlock(long sessionId, LocalDate startDate, List<DuelBlock> duels) {
    }

    /** score 키는 "TEAM1"/"TEAM2". 앞선 팀은 강조만 하고 승리라고 단정하지 않는다(대결 종료 확정이 미정). */
    public record DuelBlock(
            long duelId,
            int seq,
            FearlessMode fearlessMode,
            Map<DuelTeam, Integer> score,
            int totalSets,
            List<SetView> sets) {
    }
}
