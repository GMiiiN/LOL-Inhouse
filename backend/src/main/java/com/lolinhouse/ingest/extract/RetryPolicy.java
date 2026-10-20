package com.lolinhouse.ingest.extract;

import java.time.Duration;

/**
 * 역할: 재시도 규칙 값 묶음. 대기는 baseDelay * 2^(시도-1)에 maxDelay 상한(기본 5·10·20초)이고 지터는 RetryingExtractor가 더한다.
 * 작업 전체 마감(job-deadline)은 여기가 아니라 Deadline이 가진다.
 * 설명: docs/design/m4-design.md D5, §2.4 시간 제한 표
 *
 * @param maxAttempts    일시 오류 재시도를 포함한 총 시도 횟수(app.gemini.max-attempts)
 * @param requestTimeout 요청 하나의 상한(app.gemini.request-timeout). 실제 값은 min(이 값, 작업 마감까지 남은 시간)
 */
public record RetryPolicy(int maxAttempts, Duration baseDelay, Duration maxDelay, Duration requestTimeout) {

    public RetryPolicy {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts는 1 이상이어야 합니다.");
        }
    }

    /** 설계 기본값: 4회, 5초 시작, 60초 상한, 요청 90초. */
    public static RetryPolicy defaults() {
        return new RetryPolicy(4, Duration.ofSeconds(5), Duration.ofSeconds(60), Duration.ofSeconds(90));
    }
}
