package com.lolinhouse.record;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.lolinhouse.domain.DuelTeam;
import com.lolinhouse.domain.stats.DuelResult;
import com.lolinhouse.session.FearlessMode;

/**
 * 역할: GET /api/duels/{id} 응답. 세트는 전부(setNo 오름차순), personal은 이 대결에 뛴 선수별 결과.
 * 맞라인 교환이 있으면 한 선수의 teams가 둘일 수 있다. fearless는 "다음 세트 기준" 제한을 이어온 것과 이 대결에서 픽된 것으로 나눈다.
 */
public record DuelDetailResponse(
        long duelId,
        int seq,
        FearlessMode fearlessMode,
        Map<DuelTeam, Integer> score,
        int totalSets,
        long sessionId,
        LocalDate sessionDate,
        List<SetView> sets,
        List<Personal> personal,
        Fearless fearless) {

    public record Personal(
            long playerId,
            String displayName,
            boolean registered,
            List<DuelTeam> teams,
            int wins,
            int losses,
            DuelResult result) {
    }

    /** inherited = 이전 대결에서 이어온 제한(RESET이면 빈 목록), picked = 이 대결에서 픽된 챔피언. 모두 키 오름차순. */
    public record Fearless(List<String> inherited, List<String> picked) {
    }
}
