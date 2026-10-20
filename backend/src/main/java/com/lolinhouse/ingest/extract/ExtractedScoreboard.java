package com.lolinhouse.ingest.extract;

import java.time.LocalDate;

/**
 * 역할: Gemini 응답이 검증(ScoreboardResponseValidator)을 통과한 뒤의 타입 결과. 실험 코드의 JsonNode 반환을 대체한다.
 * 아직 선수·챔피언과 연결되지 않은 "화면에서 읽은 사실"이다(챔피언 후보는 실행기가 박스를 잘라 매칭해 덧붙인다).
 * 설명: docs/design/m4-design.md §2.1
 *
 * @param topResult 화면 상단 승리/패배(위쪽 팀 기준). Gemini의 "승리"/"패배"를 변환한 값
 * @param shotDate  화면의 날짜(YYYY/MM/DD를 STRICT로 파싱)
 * @param duration  경기 소요 시간 "mm:ss" 문자열(0초 여부 등은 이후 평가 단계가 본다)
 */
public record ExtractedScoreboard(TopResult topResult, LocalDate shotDate, String duration, ExtractedTeam top,
        ExtractedTeam bottom) {

    /** 위쪽 팀의 결과. */
    public enum TopResult {
        WIN,
        LOSS
    }
}
