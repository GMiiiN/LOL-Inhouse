package com.lolinhouse.record.export;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.lolinhouse.domain.DuelTeam;
import com.lolinhouse.domain.Position;
import com.lolinhouse.domain.Side;
import com.lolinhouse.record.BanRow;
import com.lolinhouse.record.PlayerNameRow;
import com.lolinhouse.record.PlayerRow;
import com.lolinhouse.record.SetRow;
import com.lolinhouse.session.FearlessMode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 역할: CSV 직렬화(BOM, CRLF, 따옴표, 수식 방어, 열 순서)를 DB 없이 검증한다. */
class RecordCsvWriterTest {

    private static final Function<String, String> NO_CATALOG = key -> null;

    private static SetRow set(long id, int duelSeq, int setNo, LocalDate start) {
        return new SetRow(id, 100 + id, setNo, Side.BLUE, DuelTeam.TEAM1, LocalDate.of(2130, 1, 5), 1094,
                20, 15, 40, 60000, 15, 20, 30, 55000, duelSeq, FearlessMode.RESET, 1, start);
    }

    private static PlayerRow player(long setId, long playerId, DuelTeam team, Position position, String champion) {
        return new PlayerRow(setId, playerId, team, position, champion, 4, 2, 6, 200, 10000);
    }

    private static List<BanRow> tenBans(long setId) {
        List<BanRow> bans = new ArrayList<>();
        for (int turn = 1; turn <= 10; turn++) {
            bans.add(new BanRow(setId, turn, turn % 2 == 1 ? Side.BLUE : Side.RED, "B" + turn));
        }
        return bans;
    }

    private static String text(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static byte[] one(String nickname, boolean registered) {
        Map<Long, PlayerNameRow> names = Map.of(7L, new PlayerNameRow(7, nickname, registered ? nickname : null, null));
        return RecordCsvWriter.write(List.of(set(1, 1, 1, LocalDate.of(2130, 1, 5))),
                List.of(player(1, 7, DuelTeam.TEAM1, Position.TOP, "Ahri")), List.of(), names, NO_CATALOG);
    }

    @Test
    void 첫_3바이트는_BOM이고_줄_끝은_CRLF다() {
        byte[] bytes = one("선수", true);

        assertThat(bytes[0] & 0xFF).isEqualTo(0xEF);
        assertThat(bytes[1] & 0xFF).isEqualTo(0xBB);
        assertThat(bytes[2] & 0xFF).isEqualTo(0xBF);
        String body = text(bytes).substring(1);
        assertThat(body).endsWith("\r\n");
        assertThat(body.split("\r\n", -1)).hasSize(3);
        assertThat(body.replace("\r\n", "")).doesNotContain("\n");
    }

    @Test
    void 머리글_순서는_명세와_같다() {
        String header = text(one("선수", true)).substring(1).split("\r\n")[0];

        assertThat(header).isEqualTo("내전 시작일,대결,대결 ID,세트,세트 ID,결과창 날짜,경기 시간(초),승리 팀,선수 ID,선수,"
                + "등록 여부,팀,진영,포지션,챔피언,챔피언 키,킬,데스,어시스트,CS,골드,결과,B1,R1,B2,R2,B3,R3,B4,R4,B5,R5");
    }

    @Test
    void 한_행의_값과_밴_열_순서() {
        Map<Long, PlayerNameRow> names = Map.of(7L, new PlayerNameRow(7, "nick", "홍길동", null));
        byte[] bytes = RecordCsvWriter.write(List.of(set(1, 2, 3, LocalDate.of(2130, 1, 5))),
                List.of(player(1, 7, DuelTeam.TEAM2, Position.SUPPORT, "Ahri")), tenBans(1), names,
                key -> key.equals("Ahri") ? "아리" : key.equals("B2") ? "두번째" : null);

        String row = text(bytes).substring(1).split("\r\n")[1];

        assertThat(row).isEqualTo("2130-01-05,2,101,3,1,2130-01-05,1094,A,7,홍길동,등록,B,레드,서폿,아리,Ahri,4,2,6,200,10000,패,"
                + "B1,두번째,B3,B4,B5,B6,B7,B8,B9,B10");
    }

    @Test
    void 카탈로그가_없으면_챔피언_이름_열과_밴_열은_키를_쓰고_미등록은_닉네임이다() {
        String row = text(RecordCsvWriter.write(List.of(set(1, 1, 1, LocalDate.of(2130, 1, 5))),
                List.of(player(1, 7, DuelTeam.TEAM1, Position.TOP, "MonkeyKing")), tenBans(1),
                Map.of(7L, new PlayerNameRow(7, "닉", null, null)), NO_CATALOG)).substring(1).split("\r\n")[1];

        assertThat(row).contains(",닉,미등록,A,블루,탑,MonkeyKing,MonkeyKing,").endsWith(",승,B1,B2,B3,B4,B5,B6,B7,B8,B9,B10");
    }

    @Test
    void 밴이_없으면_밴_열은_비어_있다() {
        String row = text(one("선수", true)).substring(1).split("\r\n")[1];

        assertThat(row).endsWith(",승,,,,,,,,,,");
    }

    @Test
    void 행_순서는_시작일_대결_세트_그_안에서_A팀_B팀_탑부터() {
        SetRow later = set(2, 1, 1, LocalDate.of(2130, 2, 1));
        SetRow secondSet = set(3, 1, 2, LocalDate.of(2130, 1, 5));
        SetRow firstSet = set(1, 1, 1, LocalDate.of(2130, 1, 5));
        List<PlayerRow> players = List.of(
                player(1, 21, DuelTeam.TEAM2, Position.TOP, "A"), player(1, 12, DuelTeam.TEAM1, Position.MID, "A"),
                player(1, 11, DuelTeam.TEAM1, Position.TOP, "A"), player(2, 30, DuelTeam.TEAM1, Position.TOP, "A"),
                player(3, 40, DuelTeam.TEAM1, Position.TOP, "A"));

        String[] lines = text(RecordCsvWriter.write(List.of(later, secondSet, firstSet), players, List.of(), Map.of(),
                NO_CATALOG)).substring(1).split("\r\n");

        List<String> playerIds = new ArrayList<>();
        for (int i = 1; i < lines.length; i++) {
            playerIds.add(lines[i].split(",")[8]);
        }
        assertThat(playerIds).containsExactly("11", "12", "21", "40", "30");
    }

    @Test
    void 쉼표_따옴표_줄바꿈이_함께_든_값은_하나의_셀로_직렬화된다() {
        String body = text(one("a,\"b\"\nc", true)).substring(1);

        assertThat(body).contains(",\"a,\"\"b\"\"\nc\",");
    }

    @Test
    void 수식으로_읽힐_닉네임은_작은따옴표를_앞에_붙인다() {
        assertThat(text(one("=SUM(A1)", true))).contains(",'=SUM(A1),");
        assertThat(text(one("+1", true))).contains(",'+1,");
        assertThat(text(one("-1", true))).contains(",'-1,");
        assertThat(text(one("@cmd", true))).contains(",'@cmd,");
        assertThat(text(one("＝SUM(A1)", true))).contains(",'＝SUM(A1),");
        assertThat(text(one("＋1", true))).contains(",'＋1,");
        assertThat(text(one("－1", true))).contains(",'－1,");
        assertThat(text(one("＠x", true))).contains(",'＠x,");
        assertThat(text(one("\tx", true))).contains(",'\tx,");
        assertThat(text(one("\nx", true))).contains(",\"'\nx\",");
        assertThat(text(one("\rx", true))).contains(",\"'\rx\",");
        assertThat(text(one("평범", true))).doesNotContain("'");
    }

    @Test
    void 챔피언_이름과_밴_셀도_같은_수식_방어를_받고_숫자_열은_그대로다() {
        Map<Long, PlayerNameRow> names = Map.of(7L, new PlayerNameRow(7, "n", "이름", null));
        PlayerRow row = new PlayerRow(1, 7, DuelTeam.TEAM1, Position.TOP, "K", -3, 2, 6, 200, 10000);

        String line = text(RecordCsvWriter.write(List.of(set(1, 1, 1, LocalDate.of(2130, 1, 5))), List.of(row),
                List.of(new BanRow(1, 1, Side.BLUE, "K")), names, key -> "=HYPERLINK()")).substring(1).split("\r\n")[1];

        assertThat(line).contains(",'=HYPERLINK(),K,-3,2,6,200,10000,").endsWith(",'=HYPERLINK(),,,,,,,,,");
    }
}
