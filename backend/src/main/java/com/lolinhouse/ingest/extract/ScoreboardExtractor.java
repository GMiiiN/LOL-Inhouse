package com.lolinhouse.ingest.extract;

import java.time.Duration;

/**
 * 역할: 결과창 이미지 한 장을 한 번 읽는 경계(모델 공급자를 바꿀 때의 교체점). 재시도는 하지 않는다 —
 * 재시도·마감은 {@link ResilientExtractor} 구현(RetryingExtractor)이 이 인터페이스를 감싸서 한다.
 * 구현: GeminiScoreboardExtractor, DisabledScoreboardExtractor(작업자 A), 테스트 FakeScoreboardExtractor.
 * 설명: docs/design/m4-design.md §2.1
 */
public interface ScoreboardExtractor {

    /**
     * 이미지에서 검증을 통과한 결과를 만든다.
     *
     * @param requestTimeout 이 호출 하나의 제한 시간(= min(설정, 작업 마감까지 남은 시간)). 구현은 SDK 요청 제한으로 쓴다
     * @throws ExtractionException 실패. 응답 없는 시간 초과는 EXTRACTION_TIMEOUT(재시도 대상), 4xx는 EXTRACTION_REJECTED(재시도 안 함)
     */
    ExtractedScoreboard extract(byte[] image, String mimeType, Duration requestTimeout) throws ExtractionException;
}
