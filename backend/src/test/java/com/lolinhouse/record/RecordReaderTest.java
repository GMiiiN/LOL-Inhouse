package com.lolinhouse.record;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.lolinhouse.IntegrationTest;
import com.lolinhouse.domain.DuelTeam;
import com.lolinhouse.domain.Side;
import com.lolinhouse.domain.stats.PlayerSetRecord;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 역할: 공개 조회 로더(RecordReader)의 프로젝션 쿼리 검증. 엔티티 조인 + record 생성자 표현식이 Hibernate 7에서 동작하는지,
 * 삭제된 세트가 빠지는지, 기간(시작일 양끝 포함)과 쿼리 수 고정을 본다. 날짜는 2101년 구간만 쓴다.
 */
@IntegrationTest
class RecordReaderTest {

    private static final int YEAR = 2101;
    private static final AtomicInteger SHIFT = new AtomicInteger();

    @Autowired
    RecordReader reader;
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
    LocalDate day1;
    LocalDate day2;
    List<Long> ids;
    GameSet s1;
    GameSet s2;
    GameSet deleted;
    GameSet s3;

    @BeforeEach
    void setUp() {
        fixtures = new PublicReadFixtures(players, sessions, duels, gameSets);
        // 내전 시작일은 UNIQUE라 테스트마다 겹치지 않는 날짜 구간을 쓴다
        int shift = SHIFT.getAndAdd(1000);
        day1 = PublicReadFixtures.day(YEAR, 10 + shift);
        day2 = PublicReadFixtures.day(YEAR, 20 + shift);
        ids = fixtures.tenPlayers();
        long a = fixtures.session(day1);
        long duel1 = fixtures.duel(a, 1, FearlessMode.RESET);
        long duel2 = fixtures.duel(a, 2, FearlessMode.CONTINUE);
        long b = fixtures.session(day2);
        long duel3 = fixtures.duel(b, 1, FearlessMode.RESET);
        s1 = fixtures.set(duel1, 1, Side.BLUE, DuelTeam.TEAM1, ids, PublicReadFixtures.champs(0, 5),
                PublicReadFixtures.champs(5, 5), PublicReadFixtures.champs(50, 10));
        s2 = fixtures.set(duel2, 1, Side.RED, DuelTeam.TEAM2, ids, PublicReadFixtures.champs(10, 5),
                PublicReadFixtures.champs(15, 5), PublicReadFixtures.champs(60, 10));
        deleted = fixtures.set(duel2, 2, Side.BLUE, DuelTeam.TEAM1, ids, PublicReadFixtures.champs(20, 5),
                PublicReadFixtures.champs(25, 5), PublicReadFixtures.champs(70, 10));
        deleted.markDeleted();
        gameSets.saveAndFlush(deleted);
        s3 = fixtures.set(duel3, 1, Side.BLUE, DuelTeam.TEAM1, ids, PublicReadFixtures.champs(30, 5),
                PublicReadFixtures.champs(35, 5), PublicReadFixtures.champs(80, 10));
    }

    @Test
    void 세트_프로젝션은_대결과_묶음_정보를_채우고_삭제된_세트를_뺀다_최근_것부터() {
        List<SetRow> rows = reader.setsInPeriod(day1, day2);

        assertThat(rows).extracting(SetRow::setId).containsExactly(s3.id(), s2.id(), s1.id());
        SetRow row = rows.get(1);
        assertThat(row.duelSeq()).isEqualTo(2);
        assertThat(row.fearlessMode()).isEqualTo(FearlessMode.CONTINUE);
        assertThat(row.startDate()).isEqualTo(day1);
        assertThat(row.team1Side()).isEqualTo(Side.RED);
        assertThat(row.winnerTeam()).isEqualTo(DuelTeam.TEAM2);
        assertThat(row.team1Kills()).isEqualTo(20);
        assertThat(row.team2Gold()).isEqualTo(55000);
        assertThat(row.sessionId()).isEqualTo(rows.get(2).sessionId());
    }

    @Test
    void 기간은_시작일_기준_양끝_포함이고_한쪽만_줘도_된다() {
        assertThat(reader.setsInPeriod(day1, day1)).extracting(SetRow::setId).containsExactly(s2.id(), s1.id());
        assertThat(reader.setsInPeriod(day2, day2)).extracting(SetRow::setId).containsExactly(s3.id());
        assertThat(reader.setsInPeriod(day2, null)).extracting(SetRow::setId).contains(s3.id()).doesNotContain(s1.id());
        assertThat(reader.setsInPeriod(null, day1)).extracting(SetRow::setId)
                .contains(s1.id(), s2.id()).doesNotContain(s3.id());
        assertThat(reader.setsInPeriod(day1.plusDays(1), day2.minusDays(1))).isEmpty();
    }

    @Test
    void 선수_밴_행은_세트마다_10개씩이고_삭제된_세트는_없다() {
        List<Long> setIds = List.of(s1.id(), s2.id(), deleted.id());

        assertThat(reader.playersOfSets(setIds)).hasSize(20).allMatch(p -> p.setId() != deleted.id());
        List<BanRow> bans = reader.bansOfSets(List.of(s1.id()));
        assertThat(bans).extracting(BanRow::turn).containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        assertThat(bans.get(0).side()).isEqualTo(Side.BLUE);
        assertThat(bans.get(1).side()).isEqualTo(Side.RED);
        assertThat(bans.get(0).championKey()).isEqualTo("C50");
        assertThat(reader.setsByIds(List.of(deleted.id()))).isEmpty();
    }

    @Test
    void 통계_입력은_승패를_계산하고_세트_메타와_밴을_담는다() {
        LoadedRecords loaded = reader.playerRecords(day1, day2);

        assertThat(loaded.totalSets()).isEqualTo(3);
        assertThat(loaded.records()).hasSize(30);
        assertThat(loaded.bans()).hasSize(30);
        // s1은 TEAM1 승: TEAM1 선수는 win, TEAM2 선수는 loss
        PlayerSetRecord top1 = loaded.records().stream()
                .filter(r -> r.setId().equals(String.valueOf(s1.id())) && r.playerId().equals(String.valueOf(ids.get(0))))
                .findFirst().orElseThrow();
        assertThat(top1.win()).isTrue();
        assertThat(top1.champion()).isEqualTo("C00");
        assertThat(top1.duelId()).isEqualTo(String.valueOf(s1.duelId()));
        PlayerSetRecord top2 = loaded.records().stream()
                .filter(r -> r.setId().equals(String.valueOf(s1.id())) && r.playerId().equals(String.valueOf(ids.get(5))))
                .findFirst().orElseThrow();
        assertThat(top2.win()).isFalse();
        // s2는 TEAM2 승
        PlayerSetRecord inS2 = loaded.records().stream()
                .filter(r -> r.setId().equals(String.valueOf(s2.id())) && r.playerId().equals(String.valueOf(ids.get(5))))
                .findFirst().orElseThrow();
        assertThat(inS2.win()).isTrue();

        assertThat(loaded.setMeta().get(s2.id())).isEqualTo(new LoadedRecords.SetMeta(day1, s2.duelId(), 2, 1));
        assertThat(loaded.names()).containsOnlyKeys(ids.toArray(Long[]::new));
        assertThat(loaded.recentSetIds(2)).containsExactly(s3.id(), s2.id());
        assertThat(loaded.recentSetIdsOfPlayer(ids.get(0), 5)).containsExactly(s3.id(), s2.id(), s1.id());
        assertThat(loaded.recentSetIdsOfPlayer(Long.MAX_VALUE, 5)).isEmpty();
    }

    @Test
    void 통계_입력_쿼리는_세트_수와_상관없이_4개다() {
        Statistics statistics = statistics();

        statistics.clear();
        reader.playerRecords(day1, day1);
        long small = statistics.getPrepareStatementCount();
        statistics.clear();
        reader.playerRecords(day1, day2);
        long large = statistics.getPrepareStatementCount();

        assertThat(small).isEqualTo(4);
        assertThat(large).isEqualTo(4);
    }

    @Test
    void 이름은_등록_선수는_실명_미등록은_닉네임이고_등록_선수_목록에는_미등록이_없다() {
        long registered = fixtures.registeredPlayer("홍길동");
        long unregistered = fixtures.unregisteredPlayer("외부인");

        var names = reader.namesOf(List.of(registered, unregistered));

        assertThat(names.get(registered).registered()).isTrue();
        assertThat(names.get(registered).displayName()).startsWith("홍길동");
        assertThat(names.get(unregistered).registered()).isFalse();
        assertThat(names.get(unregistered).displayName()).startsWith("외부인");
        assertThat(reader.registeredNames()).containsKey(registered).doesNotContainKey(unregistered);
    }

    @Test
    void 기록이_없는_기간은_빈_입력이다() {
        LoadedRecords loaded = reader.playerRecords(day2.plusDays(100), day2.plusDays(200));

        assertThat(loaded.totalSets()).isZero();
        assertThat(loaded.records()).isEmpty();
        assertThat(loaded.names()).isEmpty();
        assertThat(loaded.recentSetIds(5)).isEmpty();
    }

    private Statistics statistics() {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        return statistics;
    }
}
