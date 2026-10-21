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
 * 역할: 기본값(none, 개발): X-Forwarded-For를 무시하고 소켓 주소를 그대로 쓴다.
 * 실제 포트(진짜 Tomcat)로 요청하고, 팀 짜기 분당 제한(TeamMakerGuard)이 받은 주소 키로 접속 주소가 무엇이 됐는지 관찰한다.
 * 테스트 클라이언트는 루프백(Tomcat의 신뢰 프록시 기본 대역)에서 접속한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.admin.username=test-admin",
        "app.admin.password=test-password-1234"
})
@Import(TestcontainersConfiguration.class)
class ForwardedHeadersDefaultTest {

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
    void forwardedForIsIgnored() throws Exception {
        assertThat(addressSeenByServer("203.0.113.7")).isNotEqualTo("203.0.113.7").matches("127[.]0[.]0[.]1|0:0:0:0:0:0:0:1");
    }
}
