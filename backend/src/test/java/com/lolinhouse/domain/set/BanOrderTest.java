package com.lolinhouse.domain.set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lolinhouse.domain.Side;
import com.lolinhouse.domain.set.BanOrder.BanTurn;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class BanOrderTest {

    private static final List<String> TOP_BANS = List.of("t1", "t2", "t3", "t4", "t5");
    private static final List<String> BOTTOM_BANS = List.of("b1", "b2", "b3", "b4", "b5");

    @Test
    void 위쪽_팀이_블루이면_B1_R1_B2_R2_순으로_번갈아_밴한다() {
        List<BanTurn> turns = BanOrder.build(Side.BLUE, TOP_BANS, BOTTOM_BANS);

        assertThat(turns).extracting(BanTurn::label)
                .containsExactly("B1", "R1", "B2", "R2", "B3", "R3", "B4", "R4", "B5", "R5");
        assertThat(turns).extracting(BanTurn::championKey)
                .containsExactly("t1", "b1", "t2", "b2", "t3", "b3", "t4", "b4", "t5", "b5");
    }

    @Test
    void 위쪽_팀이_레드이면_블루가_된_아래쪽_팀이_먼저_밴한다() {
        List<BanTurn> turns = BanOrder.build(Side.RED, TOP_BANS, BOTTOM_BANS);

        assertThat(turns).extracting(BanTurn::championKey)
                .containsExactly("b1", "t1", "b2", "t2", "b3", "t3", "b4", "t4", "b5", "t5");
    }

    @Test
    void 턴_번호는_1부터_10까지_진영은_블루_레드_번갈아() {
        List<BanTurn> turns = BanOrder.build(Side.BLUE, TOP_BANS, BOTTOM_BANS);

        assertThat(turns).extracting(BanTurn::turn)
                .containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        assertThat(turns).extracting(BanTurn::side)
                .containsExactly(Side.BLUE, Side.RED, Side.BLUE, Side.RED, Side.BLUE,
                        Side.RED, Side.BLUE, Side.RED, Side.BLUE, Side.RED);
    }

    @Test
    void 팀당_밴이_5개가_아니면_예외() {
        List<String> fourBans = List.of("t1", "t2", "t3", "t4");

        assertThatThrownBy(() -> BanOrder.build(Side.BLUE, fourBans, BOTTOM_BANS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("5개");
    }

    @Test
    void 밴이_비어_있으면_예외() {
        List<String> withBlank = Arrays.asList("t1", "t2", " ", "t4", "t5");
        List<String> withNull = Arrays.asList("b1", "b2", "b3", null, "b5");

        assertThatThrownBy(() -> BanOrder.build(Side.BLUE, withBlank, BOTTOM_BANS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("비어");
        assertThatThrownBy(() -> BanOrder.build(Side.BLUE, TOP_BANS, withNull))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("비어");
    }

    @Test
    void 위쪽_팀_진영이_없으면_예외() {
        assertThatThrownBy(() -> BanOrder.build(null, TOP_BANS, BOTTOM_BANS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("진영");
    }
}
