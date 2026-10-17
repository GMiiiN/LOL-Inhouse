package com.lolinhouse.domain.stats;

import com.lolinhouse.domain.DuelTeam;
import com.lolinhouse.domain.Position;

/**
 * 역할: 한 선수가 한 세트에서 남긴 기록 한 줄. 통계의 입력 단위(저장 방식과 무관한 메모리 값).
 * playerId·duelId·setId는 문자열 ID, champion은 챔피언 키, win은 본인 팀의 실제 승패.
 */
public record PlayerSetRecord(
        String playerId,
        String duelId,
        String setId,
        DuelTeam team,
        Position position,
        String champion,
        boolean win,
        int kills,
        int deaths,
        int assists,
        int cs,
        int gold) {
}
