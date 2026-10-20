package com.lolinhouse.ingest.extract;

import java.util.List;

/**
 * 역할: 결과창 이미지 안의 박스. Gemini 형식 그대로 [ymin, xmin, ymax, xmax], 원본 전체 이미지 기준 0~1000 정규화 정수다(y가 먼저).
 * 범위·면적 검증은 ScoreboardResponseValidator(작업자 A)가 하고, 이 record는 값만 담는다.
 * result_json에서는 길이 4짜리 정수 배열로 저장하므로 {@link #toList()}와 {@link #fromList(List)}로 오간다.
 * 설명: docs/design/m4-design.md §1
 */
public record Box(int ymin, int xmin, int ymax, int xmax) {

    /** result_json·API 배열 표현 [ymin, xmin, ymax, xmax]. */
    public List<Integer> toList() {
        return List.of(ymin, xmin, ymax, xmax);
    }

    /** Boxes.pixels(int[] ...)가 받는 배열 표현. */
    public int[] toArray() {
        return new int[] {ymin, xmin, ymax, xmax};
    }

    /** 길이 4짜리 목록에서 만든다. 길이가 다르면 IllegalArgumentException. */
    public static Box fromList(List<Integer> values) {
        if (values == null || values.size() != 4) {
            throw new IllegalArgumentException("박스는 숫자 4개여야 합니다.");
        }
        return new Box(values.get(0), values.get(1), values.get(2), values.get(3));
    }
}
