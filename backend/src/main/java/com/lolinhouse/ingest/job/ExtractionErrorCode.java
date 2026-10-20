package com.lolinhouse.ingest.job;

/**
 * 역할: FAILED 작업의 실패 이유. DB(error_code)에는 이름 그대로 저장하고 CHECK는 걸지 않는다(코드가 늘 때마다 마이그레이션하지 않으려고).
 * 프런트 errorMessages.ts가 같은 이름에 한글 문구를 대응시킨다. 원문 메시지는 저장하지 않는다.
 * 설명: docs/design/m4-design.md §5 표, §11.2-3
 */
public enum ExtractionErrorCode {
    /** 429 재시도 소진 */
    EXTRACTION_RATE_LIMITED,
    /** 408·5xx 재시도 소진 */
    EXTRACTION_UPSTREAM_UNAVAILABLE,
    /** 그 밖의 4xx(키·요청 오류) */
    EXTRACTION_REJECTED,
    /** 작업 마감 초과, 요청 시간 초과, 또는 오래된 RUNNING */
    EXTRACTION_TIMEOUT,
    /** 응답이 형식·값 검증을 통과하지 못해 초안을 만들 수 없음 */
    EXTRACTION_INVALID_RESPONSE,
    /** API 키가 설정되지 않음(키 없는 환경의 추출기가 내는 코드) */
    EXTRACTION_NOT_CONFIGURED,
    /** 저장된 이미지를 읽을 수 없음 */
    IMAGE_UNREADABLE,
    /** 챔피언 아이콘 카탈로그가 없음(Gemini를 부르기 전에 실패해 한도를 아낀다) */
    CHAMPION_CATALOG_UNAVAILABLE,
    /** 실행기 대기열이 가득 참 */
    BUSY,
    /** 앱 재시작 또는 스레드 인터럽트 */
    INTERRUPTED,
    /** 그 밖의 예외 */
    INTERNAL_ERROR,
    /** QUEUED로 너무 오래 머물러 조회 시 정리됨 */
    EXPIRED,
    /** 같은 대결·같은 이미지의 "다시 추출"로 새 작업이 만들어져 대체됨 */
    SUPERSEDED
}
