package com.lolinhouse.auth;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.time.LocalDate;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import com.lolinhouse.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 역할: 브라우저(SPA)가 하는 일을 실제 서버(RANDOM_PORT, 진짜 Tomcat)에 JDK HttpClient로 그대로 재현한다.
 * /api/csrf → 로그인 → 로그인 상태 확인 → 로그아웃 → 이전 세션 거부 → /api/csrf 다시 → 재로그인.
 * 쿠키는 브라우저처럼 Set-Cookie를 읽어 보관했다가 Cookie 헤더로 돌려보내고, X-XSRF-TOKEN 헤더에는 XSRF-TOKEN 쿠키 값을 넣는다.
 * 로그인 상태 확인 자리에서 실제 관리 POST(내전 묶음 생성, JSON 본문 + X-XSRF-TOKEN)도 보낸다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.admin.username=test-admin",
        "app.admin.password=test-password-1234"
})
@Import(TestcontainersConfiguration.class)
class AuthSpaFlowTest {

    @LocalServerPort
    int port;

    final HttpClient client = HttpClient.newHttpClient(); // 리다이렉트를 따라가지 않는다(기본 NEVER)

    /** 브라우저 쿠키 저장소 흉내: 이름 → 값. 값이 비었거나 Max-Age=0이면 삭제로 본다. */
    final Map<String, String> cookieJar = new LinkedHashMap<>();

    HttpResponse<String> send(String method, String path, String formBody, boolean withCsrfHeader,
            Map<String, String> cookies) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (!cookies.isEmpty()) {
            builder.header("Cookie", cookies.entrySet().stream()
                    .map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.joining("; ")));
        }
        if (withCsrfHeader && cookies.containsKey("XSRF-TOKEN")) {
            builder.header("X-XSRF-TOKEN", cookies.get("XSRF-TOKEN"));
        }
        if (formBody != null) {
            // 본문이 '{'로 시작하면 JSON(관리 API), 아니면 form(로그인)으로 보낸다.
            builder.header("Content-Type", formBody.startsWith("{")
                    ? "application/json" : "application/x-www-form-urlencoded");
        }
        builder.method(method, formBody == null ? BodyPublishers.noBody() : BodyPublishers.ofString(formBody));
        return client.send(builder.build(), BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    /** 브라우저처럼 jar의 쿠키를 보내고, 응답의 Set-Cookie를 jar에 반영한다. */
    HttpResponse<String> browser(String method, String path, String formBody) throws Exception {
        HttpResponse<String> response = send(method, path, formBody, true, cookieJar);
        for (String setCookie : response.headers().allValues("set-cookie")) {
            String pair = setCookie.split(";", 2)[0];
            int eq = pair.indexOf('=');
            String name = pair.substring(0, eq);
            String value = pair.substring(eq + 1);
            if (value.isEmpty() || setCookie.toLowerCase().contains("max-age=0")) {
                cookieJar.remove(name);
            } else {
                cookieJar.put(name, value);
            }
        }
        return response;
    }

    static String setCookieOf(HttpResponse<?> response, String name) {
        List<String> all = response.headers().allValues("set-cookie");
        return all.stream().filter(c -> c.startsWith(name + "=")).findFirst()
                .orElseThrow(() -> new AssertionError(name + " Set-Cookie 없음: " + all));
    }

    static String loginBody(String username, String password) {
        return "username=" + URLEncoder.encode(username, StandardCharsets.UTF_8)
                + "&password=" + URLEncoder.encode(password, StandardCharsets.UTF_8);
    }

    @Test
    void fullSpaFlowCsrfLoginMeLogoutOldSessionRejectedRelogin() throws Exception {
        // 1) 시작: XSRF-TOKEN 쿠키 받기
        HttpResponse<String> csrf = browser("GET", "/api/csrf", null);
        assertThat(csrf.statusCode()).isEqualTo(204);
        assertThat(setCookieOf(csrf, "XSRF-TOKEN")).contains("SameSite=Lax").doesNotContain("HttpOnly");
        assertThat(cookieJar).containsKey("XSRF-TOKEN");

        // 2) 로그인: 헤더에 토큰, 본문은 form-urlencoded
        HttpResponse<String> login = browser("POST", "/api/admin/login", loginBody("test-admin", "test-password-1234"));
        assertThat(login.statusCode()).isEqualTo(200);
        assertThat(login.body()).contains("\"username\":\"test-admin\"");
        String sessionCookie = setCookieOf(login, "JSESSIONID");
        assertThat(sessionCookie).contains("HttpOnly").contains("SameSite=Lax").doesNotContain("Secure");
        String firstSessionId = cookieJar.get("JSESSIONID");
        assertThat(firstSessionId).isNotBlank();

        // 3) 로그인 상태에서 보호된 GET과 실제 관리 POST(내전 묶음 생성)
        HttpResponse<String> me = browser("GET", "/api/admin/me", null);
        assertThat(me.statusCode()).isEqualTo(200);
        assertThat(me.body()).contains("\"username\":\"test-admin\"");
        String startDate = LocalDate.of(6000, 1, 1).plusDays(ThreadLocalRandom.current().nextInt(1_000_000)).toString();
        String sessionJson = "{\"startDate\":\"" + startDate + "\"}";
        HttpResponse<String> adminPostWithoutHeader = send("POST", "/api/admin/inhouse-sessions", sessionJson, false, cookieJar);
        assertThat(adminPostWithoutHeader.statusCode()).isEqualTo(403);
        HttpResponse<String> adminPost = browser("POST", "/api/admin/inhouse-sessions", sessionJson);
        assertThat(adminPost.statusCode()).isEqualTo(201);
        assertThat(adminPost.body()).contains("\"startDate\":\"" + startDate + "\"");
        assertThat(adminPost.headers().firstValue("location")).isPresent();

        // 4) 보호된 POST: CSRF 헤더 없으면 403, 있으면 통과(여기서는 로그아웃으로 대신한다)
        HttpResponse<String> logoutWithoutHeader = send("POST", "/api/admin/logout", null, false, cookieJar);
        assertThat(logoutWithoutHeader.statusCode()).isEqualTo(403);
        assertThat(logoutWithoutHeader.body()).contains("CSRF_INVALID");
        HttpResponse<String> logout = browser("POST", "/api/admin/logout", null);
        assertThat(logout.statusCode()).isEqualTo(204);

        // 5) 이전 세션을 그대로 들고 오면 거부
        HttpResponse<String> replay = send("GET", "/api/admin/me", null, false, Map.of("JSESSIONID", firstSessionId));
        assertThat(replay.statusCode()).isEqualTo(401);
        assertThat(replay.headers().firstValue("content-type").orElse("")).startsWith("application/problem+json");
        assertThat(replay.body()).contains("UNAUTHORIZED");

        // 6) 로그아웃은 CSRF 쿠키도 지운다 → /api/csrf를 다시 받고 재로그인
        assertThat(cookieJar).doesNotContainKey("XSRF-TOKEN");
        assertThat(browser("GET", "/api/csrf", null).statusCode()).isEqualTo(204);
        HttpResponse<String> relogin = browser("POST", "/api/admin/login", loginBody("test-admin", "test-password-1234"));
        assertThat(relogin.statusCode()).isEqualTo(200);
        assertThat(cookieJar.get("JSESSIONID")).isNotBlank().isNotEqualTo(firstSessionId);
        assertThat(browser("GET", "/api/admin/me", null).statusCode()).isEqualTo(200);
    }

    @Test
    void wrongPasswordOverRealServerIsLoginFailedWithoutSession() throws Exception {
        browser("GET", "/api/csrf", null);
        HttpResponse<String> login = browser("POST", "/api/admin/login", loginBody("test-admin", "nope"));
        assertThat(login.statusCode()).isEqualTo(401);
        assertThat(login.body()).contains("LOGIN_FAILED");
        assertThat(login.headers().firstValue("location")).isEmpty();
        assertThat(login.headers().allValues("set-cookie")).noneMatch(c -> c.startsWith("JSESSIONID="));
    }

    @Test
    void errorDispatchOnUnknownAdminPathIsNotHiddenBySecurity() throws Exception {
        // 로그인한 담당자가 없는 관리 경로를 부르면 404(ERROR 디스패치도 통과), 익명이면 먼저 401.
        browser("GET", "/api/csrf", null);
        assertThat(browser("GET", "/api/admin/no-such-endpoint", null).statusCode()).isEqualTo(401);
        browser("POST", "/api/admin/login", loginBody("test-admin", "test-password-1234"));
        assertThat(browser("GET", "/api/admin/no-such-endpoint", null).statusCode()).isEqualTo(404);
    }
}
