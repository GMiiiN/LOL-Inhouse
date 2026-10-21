package com.lolinhouse.session;

import java.time.LocalDate;

import com.lolinhouse.domain.DuelTeam;
import com.lolinhouse.domain.Position;
import com.lolinhouse.domain.Side;
import com.lolinhouse.domain.set.SetFingerprint;
import com.lolinhouse.domain.set.SetFingerprint.TeamTotals;
import com.lolinhouse.session.GameSet.DisplayedTotals;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GameSetTest {

    private static final LocalDate DATE = LocalDate.of(2026, 10, 11);

    private GameSet newSet(DisplayedTotals team1, DisplayedTotals team2, String imageHash, String imageKey) {
        return GameSet.create(1L, 1, Side.BLUE, DuelTeam.TEAM1, DuelTeam.TEAM2, DATE, 1094, team1, team2,
                imageHash, imageKey);
    }

    private GameSet newSet() {
        return newSet(new DisplayedTotals(26, 8, 40, 39852), new DisplayedTotals(8, 26, 12, 29846), null, null);
    }

    @Test
    void fingerprintIsComputedFromDateDurationAndBothTeamTotals() {
        GameSet set = newSet();

        String expected = SetFingerprint.of(DATE, 1094, new TeamTotals(26, 39852), new TeamTotals(8, 29846))
                .canonical();
        assertThat(set.fingerprint()).isEqualTo(expected).isEqualTo("2026-10-11|1094|8:29846|26:39852");
    }

    @Test
    void unknownDurationUsesQuestionMarkInFingerprint() {
        GameSet set = GameSet.create(1L, 1, Side.BLUE, DuelTeam.TEAM1, null, DATE, null,
                new DisplayedTotals(26, 8, 40, 39852), new DisplayedTotals(8, 26, 12, 29846), null, null);

        assertThat(set.durationSec()).isNull();
        assertThat(set.fingerprint()).isEqualTo("2026-10-11|?|8:29846|26:39852");
    }

    @Test
    void fingerprintIsSameWhenTeamsAreSwapped() {
        GameSet original = newSet();
        GameSet swapped = newSet(new DisplayedTotals(8, 26, 12, 29846), new DisplayedTotals(26, 8, 40, 39852), null,
                null);

        assertThat(swapped.fingerprint()).isEqualTo(original.fingerprint());
    }

    @Test
    void displayedTotalsAreStoredPerTeam() {
        GameSet set = newSet();

        assertThat(set.team1Totals()).isEqualTo(new DisplayedTotals(26, 8, 40, 39852));
        assertThat(set.team2Totals()).isEqualTo(new DisplayedTotals(8, 26, 12, 29846));
    }

    @Test
    void imageHashAndKeyMustComeTogether() {
        DisplayedTotals totals = new DisplayedTotals(1, 1, 1, 1);

        assertThatThrownBy(() -> newSet(totals, totals, "ab", null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> newSet(totals, totals, null, "k")).isInstanceOf(IllegalArgumentException.class);
        assertThat(newSet(totals, totals, "ab", "k").imageKey()).isEqualTo("k");
    }

    @Test
    void setNoBelowOneAndMissingValuesAreRejected() {
        DisplayedTotals totals = new DisplayedTotals(1, 1, 1, 1);

        assertThatThrownBy(() -> GameSet.create(1L, 0, Side.BLUE, DuelTeam.TEAM1, null, DATE, 10, totals, totals,
                null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GameSet.create(1L, 1, null, DuelTeam.TEAM1, null, DATE, 10, totals, totals, null,
                null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void topTeamMayBeNullForManualEntry() {
        DisplayedTotals totals = new DisplayedTotals(1, 1, 1, 1);

        GameSet set = GameSet.create(1L, 1, Side.BLUE, DuelTeam.TEAM1, null, DATE, 10, totals, totals, null, null);

        assertThat(set.topTeam()).isNull();
    }

    @Test
    void markDeletedAndRestoreToggleDeletedState() {
        GameSet set = newSet();
        assertThat(set.isDeleted()).isFalse();

        set.markDeleted();
        assertThat(set.isDeleted()).isTrue();
        assertThat(set.deletedAt()).isNotNull();

        set.restore();
        assertThat(set.isDeleted()).isFalse();
        assertThat(set.deletedAt()).isNull();
    }

    @Test
    void deletingTwiceOrRestoringActiveSetIsRejected() {
        GameSet set = newSet();
        assertThatThrownBy(set::restore).isInstanceOf(IllegalStateException.class);

        set.markDeleted();
        assertThatThrownBy(set::markDeleted).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void playersAreListedByTeamThenLaneOrder() {
        GameSet set = newSet();
        set.addPlayer(1L, DuelTeam.TEAM2, Position.SUPPORT, 5, "Lulu", 0, 0, 0, 0, 0);
        set.addPlayer(2L, DuelTeam.TEAM1, Position.MID, 3, "Ahri", 0, 0, 0, 0, 0);
        set.addPlayer(3L, DuelTeam.TEAM1, Position.TOP, 1, "Garen", 0, 0, 0, 0, 0);
        set.addPlayer(4L, DuelTeam.TEAM2, Position.TOP, 1, "Fiora", 0, 0, 0, 0, 0);

        assertThat(set.players()).extracting(GameSetPlayer::playerId).containsExactly(3L, 2L, 4L, 1L);
    }

    @Test
    void playersListIsReadOnly() {
        GameSet set = newSet();

        assertThatThrownBy(() -> set.players().add(null)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> set.bans().add(null)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void addBanKeepsGivenValues() {
        GameSet set = newSet();

        GameSetBan ban = set.addBan(1, Side.BLUE, "Zed");

        assertThat(ban.turn()).isEqualTo(1);
        assertThat(ban.side()).isEqualTo(Side.BLUE);
        assertThat(ban.championKey()).isEqualTo("Zed");
        assertThat(set.bans()).containsExactly(ban);
    }
}
