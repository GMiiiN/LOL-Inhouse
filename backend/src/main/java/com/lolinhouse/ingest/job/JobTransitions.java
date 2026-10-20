package com.lolinhouse.ingest.job;

import java.time.Instant;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 역할: 작업 상태 전이(조건부 UPDATE)를 "짧은 트랜잭션 하나"로 실행하는 공용 도우미. 선점·성공·실패·만료·복구 모두 WHERE 상태 조건이 붙어 있어
 * 반환값(갱신된 행 수)이 0이면 다른 쪽이 먼저 상태를 바꾼 것이다 → 호출자는 자기 결과를 버린다.
 * 실행기 전체를 하나의 트랜잭션으로 감싸지 않기 위해(Gemini 호출 동안 DB 연결을 잡지 않으려고) 전이마다 따로 커밋한다.
 * 설명: docs/design/m4-design.md §11.2-4
 */
@Component
public class JobTransitions {

    private final ExtractionJobRepository jobs;
    private final TransactionTemplate tx;

    public JobTransitions(ExtractionJobRepository jobs, PlatformTransactionManager transactionManager) {
        this.jobs = jobs;
        this.tx = new TransactionTemplate(transactionManager);
    }

    int claimRunning(Long jobId, Instant now) {
        return tx.execute(status -> jobs.claimRunning(jobId, now));
    }

    int succeed(Long jobId, String resultJson, int attemptCount, Instant now) {
        return tx.execute(status -> jobs.succeed(jobId, resultJson, attemptCount, now));
    }

    int fail(Long jobId, ExtractionErrorCode code, int attemptCount, Instant now) {
        return tx.execute(status -> jobs.fail(jobId, code, attemptCount, now));
    }

    int expireStaleRunning(Long jobId, Instant startedBefore, Instant now) {
        return tx.execute(status -> jobs.expireStaleRunning(jobId, startedBefore, now));
    }

    int expireQueued(Long jobId, Instant createdBefore, Instant now) {
        return tx.execute(status -> jobs.expireQueued(jobId, createdBefore, now));
    }

    int failAllUnfinishedAsInterrupted(Instant now) {
        return tx.execute(status -> jobs.failAllUnfinishedAsInterrupted(now));
    }
}
