package com.lolinhouse.champion;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 역할: 공식 Data Dragon(https://ddragon.leagueoflegends.com)에서 바이트를 가져오는 운영용 구현. HttpClient는 빈으로 주입받아
 * 연결을 재사용하고 앱 종료 때 정리된다(ChampionConfig). 요청 하나에 제한 시간을 둔다.
 * 경로는 카탈로그가 검증한 버전·id로만 만들지만, 방어로 허용 문자만 통과시킨다. 200이 아니면 IOException.
 * 설명: docs/design/m4-design.md §2.2
 */
@Component
public class HttpDataDragonSource implements DataDragonSource {

    static final String BASE_URL = "https://ddragon.leagueoflegends.com";
    static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);
    private static final Pattern SAFE_PATH = Pattern.compile("/[A-Za-z0-9._/-]+");

    private final HttpClient httpClient;
    private final String baseUrl;

    @Autowired
    public HttpDataDragonSource(HttpClient httpClient) {
        this(httpClient, BASE_URL);
    }

    /** 테스트가 로컬 서버를 가리킬 수 있게 기준 주소를 받는다. */
    HttpDataDragonSource(HttpClient httpClient, String baseUrl) {
        this.httpClient = httpClient;
        this.baseUrl = baseUrl;
    }

    @Override
    public byte[] fetch(String path) throws IOException {
        if (!SAFE_PATH.matcher(path).matches() || path.contains("..")) {
            throw new IOException("허용되지 않은 Data Dragon 경로");
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path)).timeout(REQUEST_TIMEOUT).GET().build();
        try {
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                throw new IOException("Data Dragon HTTP " + response.statusCode());
            }
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("Data Dragon 요청 중 인터럽트");
        }
    }
}
