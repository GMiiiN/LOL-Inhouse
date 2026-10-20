package com.lolinhouse.ingest.extract;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * 역할: 작업 하나의 전체 마감 시각(RUNNING 선점 시각 + job-deadline). 카탈로그 읽기·추출·매칭 전체가 이 마감 하나를 공유한다.
 * 시계를 주입받아 테스트에서 시간을 흘려 보낼 수 있다.
 * 설명: docs/design/m4-design.md §11.2-2
 */
public final class Deadline {

    /** 남은 시간이 이보다 짧으면 새 요청을 시작하지 않는다. */
    public static final Duration MIN_REQUEST_TIME = Duration.ofSeconds(5);

    private final Clock clock;
    private final Instant at;

    private Deadline(Clock clock, Instant at) {
        this.clock = clock;
        this.at = at;
    }

    /** startedAt에서 total만큼 뒤가 마감인 Deadline. */
    public static Deadline of(Clock clock, Instant startedAt, Duration total) {
        return new Deadline(clock, startedAt.plus(total));
    }

    public Instant at() {
        return at;
    }

    /** 남은 시간. 이미 지났으면 0(음수를 돌려주지 않는다). */
    public Duration remaining() {
        Duration left = Duration.between(clock.instant(), at);
        return left.isNegative() ? Duration.ZERO : left;
    }

    public boolean isExpired() {
        return !clock.instant().isBefore(at);
    }

    /** 새 요청을 시작해도 되는가(남은 시간 >= {@link #MIN_REQUEST_TIME}). */
    public boolean canStartRequest() {
        return remaining().compareTo(MIN_REQUEST_TIME) >= 0;
    }

    /** 요청 하나에 줄 제한 시간 = min(설정값, 남은 시간). */
    public Duration requestTimeout(Duration configured) {
        Duration left = remaining();
        return left.compareTo(configured) < 0 ? left : configured;
    }
}
