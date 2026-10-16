package com.lolinhouse.domain.set;

import static com.lolinhouse.domain.Position.ADC;
import static com.lolinhouse.domain.Position.JUNGLE;
import static com.lolinhouse.domain.Position.MID;
import static com.lolinhouse.domain.Position.SUPPORT;
import static com.lolinhouse.domain.Position.TOP;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lolinhouse.domain.Position;
import com.lolinhouse.domain.set.PositionAssignment.Row;
import java.util.List;
import org.junit.jupiter.api.Test;

class PositionAssignmentTest {

    @Test
    void 캡처한_사람이_원딜이면_위쪽_팀은_원딜_탑_정글_미드_서폿_순이다() {
        // 샘플 1(2026/10/11 18:14)의 실제 배치
        assertThat(PositionAssignment.topTeamPositions(ADC))
                .containsExactly(ADC, TOP, JUNGLE, MID, SUPPORT);
    }

    @Test
    void 캡처한_사람이_미드이면_위쪽_팀은_미드_탑_정글_원딜_서폿_순이다() {
        // 샘플 3(2026/10/12 25:42)의 실제 배치
        assertThat(PositionAssignment.topTeamPositions(MID))
                .containsExactly(MID, TOP, JUNGLE, ADC, SUPPORT);
    }

    @Test
    void 캡처한_사람이_탑이면_기본_순서_그대로다() {
        assertThat(PositionAssignment.topTeamPositions(TOP))
                .containsExactly(TOP, JUNGLE, MID, ADC, SUPPORT);
    }

    @Test
    void 캡처한_사람이_정글이면_정글이_맨_위고_나머지는_기본_순서다() {
        assertThat(PositionAssignment.topTeamPositions(JUNGLE))
                .containsExactly(JUNGLE, TOP, MID, ADC, SUPPORT);
    }

    @Test
    void 캡처한_사람이_서폿이면_서폿이_맨_위고_나머지는_기본_순서다() {
        assertThat(PositionAssignment.topTeamPositions(SUPPORT))
                .containsExactly(SUPPORT, TOP, JUNGLE, MID, ADC);
    }

    @Test
    void 아래쪽_팀은_항상_탑부터_서폿_순이다() {
        assertThat(PositionAssignment.bottomTeamPositions())
                .containsExactly(TOP, JUNGLE, MID, ADC, SUPPORT);
    }

    @Test
    void 캡처한_포지션이_없으면_예외() {
        assertThatThrownBy(() -> PositionAssignment.topTeamPositions(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("포지션");
    }

    @Test
    void 행_번호는_화면_순서대로_1부터_붙는다() {
        List<Row> rows = PositionAssignment.rowsOf(List.of(ADC, TOP, JUNGLE, MID, SUPPORT));

        assertThat(rows).extracting(Row::rowNumber).containsExactly(1, 2, 3, 4, 5);
        assertThat(rows).extracting(Row::position).containsExactly(ADC, TOP, JUNGLE, MID, SUPPORT);
    }

    @Test
    void 라인_순서로_정렬해도_원래_행_번호는_유지된다() {
        List<Row> rows = PositionAssignment.rowsOf(PositionAssignment.topTeamPositions(ADC));

        List<Row> sorted = PositionAssignment.sortByLane(rows);

        assertThat(sorted).extracting(Row::position).containsExactly(TOP, JUNGLE, MID, ADC, SUPPORT);
        assertThat(sorted).extracting(Row::rowNumber).containsExactly(2, 3, 4, 1, 5);
    }

    @Test
    void 같은_팀_두_행의_포지션을_서로_맞바꾼다() {
        List<Row> rows = PositionAssignment.rowsOf(List.of(ADC, TOP, JUNGLE, MID, SUPPORT));

        List<Row> swapped = PositionAssignment.swapPositions(rows, 1, 4);

        assertThat(swapped).extracting(Row::position).containsExactly(MID, TOP, JUNGLE, ADC, SUPPORT);
        assertThat(swapped).extracting(Row::rowNumber).containsExactly(1, 2, 3, 4, 5);
    }

    @Test
    void 맞바꾸기는_원본_목록을_바꾸지_않는다() {
        List<Row> rows = PositionAssignment.rowsOf(List.of(ADC, TOP, JUNGLE, MID, SUPPORT));

        PositionAssignment.swapPositions(rows, 1, 4);

        assertThat(rows.get(0).position()).isEqualTo(ADC);
    }

    @Test
    void 없는_행_번호로_맞바꾸면_예외() {
        List<Row> rows = PositionAssignment.rowsOf(List.of(ADC, TOP, JUNGLE, MID, SUPPORT));

        assertThatThrownBy(() -> PositionAssignment.swapPositions(rows, 1, 6))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("행 번호");
    }

    @Test
    void 팀은_정확히_5행이어야_한다() {
        List<Position> fourPositions = List.of(TOP, JUNGLE, MID, ADC);

        assertThatThrownBy(() -> PositionAssignment.rowsOf(fourPositions))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("5");
    }
}
