package com.lolinhouse;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.lolinhouse.auth.AdminAccount;
import com.lolinhouse.auth.AdminAccountRepository;
import com.lolinhouse.domain.DuelTeam;
import com.lolinhouse.domain.Position;
import com.lolinhouse.domain.Side;
import com.lolinhouse.player.Player;
import com.lolinhouse.player.PlayerAlias;
import com.lolinhouse.player.PlayerAliasRepository;
import com.lolinhouse.player.PlayerRepository;
import com.lolinhouse.session.Duel;
import com.lolinhouse.session.DuelRepository;
import com.lolinhouse.session.FearlessMode;
import com.lolinhouse.session.GameSet;
import com.lolinhouse.session.GameSet.DisplayedTotals;
import com.lolinhouse.session.GameSetPlayer;
import com.lolinhouse.session.GameSetRepository;
import com.lolinhouse.session.InhouseSession;
import com.lolinhouse.session.InhouseSessionRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 역할: 엔티티 매핑이 실제 MySQL 스키마와 맞는지(저장 → flush → clear → 재조회) 확인하고, C가 쓸 리포지토리 메서드를 검증한다.
 * flush는 SQL을 DB로 보내는 것, clear는 1차 캐시를 비우는 것이다. 둘 다 해야 "DB에서 실제로 다시 읽은" 값을 본다.
 */
@PersistenceTest
class PersistenceMappingTest {

    @Autowired
    EntityManager em;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    PlayerRepository players;
    @Autowired
    PlayerAliasRepository aliases;
    @Autowired
    InhouseSessionRepository sessions;
    @Autowired
    DuelRepository duels;
    @Autowired
    GameSetRepository gameSets;
    @Autowired
    AdminAccountRepository admins;

    private int dateSeq = 0;

    private void flushAndClear() {
        em.flush();
        em.clear();
    }

    private Duel savedDuel() {
        InhouseSession session = sessions.save(InhouseSession.of(LocalDate.of(2026, 1, 1).plusDays(dateSeq++)));
        return duels.save(Duel.of(session.id(), 1, FearlessMode.RESET));
    }

    private GameSet newSet(Long duelId, int setNo, int durationSec, Side side, DuelTeam winner, DuelTeam top) {
        return GameSet.create(duelId, setNo, side, winner, top, LocalDate.of(2026, 10, 15), durationSec,
                new DisplayedTotals(10, 5, 20, 60000), new DisplayedTotals(5, 10, 8, 50000), null, null);
    }

    // ---------- enum 저장·재조회 ----------

    @Test
    void everyPositionRoundTripsAsEnumNameInPlayerMainPosition() {
        for (Position position : Position.values()) {
            Player saved = players.save(Player.registered("nick-" + position, "이름", position));
            flushAndClear();

            assertThat(players.findById(saved.id()).orElseThrow().mainPosition()).isEqualTo(position);
            assertThat(jdbc.queryForObject("select main_position from player where id = ?", String.class, saved.id()))
                    .isEqualTo(position.name());
        }
    }

    @Test
    void nullMainPositionRoundTripsAsNull() {
        Player saved = players.save(Player.unregistered("nobody"));
        flushAndClear();

        Player found = players.findById(saved.id()).orElseThrow();
        assertThat(found.mainPosition()).isNull();
        assertThat(found.realName()).isNull();
        assertThat(found.isRegistered()).isFalse();
    }

    @Test
    void everySideWinnerAndTopTeamCombinationRoundTripsIncludingNullTop() {
        Duel duel = savedDuel();
        List<Long> ids = new ArrayList<>();
        int setNo = 1;
        for (Side side : Side.values()) {
            for (DuelTeam winner : DuelTeam.values()) {
                List<DuelTeam> tops = new ArrayList<>(List.of(DuelTeam.values()));
                tops.add(null);
                for (DuelTeam top : tops) {
                    ids.add(gameSets.save(newSet(duel.id(), setNo, 1000 + setNo, side, winner, top)).id());
                    setNo++;
                }
            }
        }
        flushAndClear();

        assertThat(ids).hasSize(12);
        for (Long id : ids) {
            GameSet original = gameSets.findById(id).orElseThrow();
            String rawSide = jdbc.queryForObject("select team1_side from game_set where id = ?", String.class, id);
            String rawWinner = jdbc.queryForObject("select winner_team from game_set where id = ?", String.class, id);
            String rawTop = jdbc.queryForObject("select top_team from game_set where id = ?", String.class, id);
            assertThat(rawSide).isEqualTo(original.team1Side().name());
            assertThat(rawWinner).isEqualTo(original.winnerTeam().name());
            assertThat(rawTop).isEqualTo(original.topTeam() == null ? null : original.topTeam().name());
        }
        Set<String> distinctCombinations = new java.util.HashSet<>();
        for (Long id : ids) {
            GameSet set = gameSets.findById(id).orElseThrow();
            distinctCombinations.add(set.team1Side() + "/" + set.winnerTeam() + "/" + set.topTeam());
        }
        assertThat(distinctCombinations).hasSize(12);
    }

    @Test
    void fullGameSetAggregateRoundTripsWithAllPositionsTeamsAndBans() {
        Duel duel = savedDuel();
        GameSet set = newSet(duel.id(), 1, 1094, Side.RED, DuelTeam.TEAM2, DuelTeam.TEAM1);
        long playerSeq = 0;
        for (DuelTeam team : DuelTeam.values()) {
            for (Position position : Position.values()) {
                Player player = players.save(Player.unregistered("p" + (playerSeq++)));
                set.addPlayer(player.id(), team, position, position.ordinal() + 1, "Champ" + position.ordinal(),
                        1 + position.ordinal(), 2, 3, 150, 9000);
            }
        }
        for (int turn = 1; turn <= 10; turn++) {
            set.addBan(turn, turn % 2 == 1 ? Side.BLUE : Side.RED, "Ban" + turn);
        }
        Long id = gameSets.save(set).id();
        flushAndClear();

        GameSet found = gameSets.findById(id).orElseThrow();
        assertThat(found.players()).hasSize(10);
        assertThat(found.players()).extracting(GameSetPlayer::team)
                .containsExactly(DuelTeam.TEAM1, DuelTeam.TEAM1, DuelTeam.TEAM1, DuelTeam.TEAM1, DuelTeam.TEAM1,
                        DuelTeam.TEAM2, DuelTeam.TEAM2, DuelTeam.TEAM2, DuelTeam.TEAM2, DuelTeam.TEAM2);
        assertThat(found.players().subList(0, 5)).extracting(GameSetPlayer::position)
                .containsExactly(Position.values());
        GameSetPlayer jungle = found.players().get(1);
        assertThat(jungle.screenRow()).isEqualTo(2);
        assertThat(jungle.championKey()).isEqualTo("Champ1");
        assertThat(jungle.kills()).isEqualTo(2);
        assertThat(jungle.deaths()).isEqualTo(2);
        assertThat(jungle.assists()).isEqualTo(3);
        assertThat(jungle.cs()).isEqualTo(150);
        assertThat(jungle.gold()).isEqualTo(9000);
        assertThat(found.bans()).hasSize(10);
        assertThat(found.bans()).extracting(b -> b.turn()).containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        assertThat(found.bans().get(0).side()).isEqualTo(Side.BLUE);
        assertThat(found.bans().get(1).side()).isEqualTo(Side.RED);
        assertThat(found.bans().get(9).championKey()).isEqualTo("Ban10");
        assertThat(found.team1Totals()).isEqualTo(new DisplayedTotals(10, 5, 20, 60000));
        assertThat(found.shotDate()).isEqualTo(LocalDate.of(2026, 10, 15));
        assertThat(found.fingerprint()).isEqualTo("2026-10-15|1094|5:50000|10:60000");
        assertThat(jdbc.queryForObject("select count(*) from game_set_player where game_set_id = ?", Integer.class, id))
                .isEqualTo(10);
        assertThat(jdbc.queryForObject("select count(*) from game_set_ban where game_set_id = ?", Integer.class, id))
                .isEqualTo(10);
    }

    @Test
    void manualEntryScreenRowNullRoundTrips() {
        Duel duel = savedDuel();
        GameSet set = newSet(duel.id(), 1, 900, Side.BLUE, DuelTeam.TEAM1, null);
        Player player = players.save(Player.unregistered("manual"));
        set.addPlayer(player.id(), DuelTeam.TEAM1, Position.TOP, null, "Garen", 0, 0, 0, 0, 0);
        Long id = gameSets.save(set).id();
        flushAndClear();

        assertThat(gameSets.findById(id).orElseThrow().players().get(0).screenRow()).isNull();
    }

    // ---------- 소프트 삭제·복구 ----------

    @Test
    void softDeletePersistsAndFreesFingerprintForReRegistrationButRestoreConflicts() {
        Duel duel = savedDuel();
        GameSet first = gameSets.save(newSet(duel.id(), 1, 1094, Side.BLUE, DuelTeam.TEAM1, DuelTeam.TEAM1));
        gameSets.flush();

        first.markDeleted();
        gameSets.flush();
        assertThat(jdbc.queryForObject("select deleted_at is not null from game_set where id = ?", Boolean.class,
                first.id())).isTrue();
        assertThat(jdbc.queryForObject("select active_fingerprint is null from game_set where id = ?", Boolean.class,
                first.id())).isTrue();

        gameSets.saveAndFlush(newSet(duel.id(), 1, 1094, Side.BLUE, DuelTeam.TEAM1, DuelTeam.TEAM1));

        first.restore();
        assertThatThrownBy(() -> gameSets.flush()).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void activeSetsQueryExcludesDeletedAndOrdersBySetNo() {
        Duel duel = savedDuel();
        GameSet one = gameSets.save(newSet(duel.id(), 1, 1001, Side.BLUE, DuelTeam.TEAM1, null));
        GameSet three = gameSets.save(newSet(duel.id(), 3, 1003, Side.BLUE, DuelTeam.TEAM1, null));
        GameSet two = gameSets.save(newSet(duel.id(), 2, 1002, Side.RED, DuelTeam.TEAM2, null));
        two.markDeleted();
        gameSets.flush();

        assertThat(gameSets.findByDuelIdAndDeletedAtIsNullOrderBySetNoAsc(duel.id())).containsExactly(one, three);
    }

    // ---------- 리포지토리: 선수·별칭 ----------

    @Test
    void findByNicknameKeyInFindsAliasesByComparisonKey() {
        Player p = players.save(Player.unregistered("Faker"));
        aliases.save(PlayerAlias.of(p.id(), "Faker"));
        aliases.save(PlayerAlias.of(p.id(), "Hide On Bush"));
        flushAndClear();

        List<PlayerAlias> found = aliases.findByNicknameKeyIn(List.of("faker", "nobody"));

        assertThat(found).extracting(PlayerAlias::nickname).containsExactly("Faker");
    }

    @Test
    void aliasesOfPlayerComeInCreationOrderAndCountMatches() {
        Player p = players.save(Player.unregistered("c"));
        Player other = players.save(Player.unregistered("o"));
        PlayerAlias first = aliases.saveAndFlush(PlayerAlias.of(p.id(), "첫째"));
        PlayerAlias second = aliases.saveAndFlush(PlayerAlias.of(p.id(), "둘째"));
        aliases.saveAndFlush(PlayerAlias.of(other.id(), "남의 것"));

        assertThat(aliases.findByPlayerIdOrderByCreatedAtAscIdAsc(p.id())).containsExactly(first, second);
        assertThat(aliases.countByPlayerId(p.id())).isEqualTo(2);
        assertThat(aliases.findByPlayerIdInOrderByCreatedAtAscIdAsc(List.of(p.id(), other.id()))).hasSize(3);
    }

    @Test
    void findAliasByIdAndPlayerIdDoesNotReturnAnotherPlayersAlias() {
        Player p = players.save(Player.unregistered("a"));
        Player other = players.save(Player.unregistered("b"));
        PlayerAlias alias = aliases.saveAndFlush(PlayerAlias.of(p.id(), "x"));

        assertThat(aliases.findByIdAndPlayerId(alias.id(), p.id())).isPresent();
        assertThat(aliases.findByIdAndPlayerId(alias.id(), other.id())).isEmpty();
    }

    @Test
    void aliasKeyConflictIsReportedAsDataIntegrityViolation() {
        Player a = players.save(Player.unregistered("a"));
        Player b = players.save(Player.unregistered("b"));
        aliases.saveAndFlush(PlayerAlias.of(a.id(), "Faker"));

        assertThatThrownBy(() -> aliases.saveAndFlush(PlayerAlias.of(b.id(), "FAKER")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findByIdForUpdateReturnsPlayerAndIsEmptyForUnknownId() {
        Player p = players.save(Player.unregistered("lock"));
        flushAndClear();

        assertThat(players.findByIdForUpdate(p.id())).get().extracting(Player::gameNickname).isEqualTo("lock");
        assertThat(players.findByIdForUpdate(-1L)).isEmpty();
    }

    @Test
    void playerUpdatedAtMovesOnChangeWhileCreatedAtStays() throws InterruptedException {
        Player p = players.saveAndFlush(Player.unregistered("t"));
        flushAndClear();
        Player loaded = players.findById(p.id()).orElseThrow();
        var created = loaded.createdAt();
        var updatedBefore = loaded.updatedAt();

        Thread.sleep(5);
        loaded.changeRealName("김철수");
        flushAndClear();

        Player reloaded = players.findById(p.id()).orElseThrow();
        assertThat(reloaded.createdAt()).isEqualTo(created);
        assertThat(reloaded.updatedAt()).isAfter(updatedBefore);
        assertThat(reloaded.displayName()).isEqualTo("김철수");
    }

    // ---------- 리포지토리: 내전 묶음·대결 ----------

    @Test
    void findSessionByStartDateAndListNewestFirst() {
        InhouseSession older = sessions.save(InhouseSession.of(LocalDate.of(2026, 10, 1)));
        InhouseSession newer = sessions.save(InhouseSession.of(LocalDate.of(2026, 10, 15)));
        InhouseSession middle = sessions.save(InhouseSession.of(LocalDate.of(2026, 10, 8)));
        flushAndClear();

        assertThat(sessions.findByStartDate(LocalDate.of(2026, 10, 8))).get().extracting(InhouseSession::id)
                .isEqualTo(middle.id());
        assertThat(sessions.findByStartDate(LocalDate.of(2026, 10, 9))).isEmpty();
        assertThat(sessions.findAllByOrderByStartDateDesc()).extracting(InhouseSession::id)
                .containsExactly(newer.id(), middle.id(), older.id());
    }

    @Test
    void duelMaxSeqIsZeroWhenEmptyThenGrowsAndListIsSeqOrdered() {
        InhouseSession session = sessions.save(InhouseSession.of(LocalDate.of(2026, 10, 15)));
        assertThat(duels.findMaxSeq(session.id())).isZero();

        duels.save(Duel.of(session.id(), 1, FearlessMode.RESET));
        duels.save(Duel.of(session.id(), 3, FearlessMode.RESET));
        duels.save(Duel.of(session.id(), 2, FearlessMode.RESET));
        flushAndClear();

        assertThat(duels.findMaxSeq(session.id())).isEqualTo(3);
        assertThat(duels.findBySessionIdOrderBySeqAsc(session.id())).extracting(Duel::seq).containsExactly(1, 2, 3);
    }

    @Test
    void duelCountPerSessionOmitsSessionsWithoutDuels() {
        InhouseSession withDuels = sessions.save(InhouseSession.of(LocalDate.of(2026, 10, 15)));
        InhouseSession empty = sessions.save(InhouseSession.of(LocalDate.of(2026, 10, 16)));
        duels.save(Duel.of(withDuels.id(), 1, FearlessMode.RESET));
        duels.save(Duel.of(withDuels.id(), 2, FearlessMode.RESET));
        flushAndClear();

        var counts = duels.countBySessionIds(List.of(withDuels.id(), empty.id()));

        assertThat(counts).hasSize(1);
        assertThat(counts.get(0).getSessionId()).isEqualTo(withDuels.id());
        assertThat(counts.get(0).getDuelCount()).isEqualTo(2);
    }

    @Test
    void duelSeqDuplicateIsReportedAsDataIntegrityViolation() {
        InhouseSession session = sessions.save(InhouseSession.of(LocalDate.of(2026, 10, 15)));
        duels.saveAndFlush(Duel.of(session.id(), 1, FearlessMode.RESET));

        assertThatThrownBy(() -> duels.saveAndFlush(Duel.of(session.id(), 1, FearlessMode.RESET)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void sessionStartDateDuplicateIsReportedAsDataIntegrityViolation() {
        sessions.saveAndFlush(InhouseSession.of(LocalDate.of(2026, 10, 15)));

        assertThatThrownBy(() -> sessions.saveAndFlush(InhouseSession.of(LocalDate.of(2026, 10, 15))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ---------- 담당자 계정 ----------

    @Test
    void adminAccountRoundTripAndLookupByUsername() {
        admins.saveAndFlush(AdminAccount.of("boss", "$2a$10$hash"));
        flushAndClear();

        AdminAccount found = admins.findByUsername("boss").orElseThrow();
        assertThat(found.passwordHash()).isEqualTo("$2a$10$hash");
        assertThat(found.createdAt()).isNotNull();
        assertThat(admins.findByUsername("nobody")).isEmpty();
        assertThat(admins.count()).isEqualTo(1);
    }
}
