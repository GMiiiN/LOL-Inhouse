package com.lolinhouse.record;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.lolinhouse.IntegrationTest;
import com.lolinhouse.domain.DuelTeam;
import com.lolinhouse.domain.Position;
import com.lolinhouse.domain.Side;
import com.lolinhouse.player.PlayerRepository;
import com.lolinhouse.session.DuelRepository;
import com.lolinhouse.session.FearlessMode;
import com.lolinhouse.session.GameSet;
import com.lolinhouse.session.GameSetRepository;
import com.lolinhouse.session.InhouseSessionRepository;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static com.lolinhouse.record.PublicReadFixtures.champs;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 역할: SetViewAssembler 검증. 서로 다른 묶음·CONTINUE 체인에 걸친 세트를 한 번에 조립해도 피어리스 제한이 체인 전체로 계산되는지,
 * 삭제된 세트가 제한과 결과에서 빠지는지, 화면 필드(진영·승패·라인 순서·밴 순서·이름), 쿼리 수가 세트 수와 무관한지 본다.
 */
@IntegrationTest
class SetViewAssemblerTest {

    private static final AtomicInteger SHIFT = new AtomicInteger();

    @Autowired
    SetViewAssembler assembler;
    @Autowired
    PlayerRepository players;
    @Autowired
    InhouseSessionRepository sessions;
    @Autowired
    DuelRepository duels;
    @Autowired
    GameSetRepository gameSets;
    @Autowired
    EntityManagerFactory entityManagerFactory;

    PublicReadFixtures fixtures;
    List<Long> ids;
    long duelA1;
    long duelA2;
    long duelA3;
    long duelB1;
    long duelB2;
    GameSet a11;
    GameSet a12;
    GameSet a21;
    GameSet a22;
    GameSet a31;
    GameSet b11;
    GameSet b21;

    /**
     * 묶음 A(날짜가 앞): 대결1 RESET [세트1 픽 C00~C09, 세트2 C10~C19], 대결2 CONTINUE [세트1 C20~C29, 세트2 C30~C39], 대결3 RESET [세트1 C40~C49].
     * 묶음 B(날짜가 뒤): 대결1 RESET [세트1 C60~C69], 대결2 CONTINUE [세트1 C70~C79].
     */
    @BeforeEach
    void setUp() {
        fixtures = new PublicReadFixtures(players, sessions, duels, gameSets);
        ids = fixtures.tenPlayers();
        int shift = SHIFT.getAndAdd(1000);
        long sessionA = fixtures.session(PublicReadFixtures.day(2100, shift + 1));
        long sessionB = fixtures.session(PublicReadFixtures.day(2100, shift + 2));
        duelA1 = fixtures.duel(sessionA, 1, FearlessMode.RESET);
        duelA2 = fixtures.duel(sessionA, 2, FearlessMode.CONTINUE);
        duelA3 = fixtures.duel(sessionA, 3, FearlessMode.RESET);
        duelB1 = fixtures.duel(sessionB, 1, FearlessMode.RESET);
        duelB2 = fixtures.duel(sessionB, 2, FearlessMode.CONTINUE);
        a11 = set(duelA1, 1, 0, Side.BLUE, DuelTeam.TEAM2);
        a12 = set(duelA1, 2, 10, Side.RED, DuelTeam.TEAM1);
        a21 = set(duelA2, 1, 20, Side.BLUE, DuelTeam.TEAM1);
        a22 = set(duelA2, 2, 30, Side.RED, DuelTeam.TEAM1);
        a31 = set(duelA3, 1, 40, Side.BLUE, DuelTeam.TEAM1);
        b11 = set(duelB1, 1, 60, Side.BLUE, DuelTeam.TEAM1);
        b21 = set(duelB2, 1, 70, Side.BLUE, DuelTeam.TEAM2);
    }

    private GameSet set(long duelId, int setNo, int firstChampion, Side team1Side, DuelTeam winner) {
        return fixtures.set(duelId, setNo, team1Side, winner, ids, champs(firstChampion, 5),
                champs(firstChampion + 5, 5), champs(100 + firstChampion, 10));
    }

    @Test
    void 서로_다른_묶음과_CONTINUE_체인에_걸친_세트도_체인_전체로_제한을_계산한다() {
        Map<Long, SetView> views = assembler.assemble(List.of(a22.id(), b21.id(), a31.id(), a11.id()));

        // 최근 세트부터: 묶음 B(대결2) → 묶음 A 대결3 → 대결2 세트2 → 대결1 세트1
        assertThat(views.keySet()).containsExactly(b21.id(), a31.id(), a22.id(), a11.id());
        // 표시한 세트는 a22뿐인데도 a21(같은 대결)과 a11·a12(체인 앞 대결) 픽이 전부 들어간다
        assertThat(views.get(a22.id()).fearlessRestricted()).isEqualTo(champs(0, 30));
        assertThat(views.get(b21.id()).fearlessRestricted()).isEqualTo(champs(60, 10));
        assertThat(views.get(a31.id()).fearlessRestricted()).isEmpty();
        assertThat(views.get(a11.id()).fearlessRestricted()).isEmpty();
    }

    @Test
    void 세트_시점_제한은_같은_대결의_앞_세트만_포함한다() {
        Map<Long, SetView> views = assembler.assemble(List.of(a12.id(), a21.id()));

        assertThat(views.get(a12.id()).fearlessRestricted()).isEqualTo(champs(0, 10));
        assertThat(views.get(a21.id()).fearlessRestricted()).isEqualTo(champs(0, 20));
    }

    @Test
    void 삭제된_세트는_결과와_제한_양쪽에서_빠진다() {
        a12.markDeleted();
        gameSets.saveAndFlush(a12);

        Map<Long, SetView> views = assembler.assemble(List.of(a12.id(), a21.id()));

        assertThat(views).containsOnlyKeys(a21.id());
        assertThat(views.get(a21.id()).fearlessRestricted()).isEqualTo(champs(0, 10));
    }

    @Test
    void 없는_id와_빈_목록은_빈_맵이다() {
        assertThat(assembler.assemble(List.of(Long.MAX_VALUE))).isEmpty();
        assertThat(assembler.assemble(List.of())).isEmpty();
    }

    @Test
    void 화면_필드는_진영_승패_라인_순서_밴_순서_이름을_담는다() {
        long unregistered = fixtures.unregisteredPlayer("외부인");
        List<Long> withGuest = new ArrayList<>(ids);
        withGuest.set(0, unregistered);
        GameSet guestSet = fixtures.set(duelB2, 2, Side.RED, DuelTeam.TEAM2, withGuest, champs(80, 5), champs(85, 5),
                champs(90, 10));

        SetView view = assembler.assemble(List.of(guestSet.id())).get(guestSet.id());

        assertThat(view.setNo()).isEqualTo(2);
        assertThat(view.duelId()).isEqualTo(duelB2);
        assertThat(view.duelSeq()).isEqualTo(2);
        assertThat(view.winnerTeam()).isEqualTo(DuelTeam.TEAM2);
        assertThat(view.teams()).extracting(SetView.TeamView::team).containsExactly(DuelTeam.TEAM1, DuelTeam.TEAM2);
        assertThat(view.teams()).extracting(SetView.TeamView::side).containsExactly(Side.RED, Side.BLUE);
        assertThat(view.teams()).extracting(SetView.TeamView::won).containsExactly(false, true);
        assertThat(view.teams().get(0).displayed().kills()).isEqualTo(20);
        assertThat(view.teams().get(1).displayed().gold()).isEqualTo(55000);

        List<SetView.PlayerLine> team1 = view.teams().get(0).players();
        assertThat(team1).extracting(SetView.PlayerLine::position)
                .containsExactly(Position.TOP, Position.JUNGLE, Position.MID, Position.ADC, Position.SUPPORT);
        assertThat(team1.get(0).registered()).isFalse();
        assertThat(team1.get(0).displayName()).startsWith("외부인");
        assertThat(team1.get(1).registered()).isTrue();
        assertThat(team1.get(1).displayName()).startsWith("선수1");
        assertThat(team1.get(0).championKey()).isEqualTo("C80");

        assertThat(view.bans()).extracting(SetView.BanView::turn).containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        assertThat(view.bans().get(0).side()).isEqualTo(Side.BLUE);
        assertThat(view.bans().get(1).side()).isEqualTo(Side.RED);
        assertThat(view.bans().get(9).championKey()).isEqualTo("C99");
        // 같은 대결 세트1(b21)의 픽 + 체인 앞 대결(b11) 픽
        assertThat(view.fearlessRestricted()).isEqualTo(
                java.util.stream.Stream.concat(champs(60, 10).stream(), champs(70, 10).stream()).toList());
    }

    @Test
    void 쿼리_수는_세트_수와_상관없이_같다() {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);

        statistics.clear();
        assembler.assemble(List.of(a11.id(), a12.id(), a21.id()));
        long three = statistics.getPrepareStatementCount();

        statistics.clear();
        assembler.assemble(List.of(a11.id(), a12.id(), a21.id(), a22.id(), a31.id(), b11.id(), b21.id()));
        long seven = statistics.getPrepareStatementCount();

        assertThat(three).isEqualTo(6);
        assertThat(seven).isEqualTo(three);
    }
}
