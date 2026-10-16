package com.lolinhouse.domain.set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lolinhouse.domain.set.Fearless.PlayedSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class FearlessTest {

    private static final List<String> SET1_PICKS =
            List.of("a1", "a2", "a3", "a4", "a5", "b1", "b2", "b3", "b4", "b5");
    private static final List<String> SET2_PICKS =
            List.of("c1", "c2", "c3", "c4", "c5", "d1", "d2", "d3", "d4", "d5");
    private static final List<String> SET1_BANS =
            List.of("m1", "m2", "m3", "m4", "m5", "n1", "n2", "n3", "n4", "n5");
    private static final List<String> SET2_BANS =
            List.of("o1", "o2", "o3", "o4", "o5", "q1", "q2", "q3", "q4", "q5");

    private static final PlayedSet SET1 = new PlayedSet(SET1_PICKS, SET1_BANS);
    private static final PlayedSet SET2 = new PlayedSet(SET2_PICKS, SET2_BANS);

    @Test
    void 대결의_첫_세트는_제한이_없다() {
        assertThat(Fearless.restrictedChampions(List.of())).isEmpty();
    }

    @Test
    void 두번째_세트는_첫_세트_양_팀_픽_10개가_제한된다() {
        Set<String> restricted = Fearless.restrictedChampions(List.of(SET1));

        assertThat(restricted).containsExactlyInAnyOrderElementsOf(SET1_PICKS);
    }

    @Test
    void 초기화_없이_3세트까지_가면_앞선_두_세트의_20개가_제한된다() {
        Set<String> restricted = Fearless.restrictedChampions(List.of(SET1, SET2));

        assertThat(restricted).containsExactlyInAnyOrder(
                "a1", "a2", "a3", "a4", "a5", "b1", "b2", "b3", "b4", "b5",
                "c1", "c2", "c3", "c4", "c5", "d1", "d2", "d3", "d4", "d5");
    }

    @Test
    void 제한_목록에는_픽만_들어가고_밴은_들어가지_않는다() {
        Set<String> restricted = Fearless.restrictedChampions(List.of(SET1, SET2));

        assertThat(restricted).doesNotContainAnyElementsOf(SET1_BANS);
        assertThat(restricted).doesNotContainAnyElementsOf(SET2_BANS);
    }

    @Test
    void 새_세트의_픽_중_제한된_챔피언만_위반으로_돌려준다() {
        Set<String> restricted = Fearless.restrictedChampions(List.of(SET1));
        List<String> newPicks = List.of("x1", "a3", "x2", "b5", "x3", "x4", "x5", "x6", "x7", "x8");

        assertThat(Fearless.violations(restricted, newPicks)).containsExactly("a3", "b5");
    }

    @Test
    void 위반이_없으면_빈_목록이다() {
        Set<String> restricted = Fearless.restrictedChampions(List.of(SET1));

        assertThat(Fearless.violations(restricted, SET2_PICKS)).isEmpty();
    }

    @Test
    void 초기화한_새_대결은_제한이_비어_있어_같은_챔피언도_위반이_아니다() {
        Set<String> restrictedInNewDuel = Fearless.restrictedChampions(List.of());

        assertThat(Fearless.violations(restrictedInNewDuel, SET1_PICKS)).isEmpty();
    }

    @Test
    void 같은_챔피언이_픽에_두_번_있어도_위반은_한_번만_알린다() {
        List<String> picksWithDuplicate = List.of("a1", "a1");

        assertThat(Fearless.violations(Set.of("a1"), picksWithDuplicate)).containsExactly("a1");
    }

    @Test
    void 제한_집합은_수정할_수_없다() {
        Set<String> restricted = Fearless.restrictedChampions(List.of(SET1));

        assertThatThrownBy(() -> restricted.add("x")).isInstanceOf(UnsupportedOperationException.class);
    }
}
