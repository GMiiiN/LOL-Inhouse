package com.lolinhouse.session;

import java.time.LocalDate;

/**
 * 역할: 내전 묶음 요약 DTO(목록·생성 응답). setCount는 삭제된 세트 포함(0이어야 묶음 삭제 가능),
 * activeSetCount는 삭제 표시를 뺀 유효 세트 수. 프런트 types.ts의 InhouseSessionSummary
 */
public record InhouseSessionSummaryResponse(Long id, LocalDate startDate, long duelCount, long setCount,
        long activeSetCount) {
}
