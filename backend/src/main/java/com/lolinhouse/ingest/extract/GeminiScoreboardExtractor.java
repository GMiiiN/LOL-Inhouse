package com.lolinhouse.ingest.extract;

import java.io.InterruptedIOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.genai.Client;
import com.google.genai.errors.ApiException;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;
import com.google.genai.types.Part;
import com.lolinhouse.ingest.job.ExtractionErrorCode;

/**
 * 역할: google-genai SDK로 결과창 이미지 한 장을 한 번 읽는 어댑터. 구조화 출력(responseJsonSchema)과 박스만 요청하고
 * (챔피언 이름은 요청하지 않는다), 응답 문자열은 {@link ScoreboardResponseValidator}가 검증한다.
 * 재시도는 하지 않는다: SDK 자체 재시도는 끄고(attempts=1) RetryingExtractor가 정책대로 한다.
 * 요청 제한은 인자 requestTimeout을 SDK HttpOptions.timeout(요청 단위)으로 넘긴다(SDK는 이를 OkHttp 호출 전체 제한으로 쓴다).
 * HTTP 오류는 상태로 분류한다: 429는 RATE_LIMITED, 408·5xx는 UPSTREAM_UNAVAILABLE(둘 다 재시도 대상), 그 밖의 4xx는 REJECTED.
 * 응답 없는 시간 초과는 EXTRACTION_TIMEOUT(재시도 대상). 예외 메시지에는 SDK 오류 본문·키를 넣지 않는다.
 * 설명: docs/design/m4-design.md §2.1, §11.3
 */
public final class GeminiScoreboardExtractor implements ScoreboardExtractor, AutoCloseable {

    /** 정답값을 프롬프트에 넣지 않는다(M0 실험 프롬프트 그대로). */
    static final String PROMPT = """
            롤 사용자 설정 게임의 결과창이다. 화면에 보이는 값만 읽고 JSON으로 반환하라.
            top은 위쪽 팀, bottom은 아래쪽 팀이다. rows는 각 팀 화면 순서 그대로 5명이다.
            result는 화면 상단 승리/패배이며 위쪽 팀 결과다.
            date는 YYYY/MM/DD, duration은 경기 소요 시간 mm:ss이며 시각이 아니다.
            nickname은 보이는 문자 그대로, 공백과 일본어·한자와 끝의 …도 유지하라.
            kills,deaths,assists,cs,gold는 정수다. 쉼표는 제거하라.
            팀 kills,deaths,assists,gold는 팀 이름 옆에 표시된 합계다. 행을 더해서 대신 쓰지 마라.
            portraitBox는 챔피언 얼굴 초상화만 꼭 맞게 둘러싸라. 레벨·룬·아이템을 제외하라.
            banBoxes는 각 팀 '선택 금지 + 목표물' 영역에서 사선 있는 작은 회색 챔피언
            밴 아이콘 5개다. 위쪽 줄 왼쪽부터 3개, 그 아래 줄 왼쪽부터 2개 순서다.
            목표물 아이콘과 혼동하지 말고 밴 얼굴만 꼭 맞게 둘러싸라.
            모든 박스는 원본 전체 이미지 기준 [ymin,xmin,ymax,xmax], 0~1000 정규화 정수다.
            이미지 크기와 잘린 여백이 달라져도 실제 위치를 찾아라. 챔피언 이름은 추측하지 마라.
            """;

    private final Client client;
    private final String model;

    public GeminiScoreboardExtractor(Client client, String model) {
        this.client = client;
        this.model = model;
    }

    /** 운영용 생성. SDK 자체 재시도는 끈다(재시도는 RetryingExtractor 한 곳에서). */
    public static GeminiScoreboardExtractor create(String apiKey, String model) {
        Client client = Client.builder().apiKey(apiKey).httpOptions(sdkRetryOff(HttpOptions.builder()).build())
                .build();
        return new GeminiScoreboardExtractor(client, model);
    }

    private static HttpOptions.Builder sdkRetryOff(HttpOptions.Builder builder) {
        return builder.retryOptions(HttpRetryOptions.builder().attempts(1).build());
    }

    @Override
    public ExtractedScoreboard extract(byte[] image, String mimeType, Duration requestTimeout)
            throws ExtractionException {
        GenerateContentResponse response;
        try {
            response = client.models.generateContent(model,
                    Content.fromParts(Part.fromText(PROMPT), Part.fromBytes(image, mimeType)),
                    requestConfig(requestTimeout));
        } catch (RuntimeException e) {
            throw translate(e);
        }
        String text;
        try {
            text = response.text();
        } catch (RuntimeException e) {
            // 응답 본문을 읽는 중의 예외. 원문을 남기지 않도록 클래스 이름만 안다.
            throw new ExtractionException(ExtractionErrorCode.EXTRACTION_INVALID_RESPONSE,
                    "Gemini 응답을 읽지 못했습니다(" + e.getClass().getSimpleName() + ").");
        }
        return ScoreboardResponseValidator.validate(text);
    }

    /** 요청마다 달라지는 부분(요청 제한)과 고정 부분(스키마·온도)을 합친 SDK 설정. */
    static GenerateContentConfig requestConfig(Duration requestTimeout) {
        int timeoutMillis = (int) Math.min(Integer.MAX_VALUE, Math.max(1, requestTimeout.toMillis()));
        return GenerateContentConfig.builder()
                .responseMimeType("application/json")
                .responseJsonSchema(schema())
                .temperature(0.0f)
                .httpOptions(sdkRetryOff(HttpOptions.builder().timeout(timeoutMillis)).build())
                .build();
    }

    private static Map<String, Object> integer() {
        return Map.of("type", "integer", "minimum", 0);
    }

    private static Map<String, Object> object(Map<String, Object> properties) {
        return Map.of("type", "object", "properties", properties, "required", new ArrayList<>(properties.keySet()));
    }

    private static Map<String, Object> array(Object items, int size) {
        return Map.of("type", "array", "items", items, "minItems", size, "maxItems", size);
    }

    /** 좌표는 공식 문서의 [ymin,xmin,ymax,xmax], 0~1000이다. */
    static Map<String, Object> schema() {
        var box = array(Map.of("type", "integer", "minimum", 0, "maximum", 1000), 4);
        var row = object(Map.of("nickname", Map.of("type", "string"), "kills", integer(), "deaths", integer(),
                "assists", integer(), "cs", integer(), "gold", integer(), "portraitBox", box));
        var team = object(Map.of("kills", integer(), "deaths", integer(), "assists", integer(), "gold", integer(),
                "rows", array(row, 5), "banBoxes", array(box, 5)));
        return object(Map.of("result", Map.of("type", "string", "enum", List.of("승리", "패배")),
                "date", Map.of("type", "string"), "duration", Map.of("type", "string"), "top", team,
                "bottom", team));
    }

    /** SDK 예외를 작업 오류 코드로 바꾼다. 상태 숫자와 종류만 쓰고 SDK의 오류 본문은 버린다. */
    static ExtractionException translate(RuntimeException e) {
        if (e instanceof ApiException api) {
            int status = api.code();
            if (status == 429) {
                return new ExtractionException(ExtractionErrorCode.EXTRACTION_RATE_LIMITED, status, true,
                        "Gemini 요청 한도 초과(HTTP 429).");
            }
            if (status == 408 || status >= 500) {
                return new ExtractionException(ExtractionErrorCode.EXTRACTION_UPSTREAM_UNAVAILABLE, status, true,
                        "Gemini 서버 일시 오류(HTTP " + status + ").");
            }
            return new ExtractionException(ExtractionErrorCode.EXTRACTION_REJECTED, status, false,
                    "Gemini가 요청을 거절했습니다(HTTP " + status + ").");
        }
        if (Thread.currentThread().isInterrupted() || hasCause(e, InterruptedException.class)) {
            return new ExtractionException(ExtractionErrorCode.INTERRUPTED, "추출 중 인터럽트되었습니다.");
        }
        if (hasCause(e, InterruptedIOException.class)) {
            // OkHttp의 호출 제한(callTimeout)·소켓 시간 초과는 InterruptedIOException 계열이다
            return new ExtractionException(ExtractionErrorCode.EXTRACTION_TIMEOUT, null, true,
                    "Gemini 응답 시간 초과.");
        }
        return new ExtractionException(ExtractionErrorCode.EXTRACTION_UPSTREAM_UNAVAILABLE,
                "Gemini에 연결하지 못했습니다.");
    }

    private static boolean hasCause(Throwable error, Class<? extends Throwable> type) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (type.isInstance(current)) {
                return true;
            }
            if (current.getCause() == current) {
                break;
            }
        }
        return false;
    }

    /** SDK 연결을 닫는다. */
    @Override
    public void close() {
        client.close();
    }
}
