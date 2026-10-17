package com.lolinhouse.player;

import java.util.List;

import com.lolinhouse.domain.Position;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 역할: POST /api/admin/players 요청. 형태 검증만 한다(규칙 검증은 서비스·도메인) */
public record PlayerCreateRequest(
        @NotBlank @Size(max = 50) String realName,
        Position mainPosition,
        @NotNull @Size(min = 1, max = 10) List<@NotBlank @Size(max = 50) String> nicknames) {
}
