package com.lolinhouse.domain.set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;

import com.lolinhouse.domain.set.FearlessChain.ChainDuel;
import com.lolinhouse.domain.set.FearlessConflicts.Conflict;
import com.lolinhouse.domain.set.FearlessConflicts.SetPick;
import org.junit.jupiter.api.Test;

/** 역할: 피어리스 앞쪽 제한·충돌 목록·대결 삭제 체인 의존 판정 검증. */
class FearlessConflictsTest {

    private static ChainDuel reset(long id, int seq) {
        return new ChainDuel(id, seq, false);
    }

    private static ChainDuel cont(long id, int seq) {
        return new ChainDuel(id, seq, true);
    }

    private static SetPick pick(long setId, long duelId, int setNo, String champion) {
        return new SetPick(setId, duelId, setNo, champion);
    }

    @Test
    void 세트_k_시점_제한은_같은_대결의_앞_번호와_앞선_체인_대결만_포함한다() {
        List<ChainDuel> duels = List.of(reset(10, 1), cont(11, 2), reset(12, 3));
        List<SetPick> picks = List.of(pick(1, 10, 1, "Ahri"), pick(2, 11, 1, "Bard"), pick(3, 11, 2, "Lux"),
                pick(4, 11, 3, "Zed"), pick(5, 12, 1, "Yone"));

        assertThat(FearlessConflicts.restrictedAt(duels, 11, 2, picks)).containsExactly("Ahri", "Bard");
        assertThat(FearlessConflicts.restrictedAt(duels, 11, 3, picks)).containsExactly("Ahri", "Bard", "Lux");
        assertThat(FearlessConflicts.restrictedAt(duels, 10, 1, picks)).isEmpty();
        assertThat(FearlessConflicts.restrictedAt(duels, 12, 2, picks)).containsExactly("Yone");
    }

    @Test
    void 앞_세트와_겹치면_충돌에_나온다() {
        List<ChainDuel> duels = List.of(reset(10, 1));
        List<SetPick> picks = List.of(pick(1, 10, 1, "Ahri"), pick(2, 10, 2, "Lux"));

        assertThat(FearlessConflicts.conflicts(duels, 2, 10, 2, List.of("Ahri", "Zed"), picks))
                .containsExactly(new Conflict(1, 10, 1, 1, "Ahri"));
    }

    @Test
    void 뒤_세트와_겹치면_CONTINUE로_이어진_뒤_대결도_충돌에_나온다() {
        List<ChainDuel> duels = List.of(reset(10, 1), cont(11, 2), cont(12, 3));
        List<SetPick> picks = List.of(pick(1, 10, 1, "Ahri"), pick(2, 10, 2, "Ahri"), pick(3, 11, 1, "Ahri"),
                pick(4, 12, 1, "Ahri"));

        assertThat(FearlessConflicts.conflicts(duels, 1, 10, 1, List.of("Ahri"), picks)).extracting(Conflict::setId)
                .containsExactly(2L, 3L, 4L);
    }

    @Test
    void RESET으로_끊긴_대결은_충돌이_아니다() {
        List<ChainDuel> duels = List.of(reset(10, 1), reset(11, 2), cont(12, 3));
        List<SetPick> picks = List.of(pick(1, 10, 1, "Ahri"), pick(2, 11, 1, "Ahri"), pick(3, 12, 1, "Ahri"));

        assertThat(FearlessConflicts.conflicts(duels, 1, 10, 1, List.of("Ahri"), picks)).isEmpty();
        assertThat(FearlessConflicts.conflicts(duels, 3, 12, 1, List.of("Ahri"), picks))
                .containsExactly(new Conflict(2, 11, 2, 1, "Ahri"));
    }

    @Test
    void 겹치지_않는_챔피언은_충돌이_아니다() {
        List<ChainDuel> duels = List.of(reset(10, 1));
        List<SetPick> picks = List.of(pick(1, 10, 1, "Ahri"), pick(2, 10, 2, "Lux"));

        assertThat(FearlessConflicts.conflicts(duels, 1, 10, 1, List.of("Zed"), picks)).isEmpty();
    }

    @Test
    void Ahri와_Lux가_1세트와_3세트에서_뒤바뀐_상태는_뒤_세트_충돌과_앞쪽_제한으로_각각_보인다() {
        List<ChainDuel> duels = List.of(reset(10, 1));
        List<SetPick> picks = List.of(pick(1, 10, 1, "Lux"), pick(2, 10, 2, "Zed"), pick(3, 10, 3, "Ahri"));

        assertThat(FearlessConflicts.conflicts(duels, 1, 10, 1, List.of("Ahri"), picks))
                .containsExactly(new Conflict(3, 10, 1, 3, "Ahri"));
        assertThat(FearlessConflicts.restrictedAt(duels, 10, 3, picks)).containsExactly("Lux", "Zed");
    }

    @Test
    void 충돌은_대결_seq_세트_번호_챔피언_순으로_중복_없이_정렬된다() {
        List<ChainDuel> duels = List.of(cont(11, 2), reset(10, 1));
        List<SetPick> picks = List.of(pick(3, 11, 1, "Zed"), pick(1, 10, 1, "Zed"), pick(1, 10, 1, "Ahri"),
                pick(2, 10, 2, "Ahri"), pick(1, 10, 1, "Zed"));

        assertThat(FearlessConflicts.conflicts(duels, 9, 11, 2, List.of("Zed", "Ahri"), picks))
                .extracting(Conflict::setId, Conflict::championKey)
                .containsExactly(tuple(1L, "Ahri"), tuple(1L, "Zed"), tuple(2L, "Ahri"), tuple(3L, "Zed"));
    }

    @Test
    void 대결_삭제_체인_의존은_RESET_다음에_CONTINUE가_올_때만이다() {
        List<ChainDuel> duels = List.of(reset(10, 1), cont(11, 2), reset(12, 3));

        assertThat(FearlessConflicts.deletionChangesChain(duels, 10)).isTrue();
        assertThat(FearlessConflicts.deletionChangesChain(duels, 11)).isFalse();
        assertThat(FearlessConflicts.deletionChangesChain(duels, 12)).isFalse();
    }
}
