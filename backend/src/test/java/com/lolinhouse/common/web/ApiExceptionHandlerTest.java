package com.lolinhouse.common.web;

import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.context.request.ServletWebRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 역할: 컨트롤러로 일부러 500을 낼 방법이 없는 두 규칙을 단위로 검증한다: 500은 내부 메시지를 숨기고,
 * 인증·인가 예외는 삼키지 않고 보안 필터로 넘긴다
 */
class ApiExceptionHandlerTest {

    final ApiExceptionHandler handler = new ApiExceptionHandler();
    final ServletWebRequest request = new ServletWebRequest(new MockHttpServletRequest());

    @Test
    void unexpectedExceptionBecomesInternalErrorWithoutLeakingMessage() throws Exception {
        ResponseEntity<Object> response = handler.handleUnexpected(
                new IllegalStateException("jdbc:mysql://secret-host/db 비밀번호 틀림"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        ProblemDetail body = (ProblemDetail) response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getProperties()).containsEntry("code", "INTERNAL_ERROR");
        assertThat(String.valueOf(body.getDetail())).doesNotContain("secret-host").doesNotContain("비밀번호");
    }

    @Test
    void accessDeniedIsRethrownForSecurityFilter() {
        assertThatThrownBy(() -> handler.handleUnexpected(new AccessDeniedException("no"), request))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void lockFailureFromConcurrentRequestsBecomesConflictNotInternalError() {
        // MySQL 교착 희생자(1213)·잠금 대기 초과(1205)는 Spring이 CannotAcquireLockException으로 바꾼다(Codex M3 리뷰 2)
        ResponseEntity<Object> response = handler.handleLockFailure(
                new CannotAcquireLockException("Deadlock found when trying to get lock"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        ProblemDetail body = (ProblemDetail) response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getProperties()).containsEntry("code", "CONCURRENT_REQUEST_CONFLICT");
        assertThat(String.valueOf(body.getDetail())).doesNotContain("Deadlock");
    }

    @Test
    void apiExceptionKeepsStatusCodeAndExtraProperties() {
        ApiException exception = ApiException.conflict("SOME_CODE", "설명").with("extra", 7);
        ResponseEntity<Object> response = handler.handleApiException(exception, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        ProblemDetail body = (ProblemDetail) response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getProperties()).containsEntry("code", "SOME_CODE").containsEntry("extra", 7);
    }
}
