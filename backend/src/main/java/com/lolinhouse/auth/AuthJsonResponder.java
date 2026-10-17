package com.lolinhouse.auth;

import java.io.IOException;

import com.lolinhouse.common.web.ProblemResponses;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * 역할: 스프링 시큐리티 필터 단계(MVC 앞)의 모든 인증·인가 응답을 JSON으로 쓴다. 리다이렉트도 HTML도 없다.
 * 로그인 성공 200 {"username"}, 로그인 실패 401 LOGIN_FAILED, 미인증 401 UNAUTHORIZED, CSRF 403 CSRF_INVALID, 그 외 403 FORBIDDEN.
 * 오류 본문은 모두 ProblemResponses.write(공통 계약, application/problem+json).
 */
@Component
public class AuthJsonResponder
        implements AuthenticationSuccessHandler, AuthenticationFailureHandler, AuthenticationEntryPoint, AccessDeniedHandler {

    private static final Logger log = LoggerFactory.getLogger(AuthJsonResponder.class);

    private final ObjectMapper objectMapper;

    public AuthJsonResponder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException {
        response.setStatus(HttpStatus.OK.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), new AdminSessionResponse(authentication.getName()));
    }

    /** 사용자 없음·비밀번호 틀림·그 밖의 인증 실패를 구분하지 않고 같은 응답을 준다(계정 존재 여부 노출 방지). */
    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException exception) throws IOException {
        // 사용자명·비밀번호는 로그에 남기지 않는다. IP만.
        log.warn("담당자 로그인 실패: ip={}", request.getRemoteAddr());
        ProblemResponses.write(response, objectMapper, HttpStatus.UNAUTHORIZED, "LOGIN_FAILED",
                "아이디 또는 비밀번호가 올바르지 않습니다.");
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) throws IOException {
        ProblemResponses.write(response, objectMapper, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "로그인이 필요합니다.");
    }

    /** CsrfException(토큰 없음·불일치)만 CSRF_INVALID로 구분한다. 프런트는 이 코드를 보면 /api/csrf를 다시 받아 한 번 재시도한다. */
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {
        if (accessDeniedException instanceof CsrfException) {
            ProblemResponses.write(response, objectMapper, HttpStatus.FORBIDDEN, "CSRF_INVALID",
                    "CSRF 토큰이 없거나 올바르지 않습니다.");
            return;
        }
        ProblemResponses.write(response, objectMapper, HttpStatus.FORBIDDEN, "FORBIDDEN", "접근 권한이 없습니다.");
    }
}
