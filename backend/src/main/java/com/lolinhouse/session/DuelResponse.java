package com.lolinhouse.session;

/** 역할: 대결 DTO. 프런트 types.ts의 DuelSummary({id, seq, fearlessMode})에 소속 묶음 id를 더한 것 */
public record DuelResponse(Long id, Long sessionId, int seq, FearlessMode fearlessMode) {

    static DuelResponse from(Duel duel) {
        return new DuelResponse(duel.id(), duel.sessionId(), duel.seq(), duel.fearlessMode());
    }
}
