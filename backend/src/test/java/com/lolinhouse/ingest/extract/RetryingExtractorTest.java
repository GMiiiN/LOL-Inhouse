package com.lolinhouse.ingest.extract;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.lolinhouse.ingest.FakeScoreboardExtractor;
import com.lolinhouse.ingest.SyntheticScoreboardImage;
import com.lolinhouse.ingest.job.ExtractionErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 재시도 규칙 시험. 가짜 시계·가짜 Sleeper로 실제 대기 없이 5·10·20초를 검증한다. */
class RetryingExtractorTest {

    private static final long JITTER = 123;

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-17T00:00:00Z"));
    private final FakeScoreboardExtractor fake = new FakeScoreboardExtractor();
    private final List<Duration> sleeps = new ArrayList<>();
    private final ExtractedScoreboard scoreboard = SyntheticScoreboardImage.defaultImage().scoreboard();
    private Deadline deadline;
    private Sleeper sleeper;

    @BeforeEach
    void setUp() {
        deadline = Deadline.of(clock, clock.instant(), Duration.ofMinutes(5));
        sleeper = duration -> {
            sleeps.add(duration);
            clock.advance(duration);
        };
    }

    @AfterEach
    void tearDown() {
        fake.reset();
        Thread.interrupted();
    }

    private RetryingExtractor extractor(Sleeper useSleeper) {
        return new RetryingExtractor(fake, RetryPolicy.defaults(), useSleeper, clock, () -> JITTER);
    }

    private static ExtractionException failure(ExtractionErrorCode code, int status, boolean transientError) {
        return new ExtractionException(code, status, transientError, "실패");
    }

    @Test
    void retriesTransientErrorsWithBackoffAndReturnsAttempts() throws Exception {
        ExtractionException busy = failure(ExtractionErrorCode.EXTRACTION_UPSTREAM_UNAVAILABLE, 503, true);
        fake.thenThrow(busy).thenThrow(busy).thenReturn(scoreboard);

        ExtractionOutcome outcome = extractor(sleeper).extract(new byte[0], "image/png", deadline);

        assertThat(outcome.scoreboard()).isEqualTo(scoreboard);
        assertThat(fake.calls()).isEqualTo(3);
        assertThat(sleeps).containsExactly(Duration.ofMillis(5000 + JITTER), Duration.ofMillis(10000 + JITTER));
        assertThat(outcome.attempts()).extracting(Attempt::number).containsExactly(1, 2, 3);
        assertThat(outcome.attempts()).extracting(Attempt::httpStatus).containsExactly(503, 503, null);
        assertThat(outcome.attempts()).extracting(Attempt::backoffMs).containsExactly(5123L, 10123L, 0L);
    }

    @Test
    void permanentErrorIsNotRetried() {
        fake.thenThrow(failure(ExtractionErrorCode.EXTRACTION_REJECTED, 400, false));

        assertThatThrownBy(() -> extractor(sleeper).extract(new byte[0], "image/png", deadline))
                .isInstanceOfSatisfying(ExtractionException.class, e -> {
                    assertThat(e.errorCode()).isEqualTo(ExtractionErrorCode.EXTRACTION_REJECTED);
                    assertThat(e.attempts()).hasSize(1);
                });
        assertThat(fake.calls()).isEqualTo(1);
        assertThat(sleeps).isEmpty();
    }

    @Test
    void stopsAfterFourRateLimitedAttempts() {
        fake.thenThrow(failure(ExtractionErrorCode.EXTRACTION_RATE_LIMITED, 429, true));

        assertThatThrownBy(() -> extractor(sleeper).extract(new byte[0], "image/png", deadline))
                .isInstanceOfSatisfying(ExtractionException.class, e -> {
                    assertThat(e.errorCode()).isEqualTo(ExtractionErrorCode.EXTRACTION_RATE_LIMITED);
                    assertThat(e.attempts()).hasSize(4);
                });
        assertThat(fake.calls()).isEqualTo(4);
        assertThat(sleeps).hasSize(3);
        assertThat(sleeps.get(2)).isEqualTo(Duration.ofMillis(20000 + JITTER));
    }

    @Test
    void passesMinOfConfiguredAndRemainingTimeAsRequestTimeout() throws Exception {
        fake.thenReturn(scoreboard);
        clock.advance(Duration.ofSeconds(250));

        extractor(sleeper).extract(new byte[0], "image/png", deadline);

        assertThat(fake.lastRequestTimeout()).isEqualTo(Duration.ofSeconds(50));
    }

    @Test
    void doesNotStartWhenLessThanFiveSecondsRemain() {
        fake.thenReturn(scoreboard);
        clock.advance(Duration.ofSeconds(297));

        assertThatThrownBy(() -> extractor(sleeper).extract(new byte[0], "image/png", deadline))
                .isInstanceOfSatisfying(ExtractionException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ExtractionErrorCode.EXTRACTION_TIMEOUT));
        assertThat(fake.calls()).isZero();
    }

    @Test
    void timesOutWhenNextBackoffWouldCrossTheDeadline() {
        fake.thenThrow(failure(ExtractionErrorCode.EXTRACTION_UPSTREAM_UNAVAILABLE, 503, true));
        Deadline shortDeadline = Deadline.of(clock, clock.instant(), Duration.ofSeconds(9));

        assertThatThrownBy(() -> extractor(sleeper).extract(new byte[0], "image/png", shortDeadline))
                .isInstanceOfSatisfying(ExtractionException.class, e -> {
                    assertThat(e.errorCode()).isEqualTo(ExtractionErrorCode.EXTRACTION_TIMEOUT);
                    assertThat(e.attempts()).hasSize(1);
                });
        assertThat(fake.calls()).isEqualTo(1);
        assertThat(sleeps).isEmpty();
    }

    @Test
    void interruptDuringBackoffRestoresFlagAndReportsInterrupted() {
        fake.thenThrow(failure(ExtractionErrorCode.EXTRACTION_UPSTREAM_UNAVAILABLE, 503, true));
        Sleeper interrupting = duration -> {
            throw new InterruptedException();
        };

        assertThatThrownBy(() -> extractor(interrupting).extract(new byte[0], "image/png", deadline))
                .isInstanceOfSatisfying(ExtractionException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ExtractionErrorCode.INTERRUPTED));
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
    }

    @Test
    void unexpectedRuntimeExceptionBecomesInternalErrorWithoutRetry() {
        ScoreboardExtractor broken = (image, mimeType, timeout) -> {
            throw new IllegalStateException("비밀 메시지");
        };
        RetryingExtractor retrying = new RetryingExtractor(broken, RetryPolicy.defaults(), sleeper, Clock.systemUTC(),
                () -> 0);
        Deadline real = Deadline.of(Clock.systemUTC(), Instant.now(), Duration.ofMinutes(5));

        assertThatThrownBy(() -> retrying.extract(new byte[0], "image/png", real))
                .isInstanceOfSatisfying(ExtractionException.class, e -> {
                    assertThat(e.errorCode()).isEqualTo(ExtractionErrorCode.INTERNAL_ERROR);
                    assertThat(e.getMessage()).doesNotContain("비밀");
                    assertThat(e.attempts().get(0).errorType()).isEqualTo("IllegalStateException");
                });
    }
}
