package com.lolinhouse.ingest.job;

/**
 * 역할: POST /api/admin/duels/{duelId}/extractions의 응답. reused=true면 같은 이미지의 기존 작업을 다시 돌려준 것(새 Gemini 호출 없음).
 * 설명: docs/design/m4-design.md §5, D8
 */
public record ExtractionStartResponse(Long jobId, ExtractionJobStatus status, boolean reused) {
}
