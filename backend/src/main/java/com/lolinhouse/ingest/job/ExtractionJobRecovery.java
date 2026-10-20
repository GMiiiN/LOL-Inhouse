package com.lolinhouse.ingest.job;

import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 역할: 앱이 (재)시작할 때 끝나지 못한 QUEUED·RUNNING 작업을 모두 FAILED(INTERRUPTED)로 정리한다. 이어서 실행하지 않는다 —
 * 담당자가 같은 이미지를 다시 붙여넣으면 새 작업이 생긴다. 인스턴스 1대가 전제다(여러 대면 다른 서버의 실행 중 작업까지 끊는다).
 * 설명: docs/design/m4-design.md D4·§9 R10
 */
@Component
public class ExtractionJobRecovery {

    private static final Logger log = LoggerFactory.getLogger(ExtractionJobRecovery.class);

    private final JobTransitions transitions;
    private final Clock clock;

    @Autowired
    public ExtractionJobRecovery(JobTransitions transitions) {
        this(transitions, Clock.systemUTC());
    }

    public ExtractionJobRecovery(JobTransitions transitions, Clock clock) {
        this.transitions = transitions;
        this.clock = clock;
    }

    @EventListener(ApplicationReadyEvent.class)
    public int recover() {
        int count = transitions.failAllUnfinishedAsInterrupted(clock.instant());
        if (count > 0) {
            log.warn("재시작으로 끝나지 못한 추출 작업 {}건을 INTERRUPTED로 정리", count);
        }
        return count;
    }
}
