package com.lolinhouse.record;

import java.time.LocalDate;
import java.util.List;

import com.lolinhouse.domain.DuelTeam;
import com.lolinhouse.domain.Position;
import com.lolinhouse.domain.Side;
import com.lolinhouse.record.RecordFilter.ChampionScope;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RecordFilterTest {

    private static final long PLAYER = 1L;
    private static final long OTHER = 2L;

    // 선수 1은 Ahri, 선수 2는 Zed를 픽했고 Lux는 밴만 됐다
    private final List<PlayerRow> players = List.of(
            new PlayerRow(9, PLAYER, DuelTeam.TEAM1, Position.MID, "Ahri", 1, 1, 1, 1, 1),
            new PlayerRow(9, OTHER, DuelTeam.TEAM2, Position.MID, "Zed", 1, 1, 1, 1, 1));
    private final List<BanRow> bans = List.of(new BanRow(9, 1, Side.BLUE, "Lux"));

    private static RecordFilter filter(Long playerId, String champion, ChampionScope scope) {
        return new RecordFilter(null, null, playerId, champion, scope);
    }

    @Test
    void 조건이_없으면_모두_통과한다() {
        assertThat(RecordFilter.none().matchesSet(players, bans)).isTrue();
        assertThat(RecordFilter.none().matchesSet(List.of(), List.of())).isTrue();
    }

    @Test
    void 선수만_주면_그_세트에_뛰었는지_본다() {
        assertThat(filter(PLAYER, null, null).matchesSet(players, bans)).isTrue();
        assertThat(filter(99L, null, null).matchesSet(players, bans)).isFalse();
    }

    @Test
    void 챔피언_ANY는_픽이든_밴이든_맞는다() {
        assertThat(filter(null, "Zed", ChampionScope.ANY).matchesSet(players, bans)).isTrue();
        assertThat(filter(null, "Lux", ChampionScope.ANY).matchesSet(players, bans)).isTrue();
        assertThat(filter(null, "Jhin", ChampionScope.ANY).matchesSet(players, bans)).isFalse();
    }

    @Test
    void 선수와_챔피언_ANY는_같은_세트에_둘_다_있으면_맞는다_다른_선수의_픽도_포함() {
        assertThat(filter(PLAYER, "Zed", ChampionScope.ANY).matchesSet(players, bans)).isTrue();
        assertThat(filter(PLAYER, "Lux", ChampionScope.ANY).matchesSet(players, bans)).isTrue();
    }

    @Test
    void PLAYER_PICK은_선수_본인의_픽만_맞다_다른_선수의_픽과_밴은_아니다() {
        assertThat(filter(PLAYER, "Ahri", ChampionScope.PLAYER_PICK).matchesSet(players, bans)).isTrue();
        assertThat(filter(PLAYER, "Zed", ChampionScope.PLAYER_PICK).matchesSet(players, bans)).isFalse();
        assertThat(filter(PLAYER, "Lux", ChampionScope.PLAYER_PICK).matchesSet(players, bans)).isFalse();
    }

    @Test
    void PLAYER_PICK에_선수가_없으면_픽_행만_본다() {
        assertThat(filter(null, "Zed", ChampionScope.PLAYER_PICK).matchesSet(players, bans)).isTrue();
        assertThat(filter(null, "Lux", ChampionScope.PLAYER_PICK).matchesSet(players, bans)).isFalse();
    }

    @Test
    void 기간은_시작일_양끝_포함이다() {
        RecordFilter period = new RecordFilter(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), null, null, null);

        assertThat(period.periodContains(LocalDate.of(2026, 10, 1))).isTrue();
        assertThat(period.periodContains(LocalDate.of(2026, 10, 31))).isTrue();
        assertThat(period.periodContains(LocalDate.of(2026, 9, 30))).isFalse();
        assertThat(period.periodContains(LocalDate.of(2026, 11, 1))).isFalse();
        assertThat(period.matches(LocalDate.of(2026, 11, 1), players, bans)).isFalse();
        assertThat(period.matches(LocalDate.of(2026, 10, 5), players, bans)).isTrue();
    }

    @Test
    void championScope가_null이면_ANY다() {
        assertThat(filter(null, "Lux", null).championScope()).isEqualTo(ChampionScope.ANY);
    }
}
