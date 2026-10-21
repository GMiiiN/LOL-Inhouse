package com.lolinhouse.record;

import java.time.LocalDate;
import java.util.Collection;

/**
 * 역할: 기록 화면·CSV가 공유하는 필터. 기간은 내전 시작일 기준 양끝 포함, 모든 칸이 선택이다(null = 조건 없음).
 * championScope: ANY = 세트의 픽 또는 밴, PLAYER_PICK = 선수 행 하나가 playerId와 champion을 동시에 만족(playerId가 없으면 픽 행만 본다).
 * 형식 검증(from > to 등 INVALID_QUERY)은 컨트롤러의 몫이다. 설명: docs/design/m5-design.md §10-1
 */
public record RecordFilter(LocalDate from, LocalDate to, Long playerId, String champion, ChampionScope championScope) {

    public enum ChampionScope {
        ANY,
        PLAYER_PICK
    }

    public RecordFilter {
        if (championScope == null) {
            championScope = ChampionScope.ANY;
        }
    }

    public static RecordFilter none() {
        return new RecordFilter(null, null, null, null, ChampionScope.ANY);
    }

    public boolean hasPeriod() {
        return from != null || to != null;
    }

    public boolean periodContains(LocalDate startDate) {
        return (from == null || !startDate.isBefore(from)) && (to == null || !startDate.isAfter(to));
    }

    /** 세트 하나(그 세트의 선수 행·밴 행 전부)가 선수·챔피언 조건에 맞는지. 기간은 보지 않는다. */
    public boolean matchesSet(Collection<PlayerRow> players, Collection<BanRow> bans) {
        boolean byPlayer = playerId != null;
        boolean byChampion = champion != null;
        if (!byPlayer && !byChampion) {
            return true;
        }
        if (byPlayer && byChampion && championScope == ChampionScope.PLAYER_PICK) {
            return players.stream().anyMatch(p -> p.playerId() == playerId && p.championKey().equals(champion));
        }
        boolean playerOk = !byPlayer || players.stream().anyMatch(p -> p.playerId() == playerId);
        if (!byChampion) {
            return playerOk;
        }
        boolean championOk = players.stream().anyMatch(p -> p.championKey().equals(champion));
        if (championScope == ChampionScope.ANY) {
            championOk = championOk || bans.stream().anyMatch(b -> b.championKey().equals(champion));
        }
        return playerOk && championOk;
    }

    /** 기간 + 선수·챔피언 조건을 모두 본다. */
    public boolean matches(LocalDate startDate, Collection<PlayerRow> players, Collection<BanRow> bans) {
        return periodContains(startDate) && matchesSet(players, bans);
    }
}
