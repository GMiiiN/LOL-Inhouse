package com.lolinhouse.teammaker;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;

import com.lolinhouse.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/**
 * 역할: forward-headers-strategy=native: 신뢰 프록시(루프백)가 보낸 X-Forwarded-For가 접속 주소가 되고, 여러 값이면 신뢰 대역을 건너뛴 가장 오른쪽 주소를 쓴다(왼쪽의 가짜 값은 무시).
 * 실제 포트(진짜 Tomcat)로 요청하고, 팀 짜기 분당 제한(TeamMakerGuard)이 받은 주소 키로 접속 주소가 무엇이 됐는지 관찰한다.
 * 테스트 클라이언트는 루프백(Tomcat의 신뢰 프록시 기본 대역)에서 접속한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.admin.username=test-admin",
        "app.admin.password=test-password-1234",
        "server.forward-headers-strategy=native"
})
@Import(TestcontainersConfiguration.class)
class ForwardedHeadersNativeTest {

    @LocalServerPort
    int port;

    @MockitoSpyBean
    TeamMakerGuard guard;

    final HttpClient client = HttpClient.newHttpClient();

    /** CSRF 쿠키를 받은 뒤 빈 본문 POST를 보낸다(요청 검증 전에 제한 검사가 먼저 도므로 주소 키만 남는다). 서버가 본 주소를 돌려준다. */
    String addressSeenByServer(String forwardedFor) throws Exception {
        String base = "http://localhost:" + port;
        HttpResponse<String> csrf = client.send(HttpRequest.newBuilder(URI.create(base + "/api/csrf")).build(),
                BodyHandlers.ofString());
        String token = csrf.headers().allValues("set-cookie").stream()
                .filter(c -> c.startsWith("XSRF-TOKEN=")).findFirst().orElseThrow().split(";", 2)[0].substring(11);
        HttpRequest.Builder post = HttpRequest.newBuilder(URI.create(base + "/api/team-maker/auto"))
                .header("Content-Type", "application/json")
                .header("Cookie", "XSRF-TOKEN=" + token)
                .header("X-XSRF-TOKEN", token)
                .POST(BodyPublishers.ofString("{}"));
        if (forwardedFor != null) {
            post.header("X-Forwarded-For", forwardedFor);
        }
        client.send(post.build(), BodyHandlers.ofString());
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(guard, org.mockito.Mockito.atLeastOnce()).checkRate(captor.capture());
        return captor.getValue();
    }

    @Test
    void trustedProxyForwardedForBecomesRemoteAddress() throws Exception {
        assertThat(addressSeenByServer("203.0.113.7")).isEqualTo("203.0.113.7");
    }

    @Test
    void spoofedLeftmostValueIsIgnored() throws Exception {
        assertThat(addressSeenByServer("6.6.6.6, 203.0.113.8")).isEqualTo("203.0.113.8");
    }
}
