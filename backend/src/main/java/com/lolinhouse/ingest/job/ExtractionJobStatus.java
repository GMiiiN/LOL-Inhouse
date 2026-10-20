package com.lolinhouse.ingest.job;

/**
 * 역할: 추출 작업의 상태. 전이는 QUEUED → RUNNING → SUCCEEDED|FAILED 한 방향이고 FAILED는 끝이다(재시도는 새 작업).
 * 만료(EXPIRED)·대체(SUPERSEDED) 같은 이유는 상태가 아니라 {@link ExtractionErrorCode}로 구분한다.
 * 설명: docs/design/m4-design.md §2.4
 */
public enum ExtractionJobStatus {
    QUEUED,
    RUNNING,
    SUCCEEDED,
    FAILED
}
