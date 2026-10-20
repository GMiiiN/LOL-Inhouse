package com.lolinhouse.domain.set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lolinhouse.domain.set.FearlessChain.ChainDuel;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 역할: 피어리스 이어가기 체인(대결 seq를 거슬러 처음 만나는 RESET 대결까지) 계산 검증.
 * ChainDuel(id, seq, continuesPrevious): continuesPrevious=false가 RESET.
 */
class FearlessChainTest {

    private static ChainDuel reset(long id, int seq) {
        return new ChainDuel(id, seq, false);
    }

    private static ChainDuel cont(long id, int seq) {
        return new ChainDuel(id, seq, true);
    }

    @Test
    void RESET_대결_하나면_자기_자신만() {
        assertThat(FearlessChain.chainDuelIds(List.of(reset(10, 1)), 10)).containsExactly(10L);
    }

    @Test
    void RESET_다음_CONTINUE_CONTINUE는_처음_대결까지_모두_포함한다() {
        List<ChainDuel> duels = List.of(reset(10, 1), cont(11, 2), cont(12, 3));

        assertThat(FearlessChain.chainDuelIds(duels, 12)).containsExactly(10L, 11L, 12L);
        assertThat(FearlessChain.chainDuelIds(duels, 11)).containsExactly(10L, 11L);
        assertThat(FearlessChain.chainDuelIds(duels, 10)).containsExactly(10L);
    }

    @Test
    void 중간에_RESET이_있으면_거기서_끊긴다() {
        List<ChainDuel> duels = List.of(reset(10, 1), cont(11, 2), reset(12, 3), cont(13, 4));

        assertThat(FearlessChain.chainDuelIds(duels, 13)).containsExactly(12L, 13L);
        assertThat(FearlessChain.chainDuelIds(duels, 12)).containsExactly(12L);
        assertThat(FearlessChain.chainDuelIds(duels, 11)).containsExactly(10L, 11L);
    }

    @Test
    void 입력_순서가_섞여_있어도_seq_기준으로_거슬러_올라간다() {
        List<ChainDuel> duels = List.of(cont(13, 4), reset(10, 1), cont(12, 3), cont(11, 2));

        assertThat(FearlessChain.chainDuelIds(duels, 13)).containsExactly(10L, 11L, 12L, 13L);
    }

    @Test
    void 첫_대결은_CONTINUE로_표시돼_있어도_더_거슬러_올라가지_않는다() {
        List<ChainDuel> duels = List.of(cont(10, 1), cont(11, 2));

        assertThat(FearlessChain.chainDuelIds(duels, 11)).containsExactly(10L, 11L);
    }

    @Test
    void 목록에_없는_대결이면_예외() {
        assertThatThrownBy(() -> FearlessChain.chainDuelIds(List.of(reset(10, 1)), 99))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
