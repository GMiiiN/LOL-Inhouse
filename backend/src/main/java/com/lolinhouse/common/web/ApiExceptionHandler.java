package com.lolinhouse.common.web;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * 역할: 컨트롤러 단계의 모든 예외를 application/problem+json(ProblemDetail + code)으로 바꾼다.
 * 400 VALIDATION_FAILED(+errors), 400 MALFORMED_REQUEST, ApiException(상태·code 그대로), 500 INTERNAL_ERROR(내부 메시지 숨김).
 * 프레임워크가 던지는 나머지 표준 예외(404·405·415 등)는 부모 클래스가 처리하고, code가 없으면 상태 이름을 code로 채운다.
 * 필터 단계(401·CSRF 403)는 MVC 밖이라 여기가 아니라 AuthJsonResponder가 맡는다.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> Map.of("field", e.getField(), "message", String.valueOf(e.getDefaultMessage())))
                .toList();
        ProblemDetail problem = ProblemResponses.of(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                "요청 값이 올바르지 않습니다.");
        problem.setProperty("errors", errors);
        return handleExceptionInternal(ex, problem, headers, HttpStatus.BAD_REQUEST, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        // 파서 오류 문구에는 내부 클래스 이름이 섞여 있어 그대로 내보내지 않는다.
        ProblemDetail problem = ProblemResponses.of(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST",
                "요청 본문을 읽을 수 없습니다. JSON 형식과 값의 타입을 확인하세요.");
        return handleExceptionInternal(ex, problem, headers, HttpStatus.BAD_REQUEST, request);
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<Object> handleApiException(ApiException ex, WebRequest request) {
        ProblemDetail problem = ProblemResponses.of(ex.status(), ex.code(), ex.getMessage());
        ex.properties().forEach(problem::setProperty);
        return handleExceptionInternal(ex, problem, new HttpHeaders(), ex.status(), request);
    }

    /**
     * 동시 요청끼리 잠금을 기다리다 MySQL이 한쪽을 교착 희생자로 고르거나 잠금 대기가 끝난 경우.
     * 그 트랜잭션은 이미 통째로 롤백됐으므로 500이 아니라 "다시 시도하라"는 409로 알린다.
     */
    @ExceptionHandler(PessimisticLockingFailureException.class)
    ResponseEntity<Object> handleLockFailure(PessimisticLockingFailureException ex, WebRequest request) {
        log.warn("동시 요청 잠금 충돌: {}", ex.getClass().getSimpleName());
        ProblemDetail problem = ProblemResponses.of(HttpStatus.CONFLICT, "CONCURRENT_REQUEST_CONFLICT",
                "동시에 처리된 다른 요청과 충돌했습니다. 다시 시도하세요.");
        return handleExceptionInternal(ex, problem, new HttpHeaders(), HttpStatus.CONFLICT, request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> handleUnexpected(Exception ex, WebRequest request) throws Exception {
        // 인증·인가 예외는 보안 필터(ExceptionTranslationFilter)가 401/403으로 바꾸도록 그대로 넘긴다.
        if (ex instanceof AccessDeniedException || ex instanceof AuthenticationException) {
            throw ex;
        }
        log.error("처리되지 않은 예외", ex);
        ProblemDetail problem = ProblemResponses.of(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "서버 내부 오류가 발생했습니다.");
        return handleExceptionInternal(ex, problem, new HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR, request);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {
        if (body instanceof ProblemDetail problem && (problem.getProperties() == null
                || !problem.getProperties().containsKey(ProblemResponses.CODE))) {
            HttpStatus status = HttpStatus.resolve(statusCode.value());
            problem.setProperty(ProblemResponses.CODE, status == null ? "ERROR" : status.name());
        }
        return super.handleExceptionInternal(ex, body, headers, statusCode, request);
    }
}
