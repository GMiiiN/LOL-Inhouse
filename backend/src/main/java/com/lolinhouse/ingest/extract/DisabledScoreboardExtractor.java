package com.lolinhouse.ingest.extract;

import java.time.Duration;

import com.lolinhouse.ingest.job.ExtractionErrorCode;

/**
 * 역할: API 키가 없을 때 쓰는 추출기. 부르면 항상 EXTRACTION_NOT_CONFIGURED로 실패한다.
 * 키 없는 CI·로컬에서도 앱이 기동되게 하려고 빈 자체는 만들어 둔다(ExtractorConfig).
 * 설명: docs/design/m4-design.md §2.1
 */
public final class DisabledScoreboardExtractor implements ScoreboardExtractor {

    @Override
    public ExtractedScoreboard extract(byte[] image, String mimeType, Duration requestTimeout)
            throws ExtractionException {
        throw new ExtractionException(ExtractionErrorCode.EXTRACTION_NOT_CONFIGURED,
                "Gemini API 키가 설정되지 않았습니다.");
    }
}
