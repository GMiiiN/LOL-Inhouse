package com.lolinhouse;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * 역할: V2·V3·V4 스키마의 제약(UNIQUE·CHECK·FK·생성 컬럼)이 DB에서 실제로 위반을 막는지 JdbcTemplate으로 직접 INSERT해 확인한다.
 * 엔티티를 거치지 않는 이유: 앱 코드가 잘못되거나 동시 요청이 와도 DB가 마지막 방어선이어야 하기 때문이다.
 * 위반 한 가지당 테스트 하나. 오류는 MySQL 오류 코드로 단정한다(1062 중복, 3819 CHECK, 1451/1452 FK, 3105 생성 컬럼 쓰기).
 */
@PersistenceTest
class SchemaConstraintTest {

    private static final int DUPLICATE_ENTRY = 1062;
    private static final int CHECK_VIOLATED = 3819;
    private static final int PARENT_ROW_REFERENCED = 1451;
    private static final int CHILD_ROW_NO_PARENT = 1452;
    private static final int GENERATED_COLUMN_WRITE = 3105;
    private static final String TS = "2026-10-15 00:00:00.000000";

    @Autowired
    JdbcTemplate jdbc;

    private int dateSeq = 0;

    // ---------- 도우미 ----------

    private long insert(String table, Map<String, Object> row) {
        String columns = String.join(", ", row.keySet());
        String marks = String.join(", ", row.keySet().stream().map(c -> "?").toList());
        String sql = "insert into " + table + " (" + columns + ") values (" + marks + ")";
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            int index = 1;
            for (Object value : row.values()) {
                ps.setObject(index++, value);
            }
            return ps;
        }, keys);
        return keys.getKey().longValue();
    }

    private Map<String, Object> row(Object... keyValues) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            row.put((String) keyValues[i], keyValues[i + 1]);
        }
        return row;
    }

    private Map<String, Object> with(Map<String, Object> base, String column, Object value) {
        Map<String, Object> copy = new LinkedHashMap<>(base);
        copy.put(column, value);
        return copy;
    }

    private Map<String, Object> playerRow() {
        return row("game_nickname", "닉네임", "real_name", null, "main_position", null, "created_at", TS,
                "updated_at", TS);
    }

    private long player() {
        return insert("player", playerRow());
    }

    private Map<String, Object> aliasRow(long playerId, String nickname, String key) {
        return row("player_id", playerId, "nickname", nickname, "nickname_key", key, "created_at", TS);
    }

    private long session() {
        return insert("inhouse_session", row("start_date", "2026-01-" + String.format("%02d", ++dateSeq), "created_at", TS));
    }

    private long duel() {
        return insert("duel", row("session_id", session(), "seq", 1, "created_at", TS));
    }

    private Map<String, Object> gameSetRow(long duelId, int setNo, String fingerprint) {
        return row("duel_id", duelId, "set_no", setNo, "team1_side", "BLUE", "winner_team", "TEAM1",
                "top_team", "TEAM1", "shot_date", "2026-10-15", "duration_sec", 1094,
                "team1_displayed_kills", 10, "team1_displayed_deaths", 5, "team1_displayed_assists", 20,
                "team1_displayed_gold", 60000, "team2_displayed_kills", 5, "team2_displayed_deaths", 10,
                "team2_displayed_assists", 8, "team2_displayed_gold", 50000, "fingerprint", fingerprint,
                "image_hash", null, "image_key", null, "deleted_at", null, "created_at", TS, "updated_at", TS);
    }

    private long gameSet(long duelId, int setNo, String fingerprint) {
        return insert("game_set", gameSetRow(duelId, setNo, fingerprint));
    }

    private Map<String, Object> playerLine(long gameSetId, long playerId, String team, String position,
            Integer screenRow) {
        return row("game_set_id", gameSetId, "player_id", playerId, "team", team, "position", position,
                "screen_row", screenRow, "champion_key", "Ahri", "kills", 1, "deaths", 2, "assists", 3, "cs", 4,
                "gold", 5);
    }

    private Map<String, Object> ban(long gameSetId, int turn, String side) {
        return row("game_set_id", gameSetId, "turn", turn, "side", side, "champion_key", "Zed");
    }

    /** 실행하다 발생한 MySQL 오류 코드를 돌려준다. 예외가 없으면 테스트를 실패시킨다. */
    private int mysqlErrorOf(ThrowingCallable action) {
        Throwable thrown = catchThrowable(action);
        assertThat(thrown).as("제약 위반 예외가 나야 한다").isInstanceOf(DataAccessException.class);
        for (Throwable t = thrown; t != null; t = t.getCause()) {
            if (t instanceof SQLException sql) {
                return sql.getErrorCode();
            }
        }
        throw new AssertionError("원인에 SQLException이 없음: " + thrown);
    }

    // ---------- player_alias ----------

    @Test
    void aliasNicknameKeyDuplicateIsRejected() {
        long a = player();
        long b = player();
        insert("player_alias", aliasRow(a, "Faker", "faker"));

        assertThat(mysqlErrorOf(() -> insert("player_alias", aliasRow(b, "FAKER", "faker"))))
                .isEqualTo(DUPLICATE_ENTRY);
    }

    @Test
    void aliasKeyUsesBinaryCollationSoAccentedLetterIsNotPlainLetter() {
        long p = player();

        // ai_ci 콜레이션이면 é와 e가 같다고 보고 둘째 INSERT가 1062로 실패한다. 바이너리라 서로 다른 키다.
        insert("player_alias", aliasRow(p, "café", "e"));
        insert("player_alias", aliasRow(p, "cafe", "é"));

        assertThat(jdbc.queryForObject("select count(*) from player_alias where player_id = ?", Integer.class, p))
                .isEqualTo(2);
    }

    @Test
    void aliasKeyDoesNotLowercaseByItselfSoJavaMustProduceTheKey() {
        long p = player();

        // DB는 대소문자를 합쳐 주지 않는다. 그래서 키는 반드시 Java(comparisonKey)가 소문자로 만들어 넣는다.
        insert("player_alias", aliasRow(p, "Faker", "faker"));
        insert("player_alias", aliasRow(p, "Faker2", "Faker"));

        assertThat(jdbc.queryForObject("select count(*) from player_alias where player_id = ?", Integer.class, p))
                .isEqualTo(2);
    }

    @Test
    void aliasKeyIsNoPadSoTrailingSpaceMakesDifferentKey() {
        long p = player();

        insert("player_alias", aliasRow(p, "ab", "ab"));
        insert("player_alias", aliasRow(p, "ab ", "ab "));

        assertThat(jdbc.queryForObject("select count(*) from player_alias where player_id = ?", Integer.class, p))
                .isEqualTo(2);
    }

    @Test
    void aliasRequiresExistingPlayer() {
        assertThat(mysqlErrorOf(() -> insert("player_alias", aliasRow(999_999L, "x", "x"))))
                .isEqualTo(CHILD_ROW_NO_PARENT);
    }

    // ---------- player ----------

    @ParameterizedTest
    @ValueSource(strings = {"top", "Top", "BOTTOM"})
    void playerMainPositionMustBeExactUpperCaseEnumValue(String value) {
        assertThat(mysqlErrorOf(() -> insert("player", with(playerRow(), "main_position", value))))
                .isEqualTo(CHECK_VIOLATED);
    }

    @Test
    void playerRealNameCannotBeBlank() {
        assertThat(mysqlErrorOf(() -> insert("player", with(playerRow(), "real_name", "   "))))
                .isEqualTo(CHECK_VIOLATED);
    }

    // ---------- admin_account ----------

    @Test
    void adminUsernameMustBeUnique() {
        insert("admin_account", row("username", "boss", "password_hash", "x", "created_at", TS));

        assertThat(mysqlErrorOf(() -> insert("admin_account",
                row("username", "boss", "password_hash", "y", "created_at", TS)))).isEqualTo(DUPLICATE_ENTRY);
    }

    // ---------- inhouse_session / duel ----------

    @Test
    void sessionStartDateMustBeUnique() {
        insert("inhouse_session", row("start_date", "2026-10-15", "created_at", TS));

        assertThat(mysqlErrorOf(() -> insert("inhouse_session",
                row("start_date", "2026-10-15", "created_at", TS)))).isEqualTo(DUPLICATE_ENTRY);
    }

    @Test
    void duelSeqMustBeUniquePerSession() {
        long s = session();
        insert("duel", row("session_id", s, "seq", 1, "created_at", TS));

        assertThat(mysqlErrorOf(() -> insert("duel", row("session_id", s, "seq", 1, "created_at", TS))))
                .isEqualTo(DUPLICATE_ENTRY);
    }

    @Test
    void duelSeqMayRepeatAcrossSessions() {
        insert("duel", row("session_id", session(), "seq", 1, "created_at", TS));
        insert("duel", row("session_id", session(), "seq", 1, "created_at", TS));
    }

    @Test
    void duelSeqMustBePositive() {
        long s = session();

        assertThat(mysqlErrorOf(() -> insert("duel", row("session_id", s, "seq", 0, "created_at", TS))))
                .isEqualTo(CHECK_VIOLATED);
    }

    @Test
    void duelRequiresExistingSession() {
        assertThat(mysqlErrorOf(() -> insert("duel", row("session_id", 999_999L, "seq", 1, "created_at", TS))))
                .isEqualTo(CHILD_ROW_NO_PARENT);
    }

    // ---------- game_set: 유효 세트 안에서만 UNIQUE (생성 컬럼) ----------

    @Test
    void activeFingerprintDuplicateIsRejectedEvenAcrossDuels() {
        gameSet(duel(), 1, "2026-10-15|1094|8:29846|26:39852");

        assertThat(mysqlErrorOf(() -> gameSet(duel(), 1, "2026-10-15|1094|8:29846|26:39852")))
                .isEqualTo(DUPLICATE_ENTRY);
    }

    @Test
    void deletedSetDoesNotBlockReRegistrationOfSameFingerprint() {
        long duel = duel();
        long old = gameSet(duel, 1, "fp-1");
        jdbc.update("update game_set set deleted_at = ? where id = ?", TS, old);

        // 삭제된 세트의 active_fingerprint는 NULL이고 UNIQUE는 NULL끼리 충돌하지 않는다.
        long again = gameSet(duel, 1, "fp-1");

        assertThat(again).isNotEqualTo(old);
    }

    @Test
    void restoringDeletedSetFailsWhenActiveSetWithSameFingerprintExists() {
        long duel = duel();
        long old = gameSet(duel, 1, "fp-2");
        jdbc.update("update game_set set deleted_at = ? where id = ?", TS, old);
        gameSet(duel, 2, "fp-2");

        // 복구 = deleted_at을 NULL로. 생성 컬럼이 다시 계산되며 UNIQUE에 걸린다.
        assertThat(mysqlErrorOf(() -> jdbc.update("update game_set set deleted_at = null where id = ?", old)))
                .isEqualTo(DUPLICATE_ENTRY);
    }

    @Test
    void activeSetNoMustBeUniquePerDuel() {
        long duel = duel();
        gameSet(duel, 1, "fp-a");

        assertThat(mysqlErrorOf(() -> gameSet(duel, 1, "fp-b"))).isEqualTo(DUPLICATE_ENTRY);
    }

    @Test
    void setNoMayRepeatInAnotherDuel() {
        gameSet(duel(), 1, "fp-c");
        gameSet(duel(), 1, "fp-d");
    }

    @Test
    void deletedSetDoesNotBlockSameSetNoAndRestoreFailsOnSetNoConflict() {
        long duel = duel();
        long old = gameSet(duel, 1, "fp-e");
        jdbc.update("update game_set set deleted_at = ? where id = ?", TS, old);
        gameSet(duel, 1, "fp-f");

        assertThat(mysqlErrorOf(() -> jdbc.update("update game_set set deleted_at = null where id = ?", old)))
                .isEqualTo(DUPLICATE_ENTRY);
    }

    @Test
    void generatedColumnsCannotBeWrittenDirectly() {
        long duel = duel();

        assertThat(mysqlErrorOf(() -> insert("game_set", with(gameSetRow(duel, 1, "fp-g"), "active_set_no", 7))))
                .isEqualTo(GENERATED_COLUMN_WRITE);
    }

    @Test
    void asciiBinFingerprintIgnoresTrailingSpacesBecauseOfPadSpace() {
        // ascii_bin은 PAD SPACE: 끝 공백만 다른 두 값을 같다고 본다. JPA 경로에서는 지문이 공백 없이 만들어져 발생하지 않는다(설계 §9-6).
        gameSet(duel(), 1, "fp-pad");

        assertThat(mysqlErrorOf(() -> gameSet(duel(), 1, "fp-pad  "))).isEqualTo(DUPLICATE_ENTRY);
    }

    @Test
    void asciiBinCheckAcceptsEnumValueWithTrailingSpaceBecauseOfPadSpace() {
        // 같은 이유로 CHECK (main_position IN ('TOP', ...))도 'TOP '를 통과시킨다. JPA는 enum 이름만 쓰므로 이 값이 들어올 길이 없다.
        insert("player", with(playerRow(), "main_position", "TOP "));
    }

    // ---------- game_set: CHECK ----------

    @ParameterizedTest
    @ValueSource(strings = {"blue", "Blue", "GRAY"})
    void gameSetTeam1SideMustBeBlueOrRed(String value) {
        long duel = duel();

        assertThat(mysqlErrorOf(() -> insert("game_set", with(gameSetRow(duel, 1, "fp"), "team1_side", value))))
                .isEqualTo(CHECK_VIOLATED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"team1", "TEAM3", "BLUE"})
    void gameSetWinnerTeamMustBeTeam1OrTeam2(String value) {
        long duel = duel();

        assertThat(mysqlErrorOf(() -> insert("game_set", with(gameSetRow(duel, 1, "fp"), "winner_team", value))))
                .isEqualTo(CHECK_VIOLATED);
    }

    @Test
    void gameSetTopTeamMustBeTeamValueButMayBeNull() {
        long duel = duel();
        insert("game_set", with(gameSetRow(duel, 1, "fp-null-top"), "top_team", null));

        assertThat(mysqlErrorOf(() -> insert("game_set", with(gameSetRow(duel, 2, "fp-bad-top"), "top_team", "team1"))))
                .isEqualTo(CHECK_VIOLATED);
    }

    @Test
    void gameSetDurationMustBePositive() {
        long duel = duel();

        assertThat(mysqlErrorOf(() -> insert("game_set", with(gameSetRow(duel, 1, "fp"), "duration_sec", 0))))
                .isEqualTo(CHECK_VIOLATED);
    }

    @Test
    void gameSetDurationMayBeNullButNotZero() {
        long duel = duel();
        insert("game_set", with(gameSetRow(duel, 1, "fp-null-duration"), "duration_sec", null));

        assertThat(mysqlErrorOf(() -> insert("game_set", with(gameSetRow(duel, 2, "fp-neg"), "duration_sec", -5))))
                .isEqualTo(CHECK_VIOLATED);
    }

    @Test
    void gameSetSetNoMustBePositive() {
        long duel = duel();

        assertThat(mysqlErrorOf(() -> insert("game_set", gameSetRow(duel, 0, "fp")))).isEqualTo(CHECK_VIOLATED);
    }

    @Test
    void gameSetDisplayedTotalsCannotBeNegative() {
        long duel = duel();

        assertThat(mysqlErrorOf(() -> insert("game_set", with(gameSetRow(duel, 1, "fp"), "team2_displayed_gold", -1))))
                .isEqualTo(CHECK_VIOLATED);
    }

    @Test
    void gameSetImageHashAndKeyMustComeTogether() {
        long duel = duel();

        assertThat(mysqlErrorOf(() -> insert("game_set", with(gameSetRow(duel, 1, "fp"), "image_hash", "ab"))))
                .isEqualTo(CHECK_VIOLATED);
    }

    @Test
    void gameSetRequiresExistingDuel() {
        assertThat(mysqlErrorOf(() -> gameSet(999_999L, 1, "fp"))).isEqualTo(CHILD_ROW_NO_PARENT);
    }

    // ---------- game_set_player ----------

    @Test
    void sameDuelPlayerCannotAppearTwiceInOneSet() {
        long set = gameSet(duel(), 1, "fp");
        long p = player();
        insert("game_set_player", playerLine(set, p, "TEAM1", "TOP", 1));

        assertThat(mysqlErrorOf(() -> insert("game_set_player", playerLine(set, p, "TEAM2", "MID", 2))))
                .isEqualTo(DUPLICATE_ENTRY);
    }

    @Test
    void positionMustBeUniquePerTeamInSet() {
        long set = gameSet(duel(), 1, "fp");
        insert("game_set_player", playerLine(set, player(), "TEAM1", "TOP", 1));

        assertThat(mysqlErrorOf(() -> insert("game_set_player", playerLine(set, player(), "TEAM1", "TOP", 2))))
                .isEqualTo(DUPLICATE_ENTRY);
    }

    @Test
    void samePositionIsAllowedOnOtherTeam() {
        long set = gameSet(duel(), 1, "fp");
        insert("game_set_player", playerLine(set, player(), "TEAM1", "TOP", 1));
        insert("game_set_player", playerLine(set, player(), "TEAM2", "TOP", 1));
    }

    @Test
    void screenRowMustBeUniquePerTeamInSet() {
        long set = gameSet(duel(), 1, "fp");
        insert("game_set_player", playerLine(set, player(), "TEAM1", "TOP", 1));

        assertThat(mysqlErrorOf(() -> insert("game_set_player", playerLine(set, player(), "TEAM1", "MID", 1))))
                .isEqualTo(DUPLICATE_ENTRY);
    }

    @Test
    void nullScreenRowsDoNotCollideForManualEntry() {
        long set = gameSet(duel(), 1, "fp");
        insert("game_set_player", playerLine(set, player(), "TEAM1", "TOP", null));
        insert("game_set_player", playerLine(set, player(), "TEAM1", "MID", null));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 6, -1})
    void screenRowMustBeBetweenOneAndFive(int row) {
        long set = gameSet(duel(), 1, "fp");

        assertThat(mysqlErrorOf(() -> insert("game_set_player", playerLine(set, player(), "TEAM1", "TOP", row))))
                .isEqualTo(CHECK_VIOLATED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"team1", "BLUE", "TEAM3"})
    void setPlayerTeamMustBeTeamValue(String value) {
        long set = gameSet(duel(), 1, "fp");

        assertThat(mysqlErrorOf(() -> insert("game_set_player", playerLine(set, player(), value, "TOP", 1))))
                .isEqualTo(CHECK_VIOLATED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"top", "Mid", "BOT", "ADCARRY"})
    void setPlayerPositionMustBeExactUpperCaseEnumValue(String value) {
        long set = gameSet(duel(), 1, "fp");

        assertThat(mysqlErrorOf(() -> insert("game_set_player", playerLine(set, player(), "TEAM1", value, 1))))
                .isEqualTo(CHECK_VIOLATED);
    }

    @Test
    void setPlayerStatsCannotBeNegative() {
        long set = gameSet(duel(), 1, "fp");

        assertThat(mysqlErrorOf(() -> insert("game_set_player",
                with(playerLine(set, player(), "TEAM1", "TOP", 1), "cs", -1)))).isEqualTo(CHECK_VIOLATED);
    }

    // ---------- game_set_ban ----------

    @Test
    void banTurnMustBeUniquePerSet() {
        long set = gameSet(duel(), 1, "fp");
        insert("game_set_ban", ban(set, 1, "BLUE"));

        assertThat(mysqlErrorOf(() -> insert("game_set_ban", ban(set, 1, "BLUE")))).isEqualTo(DUPLICATE_ENTRY);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 11, -3})
    void banTurnMustBeBetweenOneAndTen(int turn) {
        long set = gameSet(duel(), 1, "fp");

        assertThat(mysqlErrorOf(() -> insert("game_set_ban", ban(set, turn, "BLUE")))).isEqualTo(CHECK_VIOLATED);
    }

    @Test
    void oddTurnsMustBeBlueAndEvenTurnsMustBeRed() {
        long set = gameSet(duel(), 1, "fp");
        insert("game_set_ban", ban(set, 1, "BLUE"));
        insert("game_set_ban", ban(set, 2, "RED"));

        assertThat(mysqlErrorOf(() -> insert("game_set_ban", ban(set, 3, "RED")))).isEqualTo(CHECK_VIOLATED);
        assertThat(mysqlErrorOf(() -> insert("game_set_ban", ban(set, 4, "BLUE")))).isEqualTo(CHECK_VIOLATED);
    }

    @Test
    void banSideMustBeUpperCaseValue() {
        long set = gameSet(duel(), 1, "fp");

        assertThat(mysqlErrorOf(() -> insert("game_set_ban", ban(set, 1, "blue")))).isEqualTo(CHECK_VIOLATED);
    }

    // ---------- FK: RESTRICT / CASCADE ----------

    @Test
    void playerInGameSetCannotBeDeleted() {
        long set = gameSet(duel(), 1, "fp");
        long p = player();
        insert("game_set_player", playerLine(set, p, "TEAM1", "TOP", 1));

        assertThat(mysqlErrorOf(() -> jdbc.update("delete from player where id = ?", p)))
                .isEqualTo(PARENT_ROW_REFERENCED);
    }

    @Test
    void playerWithAliasCannotBeDeleted() {
        long p = player();
        insert("player_alias", aliasRow(p, "x", "x"));

        assertThat(mysqlErrorOf(() -> jdbc.update("delete from player where id = ?", p)))
                .isEqualTo(PARENT_ROW_REFERENCED);
    }

    @Test
    void duelWithGameSetCannotBeDeleted() {
        long duel = duel();
        gameSet(duel, 1, "fp");

        assertThat(mysqlErrorOf(() -> jdbc.update("delete from duel where id = ?", duel)))
                .isEqualTo(PARENT_ROW_REFERENCED);
    }

    @Test
    void sessionWithDuelCannotBeDeleted() {
        long session = session();
        insert("duel", row("session_id", session, "seq", 1, "created_at", TS));

        assertThat(mysqlErrorOf(() -> jdbc.update("delete from inhouse_session where id = ?", session)))
                .isEqualTo(PARENT_ROW_REFERENCED);
    }

    @Test
    void deletingGameSetCascadesToItsPlayersAndBans() {
        long set = gameSet(duel(), 1, "fp");
        insert("game_set_player", playerLine(set, player(), "TEAM1", "TOP", 1));
        insert("game_set_ban", ban(set, 1, "BLUE"));

        jdbc.update("delete from game_set where id = ?", set);

        assertThat(jdbc.queryForObject("select count(*) from game_set_player where game_set_id = ?", Integer.class, set))
                .isZero();
        assertThat(jdbc.queryForObject("select count(*) from game_set_ban where game_set_id = ?", Integer.class, set))
                .isZero();
    }

    // ---------- extraction_job (V3) ----------

    private Map<String, Object> jobRow(long duelId, String status) {
        boolean succeeded = status.equals("SUCCEEDED");
        boolean failed = status.equals("FAILED");
        return row("duel_id", duelId, "status", status, "top_side", "BLUE", "capturer_position", "ADC",
                "image_hash", "a".repeat(64), "image_key", "aa/" + "a".repeat(64) + ".png",
                "result_json", succeeded ? "{}" : null, "error_code", failed ? "BUSY" : null, "attempt_count", 0,
                "game_set_id", null, "created_at", TS, "started_at", null, "finished_at", null, "updated_at", TS);
    }

    private long job(long duelId, String status) {
        return insert("extraction_job", jobRow(duelId, status));
    }

    @ParameterizedTest
    @ValueSource(strings = {"queued", "DONE", "EXPIRED"})
    void jobStatusMustBeOneOfFourValues(String value) {
        long duel = duel();

        assertThat(mysqlErrorOf(() -> insert("extraction_job", with(jobRow(duel, "QUEUED"), "status", value))))
                .isEqualTo(CHECK_VIOLATED);
    }

    @Test
    void jobAcceptsAllFourStatusesWithTheirMatchingColumns() {
        long duel = duel();
        job(duel, "QUEUED");
        job(duel, "RUNNING");
        job(duel, "SUCCEEDED");
        job(duel, "FAILED");
    }

    @Test
    void jobTopSideAndCapturerPositionMustBeEnumValues() {
        long duel = duel();

        assertThat(mysqlErrorOf(() -> insert("extraction_job", with(jobRow(duel, "QUEUED"), "top_side", "GRAY"))))
                .isEqualTo(CHECK_VIOLATED);
        assertThat(mysqlErrorOf(() -> insert("extraction_job", with(jobRow(duel, "QUEUED"), "capturer_position", "BOT"))))
                .isEqualTo(CHECK_VIOLATED);
    }

    @Test
    void jobErrorCodeExistsExactlyWhenFailed() {
        long duel = duel();

        assertThat(mysqlErrorOf(() -> insert("extraction_job", with(jobRow(duel, "FAILED"), "error_code", null))))
                .isEqualTo(CHECK_VIOLATED);
        assertThat(mysqlErrorOf(() -> insert("extraction_job", with(jobRow(duel, "QUEUED"), "error_code", "BUSY"))))
                .isEqualTo(CHECK_VIOLATED);
    }

    @Test
    void jobResultJsonExistsExactlyWhenSucceeded() {
        long duel = duel();

        assertThat(mysqlErrorOf(() -> insert("extraction_job", with(jobRow(duel, "SUCCEEDED"), "result_json", null))))
                .isEqualTo(CHECK_VIOLATED);
        assertThat(mysqlErrorOf(() -> insert("extraction_job", with(jobRow(duel, "RUNNING"), "result_json", "{}"))))
                .isEqualTo(CHECK_VIOLATED);
    }

    @Test
    void jobSavedSetRequiresSucceededStatusAndAttemptsAreBounded() {
        long duel = duel();
        long set = gameSet(duel, 1, "fp-job-saved");

        assertThat(mysqlErrorOf(() -> insert("extraction_job", with(jobRow(duel, "QUEUED"), "game_set_id", set))))
                .isEqualTo(CHECK_VIOLATED);
        insert("extraction_job", with(jobRow(duel, "SUCCEEDED"), "game_set_id", set));
        assertThat(mysqlErrorOf(() -> insert("extraction_job", with(jobRow(duel, "QUEUED"), "attempt_count", 11))))
                .isEqualTo(CHECK_VIOLATED);
        assertThat(mysqlErrorOf(() -> insert("extraction_job", with(jobRow(duel, "QUEUED"), "attempt_count", -1))))
                .isEqualTo(CHECK_VIOLATED);
    }

    @Test
    void jobGameSetIsOneToOne() {
        long duel = duel();
        long set = gameSet(duel, 1, "fp-job-unique");
        insert("extraction_job", with(jobRow(duel, "SUCCEEDED"), "game_set_id", set));

        assertThat(mysqlErrorOf(() -> insert("extraction_job", with(jobRow(duel, "SUCCEEDED"), "game_set_id", set))))
                .isEqualTo(DUPLICATE_ENTRY);
    }

    @Test
    void multipleJobsWithoutGameSetMayCoexist() {
        long duel = duel();
        job(duel, "SUCCEEDED");
        job(duel, "SUCCEEDED");
    }

    @Test
    void jobForeignKeysAreRestrict() {
        long duel = duel();
        long set = gameSet(duel, 1, "fp-job-fk");
        long jobWithSet = insert("extraction_job", with(jobRow(duel, "SUCCEEDED"), "game_set_id", set));
        long plainDuel = duel();
        job(plainDuel, "QUEUED");

        assertThat(mysqlErrorOf(() -> insert("extraction_job", jobRow(999_999L, "QUEUED")))).isEqualTo(CHILD_ROW_NO_PARENT);
        assertThat(mysqlErrorOf(() -> jdbc.update("delete from duel where id = ?", plainDuel)))
                .isEqualTo(PARENT_ROW_REFERENCED);
        assertThat(mysqlErrorOf(() -> jdbc.update("delete from game_set where id = ?", set)))
                .isEqualTo(PARENT_ROW_REFERENCED);
        assertThat(jobWithSet).isPositive();
    }

    // ---------- duel.fearless_mode (V3) ----------

    @ParameterizedTest
    @ValueSource(strings = {"reset", "KEEP", "Continue"})
    void duelFearlessModeMustBeResetOrContinue(String value) {
        long session = session();

        assertThat(mysqlErrorOf(() -> insert("duel",
                row("session_id", session, "seq", 1, "fearless_mode", value, "created_at", TS))))
                .isEqualTo(CHECK_VIOLATED);
    }

    @Test
    void duelFearlessModeAcceptsBothValues() {
        long session = session();
        insert("duel", row("session_id", session, "seq", 1, "fearless_mode", "RESET", "created_at", TS));
        insert("duel", row("session_id", session, "seq", 2, "fearless_mode", "CONTINUE", "created_at", TS));
    }

    // ---------- player_lane_tier / score_setting (V4) ----------

    private Map<String, Object> laneTierRow(long playerId, String position, int tier, String sub) {
        return row("player_id", playerId, "position", position, "tier", tier, "sub", sub, "updated_at", TS);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 7})
    void laneTierMustBeBetweenOneAndSix(int tier) {
        long p = player();

        assertThat(mysqlErrorOf(() -> insert("player_lane_tier", laneTierRow(p, "TOP", tier, "MID"))))
                .isEqualTo(CHECK_VIOLATED);
    }

    @Test
    void laneTierPositionAndSubMustBeExactUpperCaseValues() {
        long p = player();

        assertThat(mysqlErrorOf(() -> insert("player_lane_tier", laneTierRow(p, "top", 3, "MID"))))
                .isEqualTo(CHECK_VIOLATED);
        assertThat(mysqlErrorOf(() -> insert("player_lane_tier", laneTierRow(p, "BOT", 3, "MID"))))
                .isEqualTo(CHECK_VIOLATED);
        assertThat(mysqlErrorOf(() -> insert("player_lane_tier", laneTierRow(p, "TOP", 3, "mid"))))
                .isEqualTo(CHECK_VIOLATED);
    }

    @Test
    void laneTierLastChangeColumnsAreAllOrNone() {
        long p = player();

        Map<String, Object> onlyDirection = with(laneTierRow(p, "TOP", 3, "MID"), "last_change_direction", "UP");
        assertThat(mysqlErrorOf(() -> insert("player_lane_tier", onlyDirection))).isEqualTo(CHECK_VIOLATED);

        Map<String, Object> badDirection = with(with(with(laneTierRow(p, "TOP", 3, "MID"),
                "last_change_direction", "SIDE"), "last_change_from_tier", 4), "last_change_from_sub", "LOW");
        assertThat(mysqlErrorOf(() -> insert("player_lane_tier", badDirection))).isEqualTo(CHECK_VIOLATED);

        Map<String, Object> all = with(with(with(laneTierRow(p, "TOP", 3, "MID"),
                "last_change_direction", "UP"), "last_change_from_tier", 4), "last_change_from_sub", "LOW");
        insert("player_lane_tier", all);
    }

    @Test
    void laneTierIsUniquePerPlayerAndPositionAndNeedsExistingPlayer() {
        long p = player();
        insert("player_lane_tier", laneTierRow(p, "TOP", 3, "MID"));

        assertThat(mysqlErrorOf(() -> insert("player_lane_tier", laneTierRow(p, "TOP", 2, "HIGH"))))
                .isEqualTo(DUPLICATE_ENTRY);
        insert("player_lane_tier", laneTierRow(p, "MID", 2, "HIGH"));
        assertThat(mysqlErrorOf(() -> insert("player_lane_tier", laneTierRow(999_999L, "TOP", 3, "MID"))))
                .isEqualTo(CHILD_ROW_NO_PARENT);
    }

    @Test
    void deletingPlayerCascadesToLaneTiers() {
        long p = player();
        insert("player_lane_tier", laneTierRow(p, "TOP", 3, "MID"));

        jdbc.update("delete from player where id = ?", p);

        assertThat(jdbc.queryForObject("select count(*) from player_lane_tier where player_id = ?", Integer.class, p))
                .isZero();
    }

    @Test
    void scoreSettingAllowsOnlyRowOneAndValuesWithinRange() {
        String insertSql = "insert into score_setting values (?, 1,1,1,1,1,1, 0,0,0, 0,0,0,0,0, UTC_TIMESTAMP(6))";
        assertThat(mysqlErrorOf(() -> jdbc.update(insertSql, 2))).isEqualTo(CHECK_VIOLATED);
        assertThat(mysqlErrorOf(() -> jdbc.update("update score_setting set base_tier1 = 1000 where id = 1")))
                .isEqualTo(CHECK_VIOLATED);
        assertThat(mysqlErrorOf(() -> jdbc.update("update score_setting set lane_support = -1000 where id = 1")))
                .isEqualTo(CHECK_VIOLATED);
        assertThat(jdbc.update("update score_setting set base_tier1 = 999, lane_support = -999 where id = 1"))
                .isEqualTo(1);
    }
}
