package com.lolinhouse.auth;

import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 역할: SPA가 시작할 때(그리고 로그아웃 뒤) 호출해 XSRF-TOKEN 쿠키를 받는 엔드포인트. 본문 없이 204.
 */
@RestController
public class CsrfController {

    @GetMapping("/api/csrf")
    ResponseEntity<Void> issueCsrfCookie(CsrfToken csrfToken) {
        // 토큰은 실제로 읽는 순간에야 만들어져 쿠키에 저장된다(지연 로딩). getToken() 호출이 쿠키 발급을 일으킨다.
        csrfToken.getToken();
        return ResponseEntity.noContent().build();
    }
}
