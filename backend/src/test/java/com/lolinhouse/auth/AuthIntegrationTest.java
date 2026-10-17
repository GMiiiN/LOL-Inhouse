package com.lolinhouse.auth;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.lolinhouse.IntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 역할: 보안 설정 전체(인가 순서, CSRF, 로그인 JSON 응답, 세션 고정, 기본 로그인 페이지·기본 사용자 제거)를 MockMvc로 검증한다.
 * 쿠키는 MockMvc가 자동으로 이어 주지 않으므로 직접 붙인다. 실제 서버·실제 쿠키 흐름은 AuthSpaFlowTest가 맡는다.
 * 테스트 계정(test-admin / test-password-1234)은 IntegrationTest 속성으로 기동 때 만들어진다.
 */
@IntegrationTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class AuthIntegrationTest {

    static final String USERNAME = "test-admin";
    static final String PASSWORD = "test-password-1234";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ApplicationContext context;

    /** GET /api/csrf를 호출해 XSRF-TOKEN 쿠키를 받는다(SPA가 시작할 때 하는 일). */
    Cookie issueCsrfCookie() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/csrf")).andExpect(status().isNoContent()).andReturn();
        Cookie cookie = result.getResponse().getCookie("XSRF-TOKEN");
        assertThat(cookie).isNotNull();
        return cookie;
    }

    static Cookie lastNonEmptyCookie(MvcResult result, String name) {
        Cookie found = null;
        for (Cookie cookie : result.getResponse().getCookies()) {
            if (cookie.getName().equals(name) && !cookie.getValue().isEmpty()) {
                found = cookie;
            }
        }
        assertThat(found).as(name + " 쿠키").isNotNull();
        return found;
    }

    MockHttpServletRequestBuilder withCsrf(MockHttpServletRequestBuilder request, Cookie csrfCookie) {
        return request.cookie(csrfCookie).header("X-XSRF-TOKEN", csrfCookie.getValue());
    }

    MockHttpServletRequestBuilder loginRequest(String username, String password, Cookie csrfCookie) {
        return withCsrf(post("/api/admin/login"), csrfCookie)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("username", username)
                .param("password", password);
    }

    // ---- 인증 · CSRF 순서 ----

    @Test
    void anonymousAdminRequestIsUnauthorizedProblemJsonWithoutRedirect() throws Exception {
        mockMvc.perform(get("/api/admin/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().doesNotExist(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void postWithoutCsrfIsForbiddenCsrfInvalidEvenWhenAnonymous() throws Exception {
        // CSRF 검사가 인증보다 먼저라서, 익명 + CSRF 없음은 401이 아니라 403이다.
        mockMvc.perform(post("/api/admin/anything"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().doesNotExist(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
    }

    @Test
    void postWithWrongCsrfHeaderIsCsrfInvalid() throws Exception {
        Cookie csrf = issueCsrfCookie();
        mockMvc.perform(post("/api/admin/anything").cookie(csrf).header("X-XSRF-TOKEN", "wrong-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
    }

    @Test
    void postWithCsrfButUnauthenticatedIsUnauthorized() throws Exception {
        Cookie csrf = issueCsrfCookie();
        mockMvc.perform(withCsrf(post("/api/admin/anything"), csrf))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    // ---- CSRF 쿠키 ----

    @Test
    void csrfEndpointIssuesReadableLaxCookie() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/csrf")).andExpect(status().isNoContent()).andReturn();
        String setCookie = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE).stream()
                .filter(value -> value.startsWith("XSRF-TOKEN="))
                .findFirst().orElseThrow();
        assertThat(setCookie).doesNotContain("HttpOnly").doesNotContain("Secure");
        // MockMvc는 Set-Cookie 문자열에 SameSite를 안 적으므로 쿠키 속성으로 확인한다(실제 헤더 문자열은 AuthSpaFlowTest).
        assertThat(result.getResponse().getCookie("XSRF-TOKEN").getAttribute("SameSite")).isEqualTo("Lax");
        assertThat(result.getResponse().getContentAsString()).isEmpty();
    }

    // ---- 로그인 ----

    @Test
    void loginSucceedsWithUsernameJson() throws Exception {
        Cookie csrf = issueCsrfCookie();
        mockMvc.perform(loginRequest(USERNAME, PASSWORD, csrf))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().doesNotExist(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.username").value(USERNAME));
    }

    @Test
    void loginFailureIsIdenticalForUnknownUserAndWrongPassword() throws Exception {
        Cookie csrf = issueCsrfCookie();
        MvcResult unknownUser = mockMvc.perform(loginRequest("no-such-user", PASSWORD, csrf))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().doesNotExist(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.code").value("LOGIN_FAILED"))
                .andReturn();
        MvcResult wrongPassword = mockMvc.perform(loginRequest(USERNAME, "wrong-password", csrf))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("LOGIN_FAILED"))
                .andReturn();
        assertThat(unknownUser.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8))
                .isEqualTo(wrongPassword.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    }

    @Test
    void loginWithOverlongPasswordIsAlsoLoginFailedNotServerError() throws Exception {
        Cookie csrf = issueCsrfCookie();
        mockMvc.perform(loginRequest(USERNAME, "가".repeat(40), csrf)) // 120바이트
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("LOGIN_FAILED"));
    }

    @Test
    void loginFailureWarnLogContainsNeitherUsernameNorPassword(CapturedOutput output) throws Exception {
        Cookie csrf = issueCsrfCookie();
        mockMvc.perform(loginRequest("secret-user-name", "secret-pass-word", csrf)).andExpect(status().isUnauthorized());
        assertThat(output.getAll()).contains("담당자 로그인 실패").contains("ip=");
        assertThat(output.getAll()).doesNotContain("secret-user-name").doesNotContain("secret-pass-word");
    }

    @Test
    void loginWithoutCsrfIsForbidden() throws Exception {
        mockMvc.perform(post("/api/admin/login")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", USERNAME)
                        .param("password", PASSWORD))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
    }

    @Test
    void sessionIdChangesOnLogin() throws Exception {
        Cookie csrf = issueCsrfCookie();
        MockHttpSession before = new MockHttpSession();
        before.setAttribute("planted", "by-attacker");
        String beforeId = before.getId();

        MvcResult result = mockMvc.perform(loginRequest(USERNAME, PASSWORD, csrf).session(before))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getRequest().getSession(false)).isNotNull();
        assertThat(result.getRequest().getSession(false).getId()).isNotEqualTo(beforeId);
    }

    // ---- 로그인 상태 · 로그아웃 ----

    @Test
    void sessionFromLoginAuthenticatesMeAndLogoutInvalidatesIt() throws Exception {
        Cookie csrf = issueCsrfCookie();
        MvcResult login = mockMvc.perform(loginRequest(USERNAME, PASSWORD, csrf)).andExpect(status().isOk()).andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        assertThat(session).isNotNull();
        // 로그인 응답은 XSRF-TOKEN을 "삭제(빈 값) → 새 값" 순서로 두 번 내려준다. 브라우저처럼 마지막 값을 쓴다.
        Cookie csrfAfterLogin = lastNonEmptyCookie(login, "XSRF-TOKEN");
        assertThat(csrfAfterLogin.getValue()).isNotEqualTo(csrf.getValue());

        mockMvc.perform(get("/api/admin/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(USERNAME));

        mockMvc.perform(withCsrf(post("/api/admin/logout"), csrfAfterLogin).session(session))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void logoutWithoutCsrfIsForbidden() throws Exception {
        mockMvc.perform(post("/api/admin/logout"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
    }

    @Test
    @WithMockUser(username = "mock-admin", roles = "ADMIN")
    void adminRoleCanReadMe() throws Exception {
        mockMvc.perform(get("/api/admin/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("mock-admin"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void nonAdminRoleIsForbiddenOnAdminPaths() throws Exception {
        mockMvc.perform(get("/api/admin/me"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    // ---- 나머지 경로, 기본 설정 제거 ----

    @Test
    void unlistedPathsAreDeniedForAnonymous() throws Exception {
        mockMvc.perform(get("/api/players")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mockMvc.perform(get("/")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void unlistedPathsAreDeniedEvenForAdmin() throws Exception {
        mockMvc.perform(get("/api/players"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void defaultHtmlLoginAndLogoutPagesDoNotExist() throws Exception {
        for (String path : new String[] { "/login", "/logout", "/admin/login" }) {
            MvcResult result = mockMvc.perform(get(path)).andReturn();
            assertThat(result.getResponse().getStatus()).as(path).isEqualTo(401);
            assertThat(result.getResponse().getContentType()).as(path).startsWith("application/problem+json");
            assertThat(result.getResponse().getContentAsString()).as(path).doesNotContain("<html");
        }
    }

    @Test
    void onlyAdminUserDetailsServiceExistsAndNoGeneratedDefaultUser() {
        assertThat(context.getBeansOfType(UserDetailsService.class).values())
                .hasSize(1)
                .allSatisfy(service -> assertThat(service).isInstanceOf(AdminUserDetailsService.class));
        assertThat(context.getBeansOfType(InMemoryUserDetailsManager.class)).isEmpty();
    }
}
