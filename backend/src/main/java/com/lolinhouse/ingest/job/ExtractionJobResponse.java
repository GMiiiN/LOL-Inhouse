package com.lolinhouse.ingest.job;

import com.lolinhouse.domain.Position;
import com.lolinhouse.domain.Side;

/**
 * 역할: GET /api/admin/extractions/{jobId}의 응답. 시각(Instant) 필드는 일부러 없다 — 경과 시간은 프런트가 직접 재고,
 * jackson 시간대 설정(Asia/Seoul)과 UTC가 섞이는 문제를 만들지 않는다.
 * 설명: docs/design/m4-design.md §5, D15
 *
 * @param errorCode FAILED일 때만. 원문 메시지는 없다
 * @param savedSetId 이 작업으로 저장된 세트. 아직 저장 전이면 null
 */
public record ExtractionJobResponse(Long jobId, Long duelId, ExtractionJobStatus status,
        ExtractionErrorCode errorCode, int attemptCount, Side topSide, Position capturerPosition, Long savedSetId) {

    static ExtractionJobResponse from(ExtractionJob job) {
        return new ExtractionJobResponse(job.id(), job.duelId(), job.status(), job.errorCode(), job.attemptCount(),
                job.topSide(), job.capturerPosition(), job.gameSetId());
    }
}
