package com.lolinhouse.session;

import java.time.LocalDate;

/** 역할: 내전 묶음 요약 DTO(목록·생성 응답). 프런트 types.ts의 InhouseSessionSummary */
public record InhouseSessionSummaryResponse(Long id, LocalDate startDate, long duelCount) {
}
