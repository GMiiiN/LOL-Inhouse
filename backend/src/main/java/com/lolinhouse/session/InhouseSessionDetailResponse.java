package com.lolinhouse.session;

import java.time.LocalDate;
import java.util.List;

/** 역할: 내전 묶음 상세 DTO(대결 목록 + 대결별 세트 요약). 프런트 types.ts의 InhouseSessionDetail */
public record InhouseSessionDetailResponse(Long id, LocalDate startDate, List<AdminDuelResponse> duels) {
}
