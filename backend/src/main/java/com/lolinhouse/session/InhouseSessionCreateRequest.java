package com.lolinhouse.session;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;

/** 역할: POST /api/admin/inhouse-sessions 요청. 날짜 형식이 틀리면 본문 파싱 단계에서 400 MALFORMED_REQUEST */
public record InhouseSessionCreateRequest(@NotNull LocalDate startDate) {
}
