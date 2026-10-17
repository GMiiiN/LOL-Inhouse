package com.lolinhouse.auth;

import java.security.Principal;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 역할: 로그인 상태 확인. 200 {"username"} (미인증은 시큐리티가 401). 프런트가 새로고침 뒤 세션이 살아있는지 물을 때 쓴다.
 */
@RestController
public class AdminController {

    @GetMapping("/api/admin/me")
    AdminSessionResponse me(Principal principal) {
        return new AdminSessionResponse(principal.getName());
    }
}
