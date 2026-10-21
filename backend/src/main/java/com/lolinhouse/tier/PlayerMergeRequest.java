package com.lolinhouse.tier;

import jakarta.validation.constraints.NotNull;

/** 역할: 선수 합치기 요청 본문. targetId = 기록을 받을 선수. */
public record PlayerMergeRequest(@NotNull Long targetId) {
}
