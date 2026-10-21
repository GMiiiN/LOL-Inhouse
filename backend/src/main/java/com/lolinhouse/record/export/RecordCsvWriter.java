package com.lolinhouse.record.export;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.lolinhouse.domain.DuelTeam;
import com.lolinhouse.domain.Side;
import com.lolinhouse.record.BanRow;
import com.lolinhouse.record.PlayerNameRow;
import com.lolinhouse.record.PlayerRow;
import com.lolinhouse.record.SetRow;

/**
 * 역할: 세트×선수 행을 CSV 바이트로 직렬화한다(DB 없음). UTF-8 BOM + CRLF + RFC 4180 따옴표.
 * 모든 문자열 셀은 같은 함수(escapeFormula)로 수식 주입을 막고, 숫자 열은 그대로 쓴다.
 * 설명: docs/design/m5-design.md §5, §10-5
 */
public final class RecordCsvWriter {

    static final List<String> HEADERS = List.of("내전 시작일", "대결", "대결 ID", "세트", "세트 ID", "결과창 날짜",
            "경기 시간(초)", "승리 팀", "선수 ID", "선수", "등록 여부", "팀", "진영", "포지션", "챔피언", "챔피언 키",
            "킬", "데스", "어시스트", "CS", "골드", "결과", "B1", "R1", "B2", "R2", "B3", "R3", "B4", "R4", "B5", "R5");

    private static final String BOM = "﻿";
    private static final String CRLF = "\r\n";
    private static final int BAN_COUNT = 10;

    private RecordCsvWriter() {
    }

    /**
     * @param championNames 챔피언 키 → 한글 이름. null을 돌려주면 키를 그대로 쓴다(카탈로그 없음 포함)
     * @param names         선수 ID → 이름. 없는 선수는 ID 문자열로 대신한다
     */
    public static byte[] write(List<SetRow> sets, List<PlayerRow> players, List<BanRow> bans,
            Map<Long, PlayerNameRow> names, Function<String, String> championNames) {
        Map<Long, List<PlayerRow>> playersBySet = players.stream().collect(Collectors.groupingBy(PlayerRow::setId));
        Map<Long, List<BanRow>> bansBySet = bans.stream().collect(Collectors.groupingBy(BanRow::setId));
        List<SetRow> ordered = sets.stream().sorted(Comparator.comparing(SetRow::startDate)
                .thenComparingInt(SetRow::duelSeq).thenComparingInt(SetRow::setNo)).toList();

        StringBuilder out = new StringBuilder(BOM);
        appendLine(out, HEADERS);
        for (SetRow set : ordered) {
            List<String> banCells = banCells(bansBySet.getOrDefault(set.setId(), List.of()), championNames);
            List<PlayerRow> rows = playersBySet.getOrDefault(set.setId(), List.of()).stream()
                    .sorted(Comparator.comparing(PlayerRow::team).thenComparing(PlayerRow::position)).toList();
            for (PlayerRow p : rows) {
                appendLine(out, row(set, p, names.get(p.playerId()), championNames, banCells));
            }
        }
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static List<String> row(SetRow set, PlayerRow p, PlayerNameRow name,
            Function<String, String> championNames, List<String> banCells) {
        Side side = p.team() == DuelTeam.TEAM1 ? set.team1Side() : set.team1Side().opposite();
        List<String> cells = new ArrayList<>(HEADERS.size());
        cells.add(text(set.startDate().toString()));
        cells.add(String.valueOf(set.duelSeq()));
        cells.add(String.valueOf(set.duelId()));
        cells.add(String.valueOf(set.setNo()));
        cells.add(String.valueOf(set.setId()));
        cells.add(text(set.shotDate() == null ? "" : set.shotDate().toString()));
        cells.add(String.valueOf(set.durationSec()));
        cells.add(text(teamLetter(set.winnerTeam())));
        cells.add(String.valueOf(p.playerId()));
        cells.add(text(name == null ? String.valueOf(p.playerId()) : name.displayName()));
        cells.add(text(name != null && name.registered() ? "등록" : "미등록"));
        cells.add(text(teamLetter(p.team())));
        cells.add(text(side.label()));
        cells.add(text(p.position().label()));
        cells.add(text(championName(p.championKey(), championNames)));
        cells.add(text(p.championKey()));
        cells.add(String.valueOf(p.kills()));
        cells.add(String.valueOf(p.deaths()));
        cells.add(String.valueOf(p.assists()));
        cells.add(String.valueOf(p.cs()));
        cells.add(String.valueOf(p.gold()));
        cells.add(text(p.team() == set.winnerTeam() ? "승" : "패"));
        cells.addAll(banCells);
        return cells;
    }

    /** turn 1~10 = B1 R1 ... B5 R5 순서로 10칸. 비어 있는 칸은 빈 문자열. */
    private static List<String> banCells(List<BanRow> bans, Function<String, String> championNames) {
        String[] cells = new String[BAN_COUNT];
        for (BanRow ban : bans) {
            if (ban.turn() >= 1 && ban.turn() <= BAN_COUNT) {
                cells[ban.turn() - 1] = text(championName(ban.championKey(), championNames));
            }
        }
        List<String> list = new ArrayList<>(BAN_COUNT);
        for (String cell : cells) {
            list.add(cell == null ? "" : cell);
        }
        return list;
    }

    private static String championName(String key, Function<String, String> championNames) {
        String name = championNames.apply(key);
        return name != null ? name : key;
    }

    private static String teamLetter(DuelTeam team) {
        return team == DuelTeam.TEAM1 ? "A" : "B";
    }

    private static String text(String value) {
        return escapeFormula(value);
    }

    /** 수식으로 읽힐 수 있는 시작 문자(= + - @, 전각 형태, 탭·CR·LF)면 앞에 작은따옴표를 붙인다. */
    static String escapeFormula(String value) {
        if (value.isEmpty()) {
            return value;
        }
        return switch (value.charAt(0)) {
            case '=', '+', '-', '@', '＝', '＋', '－', '＠', '\t', '\r', '\n' -> "'" + value;
            default -> value;
        };
    }

    private static void appendLine(StringBuilder out, List<String> cells) {
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                out.append(',');
            }
            out.append(quote(cells.get(i)));
        }
        out.append(CRLF);
    }

    private static String quote(String cell) {
        boolean needs = cell.indexOf(',') >= 0 || cell.indexOf('"') >= 0 || cell.indexOf('\n') >= 0
                || cell.indexOf('\r') >= 0;
        return needs ? '"' + cell.replace("\"", "\"\"") + '"' : cell;
    }
}
