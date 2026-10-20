package com.lolinhouse.ingest.extract;

/**
 * 역할: 재시도와 작업 전체 마감을 적용한 추출의 진입점. 추출 실행기(작업자 B)는 이 인터페이스에만 의존하고,
 * 구현은 작업자 A의 RetryingExtractor(ScoreboardExtractor를 감쌈)다. B가 A의 구현 없이 가짜(FakeScoreboardExtractor)로 테스트할 수 있게 나눴다.
 * 설명: docs/design/m4-design.md §2.1, §11.2-2·10
 */
public interface ResilientExtractor {

    /**
     * @param deadline 작업 전체 마감. 요청마다 제한 시간은 deadline.requestTimeout(설정값)으로 정한다
     * @return 검증된 결과와 시도 기록
     * @throws ExtractionException 실패(시도 기록은 예외의 attempts()에). 마감 초과·남은 시간 5초 미만은 EXTRACTION_TIMEOUT,
     *                             스레드 인터럽트는 인터럽트 플래그를 복구한 뒤 INTERRUPTED
     */
    ExtractionOutcome extract(byte[] image, String mimeType, Deadline deadline) throws ExtractionException;
}
