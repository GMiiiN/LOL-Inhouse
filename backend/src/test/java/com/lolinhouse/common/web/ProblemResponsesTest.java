package com.lolinhouse.common.web;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class ProblemResponsesTest {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    void ofSetsStatusTitleDetailAndCodeProperty() {
        ProblemDetail problem = ProblemResponses.of(HttpStatus.CONFLICT, "ALIAS_CONFLICT", "이미 쓰는 닉네임입니다.");

        assertThat(problem.getStatus()).isEqualTo(409);
        assertThat(problem.getTitle()).isEqualTo("Conflict");
        assertThat(problem.getDetail()).isEqualTo("이미 쓰는 닉네임입니다.");
        assertThat(problem.getProperties()).containsEntry("code", "ALIAS_CONFLICT");
    }

    @Test
    void writeProducesProblemJsonWithCodeAtTopLevel() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        ProblemResponses.write(response, jsonMapper, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "로그인이 필요합니다.");

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).startsWith("application/problem+json");
        JsonNode json = jsonMapper.readTree(response.getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        assertThat(json.get("type").asString()).isEqualTo("about:blank");
        assertThat(json.get("title").asString()).isEqualTo("Unauthorized");
        assertThat(json.get("status").asInt()).isEqualTo(401);
        assertThat(json.get("detail").asString()).isEqualTo("로그인이 필요합니다.");
        assertThat(json.get("code").asString()).isEqualTo("UNAUTHORIZED");
        assertThat(json.has("properties")).isFalse();
    }

    @Test
    void writeDoesNothingWhenResponseIsAlreadyCommitted() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setCommitted(true);

        ProblemResponses.write(response, jsonMapper, HttpStatus.FORBIDDEN, "FORBIDDEN", "x");

        assertThat(response.getContentAsString()).isEmpty();
        assertThat(response.getStatus()).isEqualTo(200);
    }
}
