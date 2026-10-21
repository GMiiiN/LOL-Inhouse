package com.lolinhouse.ingest.extract;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.google.genai.Client;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;
import com.lolinhouse.ingest.SyntheticScoreboardImage;
import com.lolinhouse.ingest.job.ExtractionErrorCode;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 어댑터 시험. 실제 Gemini는 부르지 않고, 127.0.0.1의 가짜 HTTP 서버를 SDK 기준 주소로 지정해
 * 요청 구성(모델 경로·스키마·온도)과 응답/오류 변환을 확인한다.
 */
class GeminiScoreboardExtractorTest {

    private HttpServer server;
    private GeminiScoreboardExtractor extractor;
    private final AtomicInteger requests = new AtomicInteger();
    private final AtomicReference<String> lastPath = new AtomicReference<>();
    private final AtomicReference<String> lastBody = new AtomicReference<>();
    private volatile int status = 200;
    private volatile String responseBody = "";
    private volatile long delayMillis = 0;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requests.incrementAndGet();
            lastPath.set(exchange.getRequestURI().getPath());
            lastBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            try {
                Thread.sleep(delayMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            try {
                exchange.sendResponseHeaders(status, body.length);
                exchange.getResponseBody().write(body);
            } catch (IOException ignored) {
                // 클라이언트가 시간 초과로 먼저 끊은 경우
            } finally {
                exchange.close();
            }
        });
        server.start();
        Client client = Client.builder().apiKey("test-key-not-real").httpOptions(HttpOptions.builder()
                .baseUrl("http://127.0.0.1:" + server.getAddress().getPort())
                .retryOptions(HttpRetryOptions.builder().attempts(1).build()).build()).build();
        extractor = new GeminiScoreboardExtractor(client, "test-model");
    }

    @AfterEach
    void stop() {
        extractor.close();
        server.stop(0);
    }

    private static String geminiEnvelope(String text) {
        String escaped = text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
        return "{\"candidates\":[{\"content\":{\"role\":\"model\",\"parts\":[{\"text\":\"" + escaped
                + "\"}]},\"finishReason\":\"STOP\"}]}";
    }

    private static String validJson() {
        StringBuilder team = new StringBuilder();
        team.append("{\"kills\":1,\"deaths\":2,\"assists\":3,\"gold\":4,\"rows\":[");
        for (int i = 0; i < 5; i++) {
            team.append(i > 0 ? "," : "").append("{\"nickname\":\"n").append(i)
                    .append("\",\"kills\":0,\"deaths\":0,\"assists\":0,\"cs\":0,\"gold\":0,"
                            + "\"portraitBox\":[10,10,20,20]}");
        }
        team.append("],\"banBoxes\":[[1,1,2,2],[1,1,2,2],[1,1,2,2],[1,1,2,2],[1,1,2,2]]}");
        return "{\"result\":\"패배\",\"date\":\"2026/10/12\",\"duration\":\"25:42\",\"top\":" + team + ",\"bottom\":"
                + team + "}";
    }

    @Test
    void 프롬프트와_스키마가_닉네임_번역과_음역을_금지한다() {
        assertThat(GeminiScoreboardExtractor.PROMPT).contains("일본어 가나·한자").contains("번역·음역·한글 변환")
                .contains("…");
        assertThat(GeminiScoreboardExtractor.schema().toString())
                .contains(GeminiScoreboardExtractor.NICKNAME_DESCRIPTION);
    }

    @Test
    void sendsModelPathSchemaAndZeroTemperatureThenParsesTheStructuredAnswer() throws Exception {
        responseBody = geminiEnvelope(validJson());

        ExtractedScoreboard result = extractor.extract(SyntheticScoreboardImage.defaultImage().png(), "image/png",
                Duration.ofSeconds(30));

        assertThat(result.topResult()).isEqualTo(ExtractedScoreboard.TopResult.LOSS);
        assertThat(result.top().rows()).hasSize(5);
        assertThat(lastPath.get()).contains("test-model").contains("generateContent");
        assertThat(lastBody.get()).contains("responseJsonSchema").contains("banBoxes").contains("\"temperature\":0")
                .contains("image/png");
        assertThat(lastBody.get()).doesNotContain("test-key-not-real");
    }

    @Test
    void requestConfigCarriesTimeoutAndDisablesSdkRetry() {
        GenerateContentConfig config = GeminiScoreboardExtractor.requestConfig(Duration.ofSeconds(42));

        HttpOptions options = config.httpOptions().orElseThrow();
        assertThat(options.timeout()).contains(42_000);
        assertThat(options.retryOptions().orElseThrow().attempts()).contains(1);
        assertThat(config.temperature()).contains(0.0f);
        assertThat(config.responseMimeType()).contains("application/json");
        assertThat(config.responseJsonSchema()).isPresent();
    }

    @Test
    void mapsHttpStatusesToErrorCodesAndRetryability() {
        assertFailure(429, ExtractionErrorCode.EXTRACTION_RATE_LIMITED, true);
        assertFailure(503, ExtractionErrorCode.EXTRACTION_UPSTREAM_UNAVAILABLE, true);
        assertFailure(408, ExtractionErrorCode.EXTRACTION_UPSTREAM_UNAVAILABLE, true);
        assertFailure(400, ExtractionErrorCode.EXTRACTION_REJECTED, false);
        assertFailure(403, ExtractionErrorCode.EXTRACTION_REJECTED, false);
    }

    private void assertFailure(int httpStatus, ExtractionErrorCode code, boolean transientError) {
        int before = requests.get();
        status = httpStatus;
        responseBody = "{\"error\":{\"code\":" + httpStatus + ",\"message\":\"SECRET-BODY\",\"status\":\"X\"}}";
        assertThatThrownBy(() -> extractor.extract(new byte[] {1}, "image/png", Duration.ofSeconds(10)))
                .isInstanceOfSatisfying(ExtractionException.class, e -> {
                    assertThat(e.errorCode()).isEqualTo(code);
                    assertThat(e.httpStatus()).isEqualTo(httpStatus);
                    assertThat(e.transientError()).isEqualTo(transientError);
                    assertThat(e.getMessage()).doesNotContain("SECRET-BODY");
                });
        // 408은 OkHttp가 연결 수준에서 한 번 더 보낸다(SDK 재시도 설정과 무관). 그 밖의 상태는 정확히 1번
        assertThat(requests.get() - before).as("HTTP " + httpStatus).isEqualTo(httpStatus == 408 ? 2 : 1);
    }

    @Test
    void slowServerBecomesTransientTimeoutWithoutSdkRetry() {
        responseBody = geminiEnvelope(validJson());
        delayMillis = 3000;

        assertThatThrownBy(() -> extractor.extract(new byte[] {1}, "image/png", Duration.ofMillis(500)))
                .isInstanceOfSatisfying(ExtractionException.class, e -> {
                    assertThat(e.errorCode()).isEqualTo(ExtractionErrorCode.EXTRACTION_TIMEOUT);
                    assertThat(e.transientError()).isTrue();
                });
        assertThat(requests.get()).isEqualTo(1);
    }

    @Test
    void malformedModelAnswerIsInvalidResponse() {
        responseBody = geminiEnvelope("{\"result\":\"승리\"}");

        assertThatThrownBy(() -> extractor.extract(new byte[] {1}, "image/png", Duration.ofSeconds(10)))
                .isInstanceOfSatisfying(ExtractionException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ExtractionErrorCode.EXTRACTION_INVALID_RESPONSE));
    }

    @Test
    void disabledExtractorAlwaysReportsNotConfigured() {
        assertThatThrownBy(() -> new DisabledScoreboardExtractor().extract(new byte[0], "image/png", Duration.ofSeconds(1)))
                .isInstanceOfSatisfying(ExtractionException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ExtractionErrorCode.EXTRACTION_NOT_CONFIGURED));
    }
}
