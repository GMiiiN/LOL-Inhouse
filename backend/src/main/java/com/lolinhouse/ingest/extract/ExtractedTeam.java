package com.lolinhouse.ingest.extract;

import java.util.List;

/**
 * 역할: 결과창의 한 팀(위쪽 또는 아래쪽). 합계 4개는 팀 이름 옆에 표시된 값이지 행의 합이 아니다.
 * rows는 화면 순서 5명, banBoxes는 화면 순서 5칸(위 줄 왼쪽부터 3개, 아래 줄 왼쪽부터 2개).
 * 설명: docs/design/m4-design.md §2.1
 */
public record ExtractedTeam(int kills, int deaths, int assists, int gold, List<ExtractedRow> rows,
        List<Box> banBoxes) {

    public ExtractedTeam {
        rows = List.copyOf(rows);
        banBoxes = List.copyOf(banBoxes);
    }
}
