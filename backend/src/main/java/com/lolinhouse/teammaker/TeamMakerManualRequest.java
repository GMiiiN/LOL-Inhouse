package com.lolinhouse.teammaker;

import java.util.List;
import java.util.Map;

import com.lolinhouse.domain.Position;

/**
 * 역할: POST /api/team-maker/manual 요청(공개). picks는 라인마다 [첫째, 둘째] 2명(JSON 키는 TOP, JUNGLE, MID, ADC, SUPPORT).
 * together·apart는 자동 모드와 같다.
 */
public record TeamMakerManualRequest(Map<Position, List<Long>> picks, List<List<Long>> together,
        List<List<Long>> apart) {
}
