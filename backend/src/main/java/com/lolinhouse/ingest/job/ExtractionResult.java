package com.lolinhouse.ingest.job;

import java.util.List;

import com.lolinhouse.champion.IconMatch;
import com.lolinhouse.ingest.extract.Attempt;
import com.lolinhouse.ingest.extract.ExtractedScoreboard.TopResult;

/**
 * 역할: extraction_job.result_json에 저장하는 추출 사실(스키마 v1). 작업자 B(실행기)가 만들어 직렬화하고, 작업자 C(초안)가 읽는다.
 * 화면 순서의 숫자·닉네임·박스와 챔피언 상위 후보만 담고, 선수 매칭이나 1팀/2팀 판별 같은 초안은 담지 않는다(조회 때 현재 DB로 계산).
 * 직렬화는 Jackson 3 JsonMapper로 한다. 필드를 바꾸면 schemaVersion을 올린다. 닉네임이 들어 있으므로 로그에 남기지 않는다.
 * 설명: docs/design/m4-design.md §1 "result_json 형식"
 *
 * @param schemaVersion     {@link #CURRENT_SCHEMA_VERSION}
 * @param model             추출에 쓴 Gemini 모델 이름
 * @param dataDragonVersion 아이콘 매칭에 쓴 Data Dragon 버전
 * @param attempts          추출 호출 시도 기록
 * @param topResult         위쪽 팀 승패
 * @param shotDate          ISO 날짜 문자열 "2026-10-12"(LocalDate.toString())
 * @param duration          "mm:ss"
 */
public record ExtractionResult(int schemaVersion, String model, String dataDragonVersion, List<Attempt> attempts,
        TopResult topResult, String shotDate, String duration, Team top, Team bottom) {

    public static final int CURRENT_SCHEMA_VERSION = 1;

    /** 한 팀. rows와 bans는 화면 순서 5개씩. */
    public record Team(int kills, int deaths, int assists, int gold, List<Row> rows, List<Ban> bans) {

        public Team {
            rows = List.copyOf(rows);
            bans = List.copyOf(bans);
        }
    }

    /** 선수 한 줄. nickname은 읽은 그대로, portraitBox는 [ymin, xmin, ymax, xmax](0~1000). */
    public record Row(String nickname, int kills, int deaths, int assists, int cs, int gold, List<Integer> portraitBox,
            IconMatch champion) {
    }

    /** 밴 한 칸(화면 순서 슬롯). box는 [ymin, xmin, ymax, xmax](0~1000). */
    public record Ban(List<Integer> box, IconMatch champion) {
    }

    public ExtractionResult {
        attempts = List.copyOf(attempts);
    }
}
