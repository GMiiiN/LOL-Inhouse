package com.lolinhouse.ingest.extract;

/**
 * 역할: 추출 호출 한 번의 시도 기록(계측용). 응답 본문·예외 메시지는 비밀값·닉네임 보호를 위해 담지 않는다.
 * ExtractionResult의 attempts[]에 그대로 저장된다.
 * 설명: docs/design/m4-design.md §1·§2.1
 *
 * @param number     1부터 시작하는 시도 번호
 * @param httpStatus 실패한 HTTP 상태(성공이거나 HTTP 응답이 없으면 null)
 * @param errorType  실패한 예외의 클래스 단순 이름(성공이면 null)
 * @param latencyMs  이 시도에 걸린 시간
 * @param backoffMs  이 시도 뒤에 기다린 시간(마지막 시도나 성공이면 0)
 */
public record Attempt(int number, Integer httpStatus, String errorType, long latencyMs, long backoffMs) {
}
