package com.lolinhouse.teammaker;

import java.util.List;
import java.util.Map;

import com.lolinhouse.domain.Position;
import com.lolinhouse.domain.team.TierGap;

/**
 * 역할: POST /api/team-maker/manual 요청(공개). picks는 라인마다 [첫째, 둘째] 2명(JSON 키는 TOP, JUNGLE, MID, ADC, SUPPORT).
 * together·apart는 자동 모드와 같다. tierGaps(선택)는 그 라인에 티어 없는 사람이 있을 때 필수인 티어 단계 차이.
 */
public record TeamMakerManualRequest(Map<Position, List<Long>> picks, List<List<Long>> together,
        List<List<Long>> apart, Map<Position, TierGap> tierGaps) {
}
