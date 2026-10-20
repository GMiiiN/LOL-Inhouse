package com.lolinhouse.ingest.extract;

import java.time.Duration;

/**
 * 역할: 재시도 사이의 대기를 추상화한다. 운영은 실제로 잠들고({@link #SYSTEM}), 테스트는 기록만 하거나 가짜 시계를 흘려 보내
 * 5·10·20초를 실제로 기다리지 않고 검증한다.
 * 설명: docs/design/m4-design.md D5, §2.1
 */
@FunctionalInterface
public interface Sleeper {

    Sleeper SYSTEM = Thread::sleep;

    /** @throws InterruptedException 스레드가 인터럽트됨(호출자가 플래그를 복구하고 INTERRUPTED로 바꾼다) */
    void sleep(Duration duration) throws InterruptedException;
}
