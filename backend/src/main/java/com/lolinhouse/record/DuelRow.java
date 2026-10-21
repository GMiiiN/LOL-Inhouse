package com.lolinhouse.record;

import com.lolinhouse.session.FearlessMode;

/** 역할: 대결 한 줄 프로젝션. 피어리스 체인 계산(같은 묶음의 모든 대결)에 쓴다. */
public record DuelRow(long duelId, long sessionId, int seq, FearlessMode fearlessMode) {
}
