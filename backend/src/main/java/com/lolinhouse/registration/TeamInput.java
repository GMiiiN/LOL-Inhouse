package com.lolinhouse.registration;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 역할: 저장 명령의 한 팀. 표시 합계 4개와 화면 순서의 선수 5행·밴 5칸(비어 있으면 null). 설명: docs/design/m4-design.md §4.1 */
public record TeamInput(
        @NotNull @Min(0) Integer displayedKills,
        @NotNull @Min(0) Integer displayedDeaths,
        @NotNull @Min(0) Integer displayedAssists,
        @NotNull @Min(0) Integer displayedGold,
        @NotNull @Size(min = 5, max = 5) List<@NotNull @Valid RowInput> rows,
        @NotNull @Size(min = 5, max = 5) List<@Size(max = 40) String> bans) {
}
