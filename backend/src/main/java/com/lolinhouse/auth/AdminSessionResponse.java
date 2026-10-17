package com.lolinhouse.auth;

/** 역할: 로그인 성공·GET /api/admin/me 응답 본문 {"username": ...} */
public record AdminSessionResponse(String username) {
}
