package com.lolinhouse.player;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 역할: POST /api/admin/players/{id}/aliases 요청 */
public record AliasCreateRequest(@NotBlank @Size(max = 50) String nickname) {
}
