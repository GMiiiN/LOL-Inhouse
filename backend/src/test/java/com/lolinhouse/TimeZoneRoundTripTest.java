package com.lolinhouse;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.TimeZone;
import javax.sql.DataSource;

import com.lolinhouse.domain.DuelTeam;
import com.lolinhouse.domain.Side;
import com.lolinhouse.player.Player;
import com.lolinhouse.player.PlayerRepository;
import com.lolinhouse.session.Duel;
import com.lolinhouse.session.DuelRepository;
import com.lolinhouse.session.FearlessMode;
import com.lolinhouse.session.GameSet;
import com.lolinhouse.session.GameSet.DisplayedTotals;
import com.lolinhouse.session.GameSetRepository;
import com.lolinhouse.session.InhouseSession;
import com.lolinhouse.session.InhouseSessionRepository;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManager;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 역할: "JVM 시간대와 DB 서버 시간대가 무엇이든 시각은 UTC로, 날짜는 그대로" 저장·조회되는지 확인한다(ADR 005).
 * 환경이 일부러 어긋나 있다: JVM = TEST_TZ(기본 Asia/Seoul, CI는 UTC), MySQL 서버 OS = Asia/Seoul, 앱 설정 = UTC.
 * 모든 조회는 flush + clear 후 DB에서 다시 읽고, JDBC로 컬럼 원문(DATE_FORMAT/CAST)을 따로 읽어 대조한다.
 */
@PersistenceTest
class TimeZoneRoundTripTest {

    private static final String RAW_FORMAT = "%Y-%m-%d %H:%i:%s.%f";

    @Autowired
    EntityManager em;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    DataSource dataSource;
    @Autowired
    PlayerRepository players;
    @Autowired
    InhouseSessionRepository sessions;
    @Autowired
    DuelRepository duels;
    @Autowired
    GameSetRepository gameSets;

    private void flushAndClear() {
        em.flush();
        em.clear();
    }

    private String rawCreatedAt(Long playerId) {
        return jdbc.queryForObject("select date_format(created_at, '" + RAW_FORMAT + "') from player where id = ?",
                String.class, playerId);
    }

    // ---------- 환경 확인: 테스트가 의미 있으려면 시간대가 일부러 어긋나 있어야 한다 ----------

    @Test
    void jvmDefaultTimeZoneIsTheOneChosenByTheBuild() {
        String expected = System.getenv("TEST_TZ") != null ? System.getenv("TEST_TZ") : "Asia/Seoul";

        assertThat(TimeZone.getDefault().getID()).isEqualTo(expected);
        assertThat(ZoneId.systemDefault().getId()).isEqualTo(expected);
    }

    @Test
    void mysqlServerOsTimeZoneIsNotUtcSoTheTestCanDetectMistakes() {
        String systemTimeZone = jdbc.queryForObject("select @@system_time_zone", String.class);

        assertThat(systemTimeZone).isNotEqualTo("UTC");
    }

    @Test
    void hikariKeepsCamelCaseDriverPropertyKeysFromApplicationYml() throws Exception {
        HikariDataSource hikari = dataSource.unwrap(HikariDataSource.class);

        assertThat(hikari.getDataSourceProperties().getProperty("connectionTimeZone")).isEqualTo("UTC");
        assertThat(hikari.getDataSourceProperties().getProperty("forceConnectionTimeZoneToSession")).isEqualTo("true");
    }

    @Test
    void hibernateJdbcTimeZoneIsUtc() {
        // Instant 왕복은 이 설정 없이도 통과할 수 있어(Hibernate 7의 Instant 바인더가 UTC Calendar를 쓴다)
        // 설정이 빠지면 확실히 실패하도록 값을 직접 확인한다(Codex M3 리뷰).
        TimeZone jdbcTimeZone = em.getEntityManagerFactory().unwrap(SessionFactoryImplementor.class)
                .getSessionFactoryOptions().getJdbcTimeZone();

        assertThat(jdbcTimeZone).isNotNull();
        assertThat(jdbcTimeZone.toZoneId().normalized()).isEqualTo(ZoneOffset.UTC);
    }

    @Test
    void connectionSessionTimeZoneIsActuallyUtc() {
        // "UTC" 문자열이 아니라 실제 오프셋으로 확인: 세션의 NOW()와 UTC_TIMESTAMP()가 같아야 한다.
        Integer diffMinutes = jdbc.queryForObject("select timestampdiff(minute, utc_timestamp(), now())", Integer.class);
        String sessionTimeZone = jdbc.queryForObject("select @@session.time_zone", String.class);

        assertThat(diffMinutes).isZero();
        assertThat(sessionTimeZone).isNotEqualTo("SYSTEM");
    }

    // ---------- Instant (DATETIME(6), UTC) ----------

    @Test
    void entityCreatedAtIsStoredAsUtcWallClockAndReadsBackEqual() {
        Player saved = players.save(Player.unregistered("tz"));
        flushAndClear();

        Player found = players.findById(saved.id()).orElseThrow();

        String expectedRaw = java.time.LocalDateTime.ofInstant(found.createdAt(), ZoneOffset.UTC)
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS"));
        assertThat(rawCreatedAt(saved.id())).isEqualTo(expectedRaw);
        // 저장된 원문이 "지금의 UTC"와 거의 같다 = 서울(+9시간)이나 JVM 시간대로 저장되지 않았다
        Integer secondsFromUtcNow = jdbc.queryForObject(
                "select abs(timestampdiff(second, created_at, utc_timestamp(6))) from player where id = ?",
                Integer.class, saved.id());
        assertThat(secondsFromUtcNow).isLessThan(60);
        assertThat(found.createdAt()).isBetween(Instant.now().minusSeconds(60), Instant.now().plusSeconds(1));
    }

    @Test
    void entityTimestampsHaveNoSubMicrosecondPrecision() {
        Player saved = players.save(Player.unregistered("micros"));
        GameSet set = GameSet.create(duels.save(Duel.of(sessions.save(InhouseSession.of(LocalDate.of(2026, 10, 15))).id(), 1, FearlessMode.RESET)).id(),
                1, Side.BLUE, DuelTeam.TEAM1, null, LocalDate.of(2026, 10, 15), 100, new DisplayedTotals(1, 1, 1, 1),
                new DisplayedTotals(2, 2, 2, 2), null, null);
        set.markDeleted();
        gameSets.save(set);

        assertThat(saved.createdAt().getNano() % 1000).isZero();
        assertThat(saved.updatedAt().getNano() % 1000).isZero();
        assertThat(set.deletedAt().getNano() % 1000).isZero();
        assertThat(set.createdAt().getNano() % 1000).isZero();
    }

    @Test
    void instantNearSeoulMidnightWrittenByHibernateIsUtcWallClockInDb() {
        // 서울 2026-10-15 00:30:00.123456 = UTC 2026-10-14 15:30:00.123456 (자정 경계: UTC로 보면 전날)
        Instant seoulJustAfterMidnight = Instant.parse("2026-10-14T15:30:00.123456Z");
        em.createNativeQuery("insert into player (game_nickname, created_at, updated_at) values (?1, ?2, ?3)")
                .setParameter(1, "midnight")
                .setParameter(2, seoulJustAfterMidnight)
                .setParameter(3, seoulJustAfterMidnight)
                .executeUpdate();
        flushAndClear();

        String raw = jdbc.queryForObject("select date_format(created_at, '" + RAW_FORMAT
                + "') from player where game_nickname = 'midnight'", String.class);
        assertThat(raw).isEqualTo("2026-10-14 15:30:00.123456");
    }

    @Test
    void utcWallClockInDbReadsBackAsTheSameInstantNearSeoulMidnight() {
        jdbc.update("insert into player (game_nickname, created_at, updated_at) values "
                + "('late', '2026-10-14 15:30:00.123456', '2026-10-14 14:59:59.999999')");
        Long id = jdbc.queryForObject("select id from player where game_nickname = 'late'", Long.class);
        flushAndClear();

        Player found = players.findById(id).orElseThrow();

        assertThat(found.createdAt()).isEqualTo(Instant.parse("2026-10-14T15:30:00.123456Z"));
        assertThat(found.updatedAt()).isEqualTo(Instant.parse("2026-10-14T14:59:59.999999Z"));
        // 같은 순간을 서울 날짜로 보면 하루 뒤다. 이 변환은 저장소가 아니라 표시 단계의 일이다.
        assertThat(found.createdAt().atZone(ZoneId.of("Asia/Seoul")).toLocalDate()).isEqualTo(LocalDate.of(2026, 10, 15));
        assertThat(found.createdAt().atZone(ZoneOffset.UTC).toLocalDate()).isEqualTo(LocalDate.of(2026, 10, 14));
    }

    @Test
    void microsecondValueIsKeptThroughWriteAndRead() {
        Instant oneMicroAfterSecond = Instant.parse("2026-01-01T00:00:00.000001Z");
        em.createNativeQuery("insert into player (game_nickname, created_at, updated_at) values (?1, ?2, ?3)")
                .setParameter(1, "micro")
                .setParameter(2, oneMicroAfterSecond)
                .setParameter(3, oneMicroAfterSecond)
                .executeUpdate();
        Long id = jdbc.queryForObject("select id from player where game_nickname = 'micro'", Long.class);
        flushAndClear();

        assertThat(players.findById(id).orElseThrow().createdAt()).isEqualTo(oneMicroAfterSecond);
        assertThat(rawCreatedAt(id)).isEqualTo("2026-01-01 00:00:00.000001");
    }

    @Test
    void softDeleteTimestampIsStoredAsUtcAndReadsBackEqual() {
        Duel duel = duels.save(Duel.of(sessions.save(InhouseSession.of(LocalDate.of(2026, 10, 15))).id(), 1, FearlessMode.RESET));
        GameSet set = GameSet.create(duel.id(), 1, Side.BLUE, DuelTeam.TEAM1, null, LocalDate.of(2026, 10, 15), 100,
                new DisplayedTotals(1, 1, 1, 1), new DisplayedTotals(2, 2, 2, 2), null, null);
        set.markDeleted();
        Instant deletedAt = set.deletedAt();
        Long id = gameSets.save(set).id();
        flushAndClear();

        assertThat(gameSets.findById(id).orElseThrow().deletedAt()).isEqualTo(deletedAt);
        Integer secondsFromUtcNow = jdbc.queryForObject(
                "select abs(timestampdiff(second, deleted_at, utc_timestamp(6))) from game_set where id = ?",
                Integer.class, id);
        assertThat(secondsFromUtcNow).isLessThan(60);
    }

    // ---------- LocalDate (DATE, 변환 없음) ----------

    @Test
    void localDatesDoNotShiftByADayInAnyJvmTimeZone() {
        List<LocalDate> dates = List.of(LocalDate.of(2026, 10, 15), LocalDate.of(2026, 10, 14), LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31), LocalDate.of(2026, 3, 1), LocalDate.of(2024, 2, 29));
        List<Long> ids = dates.stream().map(date -> sessions.save(InhouseSession.of(date)).id()).toList();
        flushAndClear();

        for (int i = 0; i < dates.size(); i++) {
            InhouseSession found = sessions.findById(ids.get(i)).orElseThrow();
            assertThat(found.startDate()).isEqualTo(dates.get(i));
            assertThat(jdbc.queryForObject("select cast(start_date as char) from inhouse_session where id = ?",
                    String.class, ids.get(i))).isEqualTo(dates.get(i).toString());
        }
    }

    @Test
    void dateWrittenAsRawStringReadsBackAsSameLocalDate() {
        jdbc.update("insert into inhouse_session (start_date, created_at) values ('2026-10-15', '2026-10-15 00:00:00')");
        Long id = jdbc.queryForObject("select id from inhouse_session where start_date = '2026-10-15'", Long.class);
        flushAndClear();

        assertThat(sessions.findById(id).orElseThrow().startDate()).isEqualTo(LocalDate.of(2026, 10, 15));
    }

    @Test
    void gameSetShotDateRoundTripsWithoutShift() {
        Duel duel = duels.save(Duel.of(sessions.save(InhouseSession.of(LocalDate.of(2026, 10, 15))).id(), 1, FearlessMode.RESET));
        GameSet set = GameSet.create(duel.id(), 1, Side.BLUE, DuelTeam.TEAM1, null, LocalDate.of(2026, 10, 15), 100,
                new DisplayedTotals(1, 1, 1, 1), new DisplayedTotals(2, 2, 2, 2), null, null);
        Long id = gameSets.save(set).id();
        flushAndClear();

        assertThat(gameSets.findById(id).orElseThrow().shotDate()).isEqualTo(LocalDate.of(2026, 10, 15));
        assertThat(jdbc.queryForObject("select cast(shot_date as char) from game_set where id = ?", String.class, id))
                .isEqualTo("2026-10-15");
    }
}
