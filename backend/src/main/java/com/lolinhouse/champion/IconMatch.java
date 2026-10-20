package com.lolinhouse.champion;

import java.util.List;

/**
 * 역할: 잘라낸 아이콘 하나의 매칭 결과. 점수 높은 순 상위 후보(최대 3개)와 1·2순위 점수 차이(margin).
 * ExtractionResult(result_json)에 그대로 저장되는 "champion" 객체와 같은 모양이다.
 * 박스를 잘라내지 못했으면 cropFailed=true이고 후보는 비어 있다(칸마다 따로 실패할 수 있다).
 * 설명: docs/design/m4-design.md §1·§2.2
 *
 * @param candidates 점수 내림차순. 비어 있을 수 있다(cropFailed)
 * @param margin     candidates[0].score - candidates[1].score. 후보가 2개 미만이면 0
 * @param cropFailed 박스를 이미지에서 잘라내지 못함
 */
public record IconMatch(List<Candidate> candidates, double margin, boolean cropFailed) {

    /** 후보 챔피언 하나. 점수는 확률이 아니며 클수록 유사하다. */
    public record Candidate(String id, double score) {
    }

    public IconMatch {
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
    }

    /** 잘라내기에 실패한 칸의 결과. */
    public static IconMatch failedCrop() {
        return new IconMatch(List.of(), 0, true);
    }
}
