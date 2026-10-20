package com.lolinhouse.ingest.extract;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 역할: 추출 빈 조립. API 키(환경변수 GEMINI_API_KEY)가 있으면 Gemini 어댑터, 없으면 항상 EXTRACTION_NOT_CONFIGURED로 실패하는
 * 비활성 추출기를 만든다 — 키 없는 CI·로컬에서도 기동은 성공해야 하기 때문이다. 키는 @Value로만 읽고(설정 클래스의 속성으로
 * 받지 않아 로그·직렬화로 새지 않게 한다) 어디에도 남기지 않는다. 재시도 정책은 app.gemini.* 설정에서 읽는다.
 * 설명: docs/design/m4-design.md §2.1, §2.4 시간 제한 표
 */
@Configuration(proxyBeanMethods = false)
public class ExtractorConfig {

    /** 재시도 사이 대기에 더하는 지터의 상한(미포함, ms). */
    private static final long JITTER_BOUND_MILLIS = 1000;

    @Bean
    ScoreboardExtractor scoreboardExtractor(@Value("${GEMINI_API_KEY:}") String apiKey,
            @Value("${app.gemini.model}") String model) {
        if (apiKey == null || apiKey.isBlank()) {
            return new DisabledScoreboardExtractor();
        }
        return GeminiScoreboardExtractor.create(apiKey, model);
    }

    @Bean
    ResilientExtractor resilientExtractor(ScoreboardExtractor scoreboardExtractor,
            @Value("${app.gemini.max-attempts}") int maxAttempts,
            @Value("${app.gemini.request-timeout}") Duration requestTimeout) {
        RetryPolicy defaults = RetryPolicy.defaults();
        RetryPolicy policy = new RetryPolicy(maxAttempts, defaults.baseDelay(), defaults.maxDelay(), requestTimeout);
        return new RetryingExtractor(scoreboardExtractor, policy, Sleeper.SYSTEM, Clock.systemUTC(),
                () -> ThreadLocalRandom.current().nextLong(JITTER_BOUND_MILLIS));
    }
}
