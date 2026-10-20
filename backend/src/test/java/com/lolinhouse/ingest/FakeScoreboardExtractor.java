package com.lolinhouse.ingest;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;

import com.lolinhouse.ingest.extract.Attempt;
import com.lolinhouse.ingest.extract.Deadline;
import com.lolinhouse.ingest.extract.ExtractedScoreboard;
import com.lolinhouse.ingest.extract.ExtractionException;
import com.lolinhouse.ingest.extract.ExtractionOutcome;
import com.lolinhouse.ingest.extract.ResilientExtractor;
import com.lolinhouse.ingest.extract.ScoreboardExtractor;

/**
 * 역할: Gemini 없이 추출 결과를 흉내 내는 가짜. 시나리오(결과 반환/예외 던지기)를 차례로 설정하면 호출할 때마다 한 단계씩 소비하고,
 * 시나리오가 바닥나면 마지막 단계를 계속 반복한다. 아무것도 설정하지 않고 부르면 IllegalStateException(설정 누락을 눈에 띄게).
 * 같은 객체가 두 경계를 모두 구현한다:
 * - {@link ScoreboardExtractor}: 한 번의 읽기(작업자 A의 RetryingExtractor 테스트에서 안쪽 추출기로 쓴다)
 * - {@link ResilientExtractor}: 재시도 없이 한 단계를 소비해 바로 결과를 만든다(작업자 B의 실행기 테스트가 A 구현 없이 쓴다)
 *
 * 공유 빈 규칙: TestIngestConfiguration의 @Primary 빈 하나를 컨텍스트 전체 테스트가 같이 쓴다.
 * 시나리오·호출 횟수를 건드리는 테스트는 반드시 @AfterEach에서 {@link #reset()}을 부른다(안 하면 다음 테스트가 오염된다).
 * 설명: docs/design/m4-design.md §7, §11.2-10
 */
public class FakeScoreboardExtractor implements ScoreboardExtractor, ResilientExtractor {

    /** 한 번의 호출이 하는 일: 결과를 돌려주거나 예외를 던진다. */
    @FunctionalInterface
    public interface Step {
        ExtractedScoreboard run() throws ExtractionException;
    }

    private final ConcurrentLinkedDeque<Step> script = new ConcurrentLinkedDeque<>();
    private final AtomicInteger calls = new AtomicInteger();
    private volatile Step lastStep;
    private volatile Runnable beforeCall;
    private volatile Duration lastRequestTimeout;

    /** 다음 호출들이 이 결과를 돌려주게 한다(차례대로 소비, 바닥나면 마지막 단계 반복). */
    public FakeScoreboardExtractor thenReturn(ExtractedScoreboard scoreboard) {
        script.addLast(() -> scoreboard);
        return this;
    }

    /** 다음 호출이 이 예외를 던지게 한다. */
    public FakeScoreboardExtractor thenThrow(ExtractionException exception) {
        script.addLast(() -> {
            throw exception;
        });
        return this;
    }

    /** 호출이 시작될 때마다(시나리오 소비 전에) 실행할 동작. 래치로 호출을 붙잡아 경쟁 상황을 만들 때 쓴다. */
    public FakeScoreboardExtractor beforeEachCall(Runnable action) {
        this.beforeCall = action;
        return this;
    }

    /** 지금까지의 호출 횟수(성공·실패 모두). "Gemini를 0번 불렀다" 단정에 쓴다. */
    public int calls() {
        return calls.get();
    }

    /** 마지막 ScoreboardExtractor 호출에 전달된 요청 제한 시간. */
    public Duration lastRequestTimeout() {
        return lastRequestTimeout;
    }

    /** 테스트가 끝날 때 부른다: 시나리오·훅·호출 횟수를 모두 비운다. */
    public void reset() {
        script.clear();
        lastStep = null;
        beforeCall = null;
        lastRequestTimeout = null;
        calls.set(0);
    }

    @Override
    public ExtractedScoreboard extract(byte[] image, String mimeType, Duration requestTimeout)
            throws ExtractionException {
        lastRequestTimeout = requestTimeout;
        return consume();
    }

    @Override
    public ExtractionOutcome extract(byte[] image, String mimeType, Deadline deadline) throws ExtractionException {
        try {
            ExtractedScoreboard scoreboard = consume();
            return new ExtractionOutcome(scoreboard, List.of(new Attempt(1, null, null, 0, 0)));
        } catch (ExtractionException e) {
            if (!e.attempts().isEmpty()) {
                throw e;
            }
            throw e.withAttempts(List.of(new Attempt(1, e.httpStatus(), e.getClass().getSimpleName(), 0, 0)));
        }
    }

    private ExtractedScoreboard consume() throws ExtractionException {
        calls.incrementAndGet();
        Runnable hook = beforeCall;
        if (hook != null) {
            hook.run();
        }
        Step step = script.pollFirst();
        if (step == null) {
            step = lastStep;
        } else {
            lastStep = step;
        }
        if (step == null) {
            throw new IllegalStateException("FakeScoreboardExtractor에 시나리오가 설정되지 않았습니다.");
        }
        return step.run();
    }
}
