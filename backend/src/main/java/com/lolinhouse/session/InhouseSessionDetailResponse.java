package com.lolinhouse.session;

import java.time.LocalDate;
import java.util.List;

/** 역할: 내전 묶음 상세 DTO(대결 목록 포함). 프런트 types.ts의 InhouseSessionDetail */
public record InhouseSessionDetailResponse(Long id, LocalDate startDate, List<DuelResponse> duels) {
}
