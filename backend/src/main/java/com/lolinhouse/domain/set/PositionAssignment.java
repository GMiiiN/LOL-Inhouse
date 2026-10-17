package com.lolinhouse.domain.set;

import com.lolinhouse.domain.Position;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 결과창 행 순서에 실제 포지션을 배정하는 규칙(순수 계산, 상태 없음).
 * 위쪽 팀은 "캡처한 사람 포지션 + 나머지 기본 순서", 아래쪽 팀은 탑→서폿.
 */
public final class PositionAssignment {

    private static final int TEAM_SIZE = 5;

    private PositionAssignment() {
    }

    /** 결과창 한 행: 화면 행 번호(1~5, 정렬해도 유지)와 그 행에 배정된 포지션. */
    public record Row(int rowNumber, Position position) {
    }

    /** 위쪽 팀 행 순서. 첫 행이 캡처한 사람, 나머지는 그 포지션을 뺀 기본 순서. */
    public static List<Position> topTeamPositions(Position capturerPosition) {
        if (capturerPosition == null) {
            throw new IllegalArgumentException("캡처한 사람의 포지션을 선택해야 합니다.");
        }
        List<Position> positions = new ArrayList<>();
        positions.add(capturerPosition);
        for (Position position : Position.values()) {
            if (position != capturerPosition) {
                positions.add(position);
            }
        }
        return List.copyOf(positions);
    }

    /** 아래쪽 팀 행 순서. 항상 탑→서폿(Position 선언 순서). */
    public static List<Position> bottomTeamPositions() {
        return List.of(Position.values());
    }

    /** 화면 순서의 포지션 목록에 행 번호 1~5를 붙인다. */
    public static List<Row> rowsOf(List<Position> positionsInScreenOrder) {
        if (positionsInScreenOrder == null || positionsInScreenOrder.size() != TEAM_SIZE) {
            throw new IllegalArgumentException("한 팀은 정확히 5행이어야 합니다.");
        }
        List<Row> rows = new ArrayList<>();
        for (int i = 0; i < TEAM_SIZE; i++) {
            rows.add(new Row(i + 1, positionsInScreenOrder.get(i)));
        }
        return List.copyOf(rows);
    }

    /** 확인 화면용: 탑→서폿 순으로 정렬. 원본 대조를 위해 행 번호는 그대로 둔다. */
    public static List<Row> sortByLane(List<Row> rows) {
        return rows.stream().sorted(Comparator.comparing(Row::position)).toList();
    }

    /** 같은 팀의 두 행이 서로 포지션을 맞바꾼다(행 번호는 제자리). 원본은 바꾸지 않는다. */
    public static List<Row> swapPositions(List<Row> rows, int rowNumberA, int rowNumberB) {
        Row a = findRow(rows, rowNumberA);
        Row b = findRow(rows, rowNumberB);
        return rows.stream()
                .map(row -> {
                    if (row.equals(a)) {
                        return new Row(a.rowNumber(), b.position());
                    }
                    if (row.equals(b)) {
                        return new Row(b.rowNumber(), a.position());
                    }
                    return row;
                })
                .toList();
    }

    private static Row findRow(List<Row> rows, int rowNumber) {
        return rows.stream()
                .filter(row -> row.rowNumber() == rowNumber)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 행 번호입니다: " + rowNumber));
    }
}
