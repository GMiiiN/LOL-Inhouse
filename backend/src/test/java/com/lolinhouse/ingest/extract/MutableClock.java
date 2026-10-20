package com.lolinhouse.ingest.extract;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

/** 테스트용 시계: advance()로만 흐른다. extract·champion 테스트가 함께 쓴다. */
public final class MutableClock extends Clock {

    private final AtomicReference<Instant> now;

    public MutableClock(Instant start) {
        this.now = new AtomicReference<>(start);
    }

    public void advance(Duration duration) {
        now.updateAndGet(current -> current.plus(duration));
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }

    @Override
    public Instant instant() {
        return now.get();
    }
}
