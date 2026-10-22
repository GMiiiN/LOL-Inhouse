package com.lolinhouse.session;

import java.util.List;

/** 역할: 관리자 묶음 상세의 대결 한 줄(DuelResponse 필드 + 넣은 세트 목록, setNo 오름차순, 삭제 표시 포함). */
public record AdminDuelResponse(Long id, Long sessionId, int seq, FearlessMode fearlessMode,
        List<AdminSetSummary> sets) {
}
