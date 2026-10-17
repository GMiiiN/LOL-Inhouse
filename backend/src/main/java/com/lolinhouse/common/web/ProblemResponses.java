package com.lolinhouse.common.web;

import java.io.IOException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import tools.jackson.databind.ObjectMapper;

/**
 * 역할: 모든 오류 응답이 같은 모양(RFC 9457 ProblemDetail + 확장 속성 code)을 갖게 하는 공통 작성기.
 * 컨트롤러 단계(ApiExceptionHandler)는 {@link #of}로 ProblemDetail을 만들어 반환하고,
 * 필터 단계(AuthJsonResponder 등 MVC 밖)는 {@link #write}로 같은 JSON을 직접 쓴다.
 * 필터에서는 MVC 메시지 변환기를 거치지 않으므로 JSON 모양을 {@link #toBody}가 직접 만든다(변환기가 만드는 모양과 같다).
 * 학습 노트: docs/design/m3-design.md §5
 */
public final class ProblemResponses {

    /** 확장 속성 이름. 프런트는 이 값으로 오류 종류를 구분한다(예: LOGIN_FAILED, ALIAS_CONFLICT). */
    public static final String CODE = "code";

    private ProblemResponses() {
    }

    /** 상태 + 기계가 읽는 code + 사람이 읽는 detail. title은 상태의 표준 문구. */
    public static ProblemDetail of(HttpStatus status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(status.getReasonPhrase());
        problem.setProperty(CODE, code);
        return problem;
    }

    /** 필터 단계에서 application/problem+json 본문을 쓴다. 응답이 이미 커밋됐다면 쓰지 않는다. */
    public static void write(HttpServletResponse response, ObjectMapper objectMapper, HttpStatus status, String code,
            String detail) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), toBody(of(status, code, detail)));
    }

    /** ProblemDetail → JSON으로 쓸 맵. type/title/status/detail/instance 뒤에 확장 속성이 같은 수준에 붙는다. */
    public static Map<String, Object> toBody(ProblemDetail problem) {
        Map<String, Object> body = new LinkedHashMap<>();
        URI type = problem.getType();
        body.put("type", type == null ? "about:blank" : type.toString());
        if (problem.getTitle() != null) {
            body.put("title", problem.getTitle());
        }
        body.put("status", problem.getStatus());
        if (problem.getDetail() != null) {
            body.put("detail", problem.getDetail());
        }
        if (problem.getInstance() != null) {
            body.put("instance", problem.getInstance().toString());
        }
        if (problem.getProperties() != null) {
            body.putAll(problem.getProperties());
        }
        return body;
    }
}
