package com.lolinhouse.edit;

import java.time.Instant;

import com.lolinhouse.domain.Side;
import com.lolinhouse.ingest.extract.ExtractedScoreboard.TopResult;
import com.lolinhouse.registration.SetRegistrationRequest;
import com.lolinhouse.registration.TeamInput;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * 역할: 저장된 세트 수정·검사 본문. 등록 명령과 같은 모양(TeamInput·RowInput 재사용)이다.
 * duration은 "mm:ss"이고 빈 문자열이면 경기 시간 "모름"이다. expectedUpdatedAt은 폼을 받았을 때의 세트 updatedAt(동시 수정 보호).
 */
public record SetEditRequest(
        @NotNull Side topSide,
        @NotNull TopResult topResult,
        @NotNull String shotDate,
        @NotNull String duration,
        @NotNull @Valid TeamInput top,
        @NotNull @Valid TeamInput bottom,
        @NotNull Instant expectedUpdatedAt) {

    /** 평가기가 받는 등록 명령 모양으로. 캡처한 사람 포지션은 위쪽 1행 포지션으로 채운다. */
    SetRegistrationRequest toRegistration() {
        return new SetRegistrationRequest(null, topSide, top.rows().get(0).position(), topResult, shotDate, duration,
                top, bottom, null);
    }
}
