package com.lolinhouse.domain.tier;

import java.util.List;
import java.util.Map;

import com.lolinhouse.domain.Position;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 역할: Phase 0 계약의 손계산 사례(설계 §7.2). 점수 공식, rank, 대체 점수, 마지막 변경 판정.
 * 작업자 A가 같은 패키지에 사례를 더한다.
 */
class TierDomainTest {

    private static LaneTier lt(int tier, TierSub sub) {
        return new LaneTier(tier, sub);
    }

    @Test
    void 기본표_점수는_손계산과_같다() {
        ScoreTable table = ScoreTable.DEFAULT;

        assertThat(table.points(lt(1, TierSub.HIGH), Position.TOP)).isEqualTo(63);
        assertThat(table.points(lt(3, TierSub.MID), Position.ADC)).isEqualTo(40);
        assertThat(table.points(lt(6, TierSub.LOW), Position.SUPPORT)).isEqualTo(14);
    }

    @Test
    void 티어_없는_라인의_대체_점수는_6티어_하다() {
        ScoreTable table = ScoreTable.DEFAULT;

        assertThat(table.fallbackPoints(Position.TOP)).isEqualTo(17);
        assertThat(table.fallbackPoints(Position.ADC)).isEqualTo(13);
        assertThat(table.fallbackPoints(Position.SUPPORT)).isEqualTo(14);
    }

    @Test
    void rank는_1티어_상이_20_6티어_하가_3이다() {
        assertThat(lt(1, TierSub.HIGH).rank()).isEqualTo(20);
        assertThat(lt(6, TierSub.LOW).rank()).isEqualTo(3);
        assertThat(lt(3, TierSub.HIGH).rank()).isGreaterThan(lt(3, TierSub.MID).rank());
    }

    @Test
    void 티어_범위_밖이면_거절한다() {
        assertThatThrownBy(() -> lt(0, TierSub.MID)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> lt(7, TierSub.MID)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 점수표_칸이_모자라거나_범위_밖이면_거절한다() {
        assertThatThrownBy(() -> new ScoreTable(List.of(1, 2, 3), ScoreTable.DEFAULT.sub(), ScoreTable.DEFAULT.lane()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ScoreTable(List.of(1, 2, 3, 4, 5, 1000), ScoreTable.DEFAULT.sub(),
                ScoreTable.DEFAULT.lane())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ScoreTable(ScoreTable.DEFAULT.base(), Map.of(TierSub.HIGH, 1),
                ScoreTable.DEFAULT.lane())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 마지막_변경_판정() {
        LaneTier old = lt(3, TierSub.MID);
        LastChange existing = new LastChange(LastChange.Direction.DOWN, lt(2, TierSub.LOW));

        assertThat(LastChange.afterSave(null, old, null)).as("새 라인").isNull();
        assertThat(LastChange.afterSave(old, old, existing)).as("같은 값").isSameAs(existing);
        assertThat(LastChange.afterSave(old, lt(2, TierSub.LOW), null))
                .isEqualTo(new LastChange(LastChange.Direction.UP, old));
        assertThat(LastChange.afterSave(old, lt(3, TierSub.LOW), existing))
                .as("같은 티어 안 하락").isEqualTo(new LastChange(LastChange.Direction.DOWN, old));
    }
}
