package com.lolinhouse.record;

import java.util.List;

import com.lolinhouse.domain.DuelTeam;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** RecordViews의 대결 점수와 대결 상세 제한 나누기(DB 없음). */
class RecordViewsDuelTest {

    private static PickRow pick(long duelId, int setNo, String champion) {
        return new PickRow(duelId * 10 + setNo, duelId, setNo, champion);
    }

    @Test
    void 대결_점수는_승리_팀별_세트_수이고_두_팀_키가_항상_있다() {
        var score = RecordViews.score(List.of(DuelTeam.TEAM1, DuelTeam.TEAM2, DuelTeam.TEAM1));

        assertThat(score).containsEntry(DuelTeam.TEAM1, 2).containsEntry(DuelTeam.TEAM2, 1);
        assertThat(RecordViews.score(List.of())).containsEntry(DuelTeam.TEAM1, 0).containsEntry(DuelTeam.TEAM2, 0);
    }

    @Test
    void 대결_상세는_앞_대결_픽을_이어온_것으로_이_대결_픽을_이번_것으로_나눈다() {
        List<PickRow> picks = List.of(pick(1, 1, "Zed"), pick(1, 2, "Ahri"), pick(2, 1, "Lux"), pick(2, 2, "Lux"),
                pick(3, 1, "Garen"));

        var fearless = RecordViews.fearlessOfDuel(List.of(1L, 2L), 2L, picks);

        assertThat(fearless.inherited()).containsExactly("Ahri", "Zed");
        assertThat(fearless.picked()).containsExactly("Lux");
    }

    @Test
    void RESET_대결은_이어온_제한이_비어_있다() {
        var fearless = RecordViews.fearlessOfDuel(List.of(2L), 2L, List.of(pick(1, 1, "Zed"), pick(2, 1, "Lux")));

        assertThat(fearless.inherited()).isEmpty();
        assertThat(fearless.picked()).containsExactly("Lux");
    }
}
