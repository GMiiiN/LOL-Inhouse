package com.lolinhouse.record;

import com.lolinhouse.domain.Side;

/** 역할: 한 세트의 밴 한 칸 프로젝션. turn 1~10 = B1 R1 ... B5 R5. */
public record BanRow(long setId, int turn, Side side, String championKey) {
}
