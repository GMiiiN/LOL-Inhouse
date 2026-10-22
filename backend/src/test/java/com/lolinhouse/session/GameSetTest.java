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

    @Test
    void reviseRecomputesFingerprintAndKeepsDuelNumberImageAndCreatedAt() throws Exception {
        GameSet set = newSet(new DisplayedTotals(26, 8, 40, 39852), new DisplayedTotals(8, 26, 12, 29846), "h".repeat(64),
                "k/key.png");
        java.time.Instant before = java.time.Instant.parse("2026-01-01T00:00:00Z");
        var updatedAtField = GameSet.class.getDeclaredField("updatedAt");
        updatedAtField.setAccessible(true);
        updatedAtField.set(set, before);

        set.revise(Side.RED, DuelTeam.TEAM2, DuelTeam.TEAM1, LocalDate.of(2026, 10, 12), null,
                new DisplayedTotals(30, 9, 41, 40000), new DisplayedTotals(9, 30, 10, 30000));

        assertThat(set.fingerprint()).isEqualTo("2026-10-12|?|9:30000|30:40000");
        assertThat(set.durationSec()).isNull();
        assertThat(set.team1Side()).isEqualTo(Side.RED);
        assertThat(set.winnerTeam()).isEqualTo(DuelTeam.TEAM2);
        assertThat(set.topTeam()).isEqualTo(DuelTeam.TEAM1);
        assertThat(set.team1Totals()).isEqualTo(new DisplayedTotals(30, 9, 41, 40000));
        assertThat(set.setNo()).isEqualTo(1);
        assertThat(set.duelId()).isEqualTo(1L);
        assertThat(set.imageHash()).isEqualTo("h".repeat(64));
        assertThat(set.imageKey()).isEqualTo("k/key.png");
        assertThat(set.updatedAt()).isAfter(before);
    }

    @Test
    void reviseWithKnownDurationUsesSecondsInFingerprint() {
        GameSet set = newSet();

        set.revise(Side.BLUE, DuelTeam.TEAM1, DuelTeam.TEAM2, DATE, 1500, new DisplayedTotals(26, 8, 40, 39852),
                new DisplayedTotals(8, 26, 12, 29846));

        assertThat(set.fingerprint()).isEqualTo("2026-10-11|1500|8:29846|26:39852");
    }

    @Test
    void reviseRejectsDeletedSet() {
        GameSet set = newSet();
        set.markDeleted();

        assertThatThrownBy(() -> set.revise(Side.BLUE, DuelTeam.TEAM1, DuelTeam.TEAM2, DATE, 1500,
                new DisplayedTotals(26, 8, 40, 39852), new DisplayedTotals(8, 26, 12, 29846)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void clearRecordsEmptiesPlayersAndBans() {
        GameSet set = newSet();
        set.addPlayer(1L, DuelTeam.TEAM1, com.lolinhouse.domain.Position.TOP, 1, "Zed", 1, 1, 1, 1, 1);
        set.addBan(1, Side.BLUE, "Zed");

        set.clearRecords();

        assertThat(set.players()).isEmpty();
        assertThat(set.bans()).isEmpty();
    }

    @Test
    void 수정_시각은_같은_시각이나_시계_역행에도_반드시_앞으로_간다() {
        java.time.Instant future = java.time.Instant.now().plus(java.time.Duration.ofDays(1))
                .truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        assertThat(GameSet.advance(future)).isEqualTo(future.plus(1, java.time.temporal.ChronoUnit.MICROS));
        assertThat(GameSet.advance(null)).isNotNull();
        java.time.Instant past = java.time.Instant.parse("2000-01-01T00:00:00Z");
        assertThat(GameSet.advance(past)).isAfter(past);
    }
}
