package com.lolinhouse.tier;

import java.util.List;
import java.util.Map;

import com.lolinhouse.domain.Position;
import com.lolinhouse.domain.tier.TierSub;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 역할: POST/PUT /api/admin/player-profiles 요청. 선수 한 명의 실명·주력 포지션·라인 티어·닉네임 목록을 한 번에 담는다.
 * lanes에서 키가 없거나 값이 null인 라인 = 티어 없음(기존 행이 있으면 삭제). 형태 검증만 여기서 하고
 * recordChange(선택, 기본 true)가 false면 티어를 바꿔도 ▲▼를 갱신하지 않는다.
 * flagged(요주 인물)는 생략·null이면 생성은 false, 수정은 기존 값 유지.
 * 규칙(INVALID_LANE_TIER, LANE_TIER_REQUIRED, NICKNAME_REQUIRED, 닉네임 개수 등)은 서비스가 검사한다.
 */
public record PlayerProfileRequest(
        @Size(max = 50) String realName,
        Position mainPosition,
        Map<Position, LaneTierInput> lanes,
        @NotNull List<@Size(max = 50) String> nicknames,
        Boolean recordChange,
        Boolean flagged) {

    /** 라인 티어를 바꿀 때 ▲▼(마지막 변경)를 갱신할지. 생략하면 true, false는 가져오기 도구용. */
    public boolean shouldRecordChange() {
        return recordChange == null || recordChange;
    }

    /** 한 라인 입력. 범위 검사를 서비스에서 하려고 tier는 Integer. */
    public record LaneTierInput(Integer tier, TierSub sub) {
    }
}
