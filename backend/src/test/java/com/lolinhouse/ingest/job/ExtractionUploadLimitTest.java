package com.lolinhouse.ingest.job;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import com.lolinhouse.TestcontainersConfiguration;
import com.lolinhouse.ingest.FakeScoreboardExtractor;
import com.lolinhouse.ingest.SyntheticScoreboardImage;
import com.lolinhouse.ingest.TestIngestConfiguration;
import com.lolinhouse.session.Duel;
import com.lolinhouse.session.DuelRepository;
import com.lolinhouse.session.InhouseSessionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 역할: 업로드 크기 제한을 MockMvc가 아니라 진짜 Tomcat(RANDOM_PORT)으로 확인한다. MockMvc는 Tomcat의 multipart 파싱과
 * max-swallow-size(상한을 넘은 본문을 읽어 버리는 한도)를 거치지 않아, 큰 파일이 413 대신 연결 끊김으로 끝나는 문제를 못 잡는다.
 * 브라우저처럼 /api/csrf → 로그인 → CSRF 헤더를 단 multipart POST를 보낸다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.admin.username=test-admin",
        "app.admin.password=test-password-1234",
        "GEMINI_API_KEY=test-dummy-key"
})
@Import({TestcontainersConfiguration.class, TestIngestConfiguration.class})
class ExtractionUploadLimitTest {

    @LocalServerPort
    int port;
    @Autowired
    FakeScoreboardExtractor extractor;
    @Autowired
    DuelRepository duels;
    @Autowired
    InhouseSessionRepository sessions;

    final HttpClient client = HttpClient.newHttpClient();
    final Map<String, String> cookies = new LinkedHashMap<>();

    @BeforeEach
    void login() throws Exception {
        send("GET", "/api/csrf", null, null);
        HttpResponse<byte[]> response = send("POST", "/api/admin/login",
                "application/x-www-form-urlencoded", ("username=" + URLEncoder.encode("test-admin", StandardCharsets.UTF_8)
                        + "&password=" + URLEncoder.encode("test-password-1234", StandardCharsets.UTF_8))
                        .getBytes(StandardCharsets.UTF_8));
        assertThat(response.statusCode()).isEqualTo(200);
    }

    @AfterEach
    void resetFake() {
        extractor.reset();
    }

    private HttpResponse<byte[]> send(String method, String path, String contentType, byte[] body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (!cookies.isEmpty()) {
            builder.header("Cookie", cookies.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue())
                    .collect(Collectors.joining("; ")));
        }
        if (cookies.containsKey("XSRF-TOKEN")) {
            builder.header("X-XSRF-TOKEN", cookies.get("XSRF-TOKEN"));
        }
        if (contentType != null) {
            builder.header("Content-Type", contentType);
        }
        builder.method(method, body == null ? BodyPublishers.noBody() : BodyPublishers.ofByteArray(body));
        HttpResponse<byte[]> response = client.send(builder.build(), BodyHandlers.ofByteArray());
        for (String setCookie : response.headers().allValues("set-cookie")) {
            String pair = setCookie.split(";", 2)[0];
            int eq = pair.indexOf('=');
            cookies.put(pair.substring(0, eq), pair.substring(eq + 1));
        }
        return response;
    }

    private HttpResponse<byte[]> uploadImage(long duelId, byte[] image) throws Exception {
        String boundary = "----test" + System.nanoTime();
        ByteArrayOutputStream out = new ByteArrayOutputStream(image.length + 1024);
        field(out, boundary, "topSide", "BLUE");
        field(out, boundary, "capturerPosition", "ADC");
        out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"image\"; filename=\"r.png\"\r\n"
                + "Content-Type: image/png\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(image);
        out.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return send("POST", "/api/admin/duels/" + duelId + "/extractions", "multipart/form-data; boundary=" + boundary,
                out.toByteArray());
    }

    private static void field(ByteArrayOutputStream out, String boundary, String name, String value) throws Exception {
        out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + name + "\"\r\n\r\n" + value
                + "\r\n").getBytes(StandardCharsets.UTF_8));
    }

    private Duel duel() {
        return ExtractionTestSupport.newDuel(sessions, duels);
    }

    @Test
    void fileOverTenMegabytesIs413ImageTooLarge() throws Exception {
        HttpResponse<byte[]> response = uploadImage(duel().id(), new byte[10 * 1024 * 1024 + 1]);

        assertThat(response.statusCode()).isEqualTo(413);
        assertThat(new String(response.body(), StandardCharsets.UTF_8)).contains("\"code\":\"IMAGE_TOO_LARGE\"");
    }

    @Test
    void requestOverTheRequestLimitButUnderSwallowLimitIsAlso413() throws Exception {
        // 11MB(요청 상한)를 넘지만 12MB(max-swallow-size) 아래: Tomcat이 본문을 읽어 버린 뒤 413으로 응답해야 한다.
        HttpResponse<byte[]> response = uploadImage(duel().id(), new byte[11 * 1024 * 1024 + 512 * 1024]);

        assertThat(response.statusCode()).isEqualTo(413);
        assertThat(new String(response.body(), StandardCharsets.UTF_8)).contains("\"code\":\"IMAGE_TOO_LARGE\"");
    }

    @Test
    void normalImageStillUploadsThroughRealTomcat() throws Exception {
        SyntheticScoreboardImage synthetic = SyntheticScoreboardImage.defaultImage();
        extractor.thenReturn(synthetic.scoreboard());

        HttpResponse<byte[]> response = uploadImage(duel().id(), synthetic.png());

        assertThat(response.statusCode()).isEqualTo(202);
        assertThat(response.headers().firstValue("Location")).isPresent();
    }
}
