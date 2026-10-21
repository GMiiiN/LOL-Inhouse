package com.lolinhouse.common.web;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;

/**
 * 역할: 서비스가 "이 요청은 이런 이유로 거절"을 알리는 예외. 상태 + 기계가 읽는 code + 사람이 읽는 detail에
 * ProblemDetail 최상위 속성(errors, conflicts, existingSessionId 등)을 덧붙일 수 있다.
 * {@link ApiExceptionHandler}가 {@link ProblemResponses}로 응답을 만든다. RuntimeException이라 트랜잭션은 롤백된다.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final Map<String, Object> properties = new LinkedHashMap<>();

    private ApiException(HttpStatus status, String code, String detail) {
        super(detail);
        this.status = status;
        this.code = code;
    }

    public static ApiException notFound(String code, String detail) {
        return new ApiException(HttpStatus.NOT_FOUND, code, detail);
    }

    public static ApiException conflict(String code, String detail) {
        return new ApiException(HttpStatus.CONFLICT, code, detail);
    }

    public static ApiException badRequest(String code, String detail) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, detail);
    }

    public static ApiException payloadTooLarge(String code, String detail) {
        return new ApiException(HttpStatus.CONTENT_TOO_LARGE, code, detail);
    }

    public static ApiException unprocessable(String code, String detail) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, code, detail);
    }

    public static ApiException tooManyRequests(String code, String detail) {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, code, detail);
    }

    public static ApiException serviceUnavailable(String code, String detail) {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, code, detail);
    }

    /** 응답 JSON 최상위에 붙일 속성을 추가한다(체이닝). */
    public ApiException with(String name, Object value) {
        properties.put(name, value);
        return this;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    public Map<String, Object> properties() {
        return properties;
    }
}
