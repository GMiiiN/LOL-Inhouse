package com.lolinhouse.ingest.extract;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongSupplier;

import com.lolinhouse.ingest.job.ExtractionErrorCode;

/**
 * 역할: {@link ScoreboardExtractor}를 감싸 재시도와 작업 전체 마감을 적용한다. 호출 한 번이 일시 오류(408·429·5xx·응답 없는
 * 시간 초과)로 실패하면 5·10·20초(+지터 0~999ms)를 기다려 다시 부르고, 총 maxAttempts번까지만 시도한다.
 * 마감(Deadline)에 새 요청을 시작할 5초가 남지 않았거나 다음 대기가 마감을 넘기면 EXTRACTION_TIMEOUT.
 * 대기는 Sleeper, 시각은 Clock, 지터는 LongSupplier를 주입받아 테스트에서 실제로 기다리지 않는다.
 * 시도 기록에는 응답 원문·메시지를 담지 않는다(상태, 오류 코드 이름, 지연 시간만).
 * 설명: docs/design/m4-design.md D5, §2.1, §11.2-2
 */
public final class RetryingExtractor implements ResilientExtractor {

    private final ScoreboardExtractor delegate;
    private final RetryPolicy policy;
    private final Sleeper sleeper;
    private final Clock clock;
    private final LongSupplier jitterMillis;

    public RetryingExtractor(ScoreboardExtractor delegate, RetryPolicy policy, Sleeper sleeper, Clock clock,
            LongSupplier jitterMillis) {
        this.delegate = delegate;
        this.policy = policy;
        this.sleeper = sleeper;
        this.clock = clock;
        this.jitterMillis = jitterMillis;
    }

    @Override
    public ExtractionOutcome extract(byte[] image, String mimeType, Deadline deadline) throws ExtractionException {
        List<Attempt> attempts = new ArrayList<>();
        for (int number = 1; number <= policy.maxAttempts(); number++) {
            if (!deadline.canStartRequest()) {
                throw timeout("작업 제한 시간이 모자라 요청을 시작하지 못했습니다.").withAttempts(attempts);
            }
            Instant started = clock.instant();
            ExtractionException failure;
            String errorType;
            try {
                ExtractedScoreboard scoreboard = delegate.extract(image, mimeType,
                        deadline.requestTimeout(policy.requestTimeout()));
                attempts.add(new Attempt(number, null, null, elapsedMillis(started), 0));
                return new ExtractionOutcome(scoreboard, attempts);
            } catch (ExtractionException e) {
                failure = e;
                errorType = e.errorCode().name();
            } catch (RuntimeException e) {
                failure = new ExtractionException(ExtractionErrorCode.INTERNAL_ERROR, "추출 중 예기치 못한 오류.");
                errorType = e.getClass().getSimpleName();
            }
            long latency = elapsedMillis(started);

            if (!failure.transientError() || number == policy.maxAttempts()) {
                attempts.add(new Attempt(number, failure.httpStatus(), errorType, latency, 0));
                throw failure.withAttempts(attempts);
            }
            long delayMillis = backoffMillis(number) + jitterMillis.getAsLong();
            if (deadline.remaining().minusMillis(delayMillis).compareTo(Deadline.MIN_REQUEST_TIME) < 0) {
                attempts.add(new Attempt(number, failure.httpStatus(), errorType, latency, 0));
                throw timeout("다음 시도까지 기다리면 작업 제한 시간을 넘습니다.").withAttempts(attempts);
            }
            attempts.add(new Attempt(number, failure.httpStatus(), errorType, latency, delayMillis));
            try {
                sleeper.sleep(Duration.ofMillis(delayMillis));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ExtractionException(ExtractionErrorCode.INTERRUPTED, "대기 중 인터럽트되었습니다.")
                        .withAttempts(attempts);
            }
        }
        throw new IllegalStateException("재시도 반복이 끝났는데 결과가 없습니다.");
    }

    /** baseDelay * 2^(number-1)을 maxDelay로 자른 값. 5·10·20초... */
    private long backoffMillis(int number) {
        long doubled = policy.baseDelay().toMillis() << Math.min(number - 1, 20);
        return Math.min(policy.maxDelay().toMillis(), doubled);
    }

    private long elapsedMillis(Instant started) {
        return Duration.between(started, clock.instant()).toMillis();
    }

    private static ExtractionException timeout(String message) {
        return new ExtractionException(ExtractionErrorCode.EXTRACTION_TIMEOUT, message);
    }
}
