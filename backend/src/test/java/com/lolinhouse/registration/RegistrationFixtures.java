package com.lolinhouse.registration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;

import com.lolinhouse.champion.CatalogSnapshot;
import com.lolinhouse.champion.Champion;
import com.lolinhouse.champion.IconMatch;
import com.lolinhouse.domain.DuelTeam;
import com.lolinhouse.domain.Position;
import com.lolinhouse.domain.Side;
import com.lolinhouse.domain.player.KnownAlias;
import com.lolinhouse.domain.set.PositionAssignment;
import com.lolinhouse.ingest.extract.ExtractedScoreboard.TopResult;

/**
 * 역할: 평가기·초안 단위 테스트용 조립기. 기본 요청 = 1세트, 위쪽 팀 블루, 캡처한 사람 원딜, 위쪽 선수 id 1~5·아래쪽 6~10(모두 기존 선수),
 * 위쪽 픽 Champion00~04·아래쪽 픽 Champion05~09, 밴 Champion10~19, 팀 합계는 행 합과 같다. 챔피언 카탈로그는 Champion00~19.
 */
final class RegistrationFixtures {

    private RegistrationFixtures() {
    }

    static String champion(int index) {
        return String.format("Champion%02d", index);
    }

    static CatalogSnapshot catalog() {
        List<Champion> champions = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            champions.add(new Champion(champion(i), String.valueOf(i + 1), "챔피언" + i));
        }
        return new CatalogSnapshot("16.20.1", champions, (crop, ban) -> IconMatch.failedCrop());
    }

    /** 선수 id 1~20이 있고 별칭 소유자 맵은 주어진 닉네임 키만 가진다. */
    static PlayerDirectory players(Map<String, Long> aliasOwners) {
        Map<Long, String> names = new HashMap<>();
        List<KnownAlias> known = new ArrayList<>();
        for (long id = 1; id <= 20; id++) {
            names.put(id, "선수" + id);
        }
        aliasOwners.forEach((key, owner) -> known.add(new KnownAlias(String.valueOf(owner), key, false)));
        return new PlayerDirectory(names, aliasOwners, known);
    }

    static PlayerDirectory players() {
        return players(Map.of());
    }

    static DuelState firstSet() {
        return DuelState.firstSet(1L, Set.of());
    }

    /** 직전 세트(1세트)가 있는 2세트 상태: 팀1 = 1~5(위쪽 배치), 팀2 = 6~10(아래쪽 배치). */
    static DuelState secondSet(Set<String> restricted) {
        Map<String, Position> team1 = new LinkedHashMap<>();
        List<Position> topPositions = PositionAssignment.topTeamPositions(Position.ADC);
        for (int i = 0; i < 5; i++) {
            team1.put(String.valueOf(i + 1), topPositions.get(i));
        }
        Map<String, Position> team2 = new LinkedHashMap<>();
        List<Position> bottomPositions = PositionAssignment.bottomTeamPositions();
        for (int i = 0; i < 5; i++) {
            team2.put(String.valueOf(i + 6), bottomPositions.get(i));
        }
        return new DuelState(1L, 2, new DuelState.PreviousSet(100L, 1, DuelTeam.TEAM1, team1, team2), restricted);
    }

    static DuelState secondSet() {
        return secondSet(Set.of());
    }

    /** 요청 조립기. 필드를 바꾼 뒤 build(). */
    static final class Req {
        Long jobId = 1L;
        Side topSide = Side.BLUE;
        Position capturer = Position.ADC;
        TopResult topResult = TopResult.WIN;
        String shotDate = "2026-10-12";
        String duration = "25:42";
        NewDuelOption newDuel;
        final Team top = new Team(true);
        final Team bottom = new Team(false);

        Req topRow(int index, UnaryOperator<RowInput> change) {
            top.rows.set(index, change.apply(top.rows.get(index)));
            top.sync();
            return this;
        }

        Req bottomRow(int index, UnaryOperator<RowInput> change) {
            bottom.rows.set(index, change.apply(bottom.rows.get(index)));
            bottom.sync();
            return this;
        }

        SetRegistrationRequest build() {
            return new SetRegistrationRequest(jobId, topSide, capturer, topResult, shotDate, duration, top.build(),
                    bottom.build(), newDuel);
        }
    }

    static final class Team {
        int kills;
        int deaths;
        int assists;
        int gold;
        final List<RowInput> rows = new ArrayList<>();
        final List<String> bans = new ArrayList<>();

        Team(boolean top) {
            List<Position> positions = top ? PositionAssignment.topTeamPositions(Position.ADC)
                    : PositionAssignment.bottomTeamPositions();
            for (int i = 0; i < 5; i++) {
                long playerId = (top ? 1 : 6) + i;
                RowInput row = new RowInput(i + 1, positions.get(i), "닉" + playerId, RowResolution.EXISTING, playerId,
                        false, champion((top ? 0 : 5) + i), 2 + i, 1 + i, 3 + i, 100 + i, 10000 + 100 * i);
                rows.add(row);
                bans.add(champion((top ? 10 : 15) + i));
            }
            sync();
        }

        /** 팀 합계를 현재 행의 합으로 맞춘다. */
        Team sync() {
            kills = rows.stream().mapToInt(RowInput::kills).sum();
            deaths = rows.stream().mapToInt(RowInput::deaths).sum();
            assists = rows.stream().mapToInt(RowInput::assists).sum();
            gold = rows.stream().mapToInt(RowInput::gold).sum();
            return this;
        }

        TeamInput build() {
            return new TeamInput(kills, deaths, assists, gold, List.copyOf(rows), new ArrayList<>(bans));
        }
    }

    static RowInput existing(RowInput row, long playerId) {
        return new RowInput(row.screenRow(), row.position(), "닉" + playerId, RowResolution.EXISTING, playerId, false,
                row.championKey(), row.kills(), row.deaths(), row.assists(), row.cs(), row.gold());
    }

    static RowInput unresolved(RowInput row) {
        return new RowInput(row.screenRow(), row.position(), row.nickname(), RowResolution.UNRESOLVED, null, false,
                row.championKey(), row.kills(), row.deaths(), row.assists(), row.cs(), row.gold());
    }

    static RowInput newPlayer(RowInput row, String nickname) {
        return new RowInput(row.screenRow(), row.position(), nickname, RowResolution.NEW, null, false,
                row.championKey(), row.kills(), row.deaths(), row.assists(), row.cs(), row.gold());
    }

    static RowInput withChampion(RowInput row, String championKey) {
        return new RowInput(row.screenRow(), row.position(), row.nickname(), row.resolution(), row.playerId(),
                row.addAlias(), championKey, row.kills(), row.deaths(), row.assists(), row.cs(), row.gold());
    }

    static RowInput withAlias(RowInput row, String nickname) {
        return new RowInput(row.screenRow(), row.position(), nickname, row.resolution(), row.playerId(), true,
                row.championKey(), row.kills(), row.deaths(), row.assists(), row.cs(), row.gold());
    }

    static RowInput withPosition(RowInput row, Position position) {
        return new RowInput(row.screenRow(), position, row.nickname(), row.resolution(), row.playerId(),
                row.addAlias(), row.championKey(), row.kills(), row.deaths(), row.assists(), row.cs(), row.gold());
    }
}
