package com.lolinhouse.domain.stats;

import java.util.ArrayList;
import java.util.List;

import com.lolinhouse.domain.DuelTeam;
import com.lolinhouse.domain.Position;
import com.lolinhouse.domain.stats.DuoCalculator.Duo;
import com.lolinhouse.domain.stats.DuoCalculator.Selection;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 역할: 듀오 집계(같은 세트 같은 팀)·최소 세트·정렬·최고/최악 겹침 제외를 DB 없이 확인한다. */
class DuoCalculatorTest {

    private int setSeq;

    /** 한 세트: team1 선수들이 같은 팀. team1Win이면 team1 승리. */
    private void set(List<PlayerSetRecord> out, boolean team1Win, long[] team1, long[] team2) {
        String setId = String.valueOf(++setSeq);
        for (long p : team1) {
            out.add(rec(p, setId, DuelTeam.TEAM1, team1Win));
        }
        for (long p : team2) {
            out.add(rec(p, setId, DuelTeam.TEAM2, !team1Win));
        }
    }

    private static PlayerSetRecord rec(long p, String setId, DuelTeam team, boolean win) {
        return new PlayerSetRecord(String.valueOf(p), "1", setId, team, Position.TOP, "Ahri", win, 1, 1, 1, 1, 1);
    }

    private static String name(long id) {
        return "p" + id;
    }

    private void repeat(List<PlayerSetRecord> out, int wins, int losses, long[] team1, long[] team2) {
        for (int i = 0; i < wins; i++) {
            set(out, true, team1, team2);
        }
        for (int i = 0; i < losses; i++) {
            set(out, false, team1, team2);
        }
    }

    @Test
    void 같은_세트_같은_팀만_듀오이고_상대_팀은_세지_않는다() {
        List<PlayerSetRecord> records = new ArrayList<>();
        set(records, true, new long[] {1, 2}, new long[] {3});

        assertThat(DuoCalculator.pairs(records)).containsExactly(new Duo(1, 2, 1, 1));
    }

    @Test
    void 선수_아이디_오름차순으로_쌍을_만들고_패배도_센다() {
        List<PlayerSetRecord> records = new ArrayList<>();
        set(records, false, new long[] {9, 4}, new long[] {1});

        assertThat(DuoCalculator.pairs(records)).containsExactly(new Duo(4, 9, 1, 0));
    }

    @Test
    void 팀이_바뀌어도_같은_팀이던_세트만_센다() {
        List<PlayerSetRecord> records = new ArrayList<>();
        set(records, true, new long[] {1, 2}, new long[] {3});
        set(records, true, new long[] {1, 3}, new long[] {2});

        assertThat(DuoCalculator.pairs(records)).containsExactlyInAnyOrder(new Duo(1, 2, 1, 1), new Duo(1, 3, 1, 1));
    }

    @Test
    void 함께_세트가_최소_미만이면_제외한다() {
        List<PlayerSetRecord> records = new ArrayList<>();
        repeat(records, 2, 0, new long[] {1, 2}, new long[] {3});
        repeat(records, 2, 1, new long[] {4, 5}, new long[] {6});

        Selection s = DuoCalculator.select(records, DuoCalculatorTest::name, 3, 3);

        assertThat(s.best()).containsExactly(new Duo(4, 5, 3, 2));
        assertThat(s.worst()).isEmpty();
    }

    @Test
    void 최고는_승률_세트수_이름순이고_최악은_승률_오름차순이다() {
        List<PlayerSetRecord> records = new ArrayList<>();
        repeat(records, 3, 0, new long[] {1, 2}, new long[] {90});
        repeat(records, 4, 0, new long[] {3, 4}, new long[] {90});
        repeat(records, 3, 0, new long[] {5, 6}, new long[] {90});
        repeat(records, 1, 2, new long[] {7, 8}, new long[] {90});
        repeat(records, 0, 3, new long[] {10, 11}, new long[] {90});

        Selection s = DuoCalculator.select(records, DuoCalculatorTest::name, 3, 3);

        assertThat(s.best()).extracting(Duo::a).containsExactly(3L, 1L, 5L);
        assertThat(s.worst()).extracting(Duo::a).containsExactly(10L, 7L);
    }

    @Test
    void 최고에_뽑힌_쌍은_최악에_나오지_않는다() {
        List<PlayerSetRecord> records = new ArrayList<>();
        repeat(records, 3, 0, new long[] {1, 2}, new long[] {90});
        repeat(records, 0, 3, new long[] {3, 4}, new long[] {90});

        Selection s = DuoCalculator.select(records, DuoCalculatorTest::name, 3, 3);

        assertThat(s.best()).extracting(Duo::a).containsExactly(1L, 3L);
        assertThat(s.worst()).isEmpty();
    }

    @Test
    void 각각_limit쌍까지만_담고_서로_겹치지_않는다() {
        List<PlayerSetRecord> records = new ArrayList<>();
        for (long i = 0; i < 8; i++) {
            repeat(records, (int) (i % 4) + 1, 3 - (int) (i % 4), new long[] {100 + i, 200 + i}, new long[] {900});
        }

        Selection s = DuoCalculator.select(records, DuoCalculatorTest::name, 3, 3);

        assertThat(s.best()).hasSize(3);
        assertThat(s.worst()).hasSize(3);
        assertThat(s.best()).doesNotContainAnyElementsOf(s.worst());
    }
}
