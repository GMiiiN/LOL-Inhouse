package com.lolinhouse.registration;

import com.lolinhouse.domain.Position;
import com.lolinhouse.domain.Side;
import com.lolinhouse.ingest.extract.ExtractedScoreboard.TopResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * 역할: 세트 검사(check)와 저장이 같이 쓰는 명령 본문. 날짜·경기 시간은 문자열로 받아 형식 오류를 규칙 위반(422)으로 돌려준다.
 * newDuel이 있으면 새 대결(세트 1, 블루=A팀) 기준으로 평가·저장한다. 설명: docs/design/m4-design.md §4.1, §11.1
 */
public record SetRegistrationRequest(
        @NotNull Long extractionJobId,
        @NotNull Side topSide,
        @NotNull Position capturerPosition,
        @NotNull TopResult topResult,
        @NotNull String shotDate,
        @NotNull String duration,
        @NotNull @Valid TeamInput top,
        @NotNull @Valid TeamInput bottom,
        @Valid NewDuelOption newDuel) {
}
