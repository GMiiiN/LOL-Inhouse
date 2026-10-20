package com.lolinhouse.registration;

import com.lolinhouse.domain.Position;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 역할: 저장 명령의 선수 한 행(화면 순서). 형태 검증만 하고 규칙 검증은 SetEvaluator가 한다.
 * nickname은 확인된 표기(읽은 값 또는 담당자가 고친 전체 닉네임). 설명: docs/design/m4-design.md §4.1, §11.2-1
 */
public record RowInput(
        @NotNull @Min(1) @Max(5) Integer screenRow,
        @NotNull Position position,
        @NotBlank @Size(max = 100) String nickname,
        @NotNull RowResolution resolution,
        Long playerId,
        boolean addAlias,
        @Size(max = 40) String championKey,
        @NotNull @Min(0) Integer kills,
        @NotNull @Min(0) Integer deaths,
        @NotNull @Min(0) Integer assists,
        @NotNull @Min(0) Integer cs,
        @NotNull @Min(0) Integer gold) {
}
