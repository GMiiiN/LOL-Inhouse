package com.lolinhouse.domain.stats;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * 역할: 챔피언 하나의 픽·밴 집계. 승패는 픽한 쪽 결과이고 정렬은 하지 않는다(프런트가 고른다).
 * setsWithPickOrBan = 픽이나 밴이 있었던 서로 다른 세트 수(같은 세트에서 픽과 밴이 겹쳐도 1). 밴픽률 = setsWithPickOrBan ÷ 전체 세트.
 * 결과는 챔피언 키 오름차순(결정적 순서). 설명: docs/design/m5-design.md §10-2
 */
public record ChampionPickBan(String champion, int picks, int bans, int wins, int losses, int setsWithPickOrBan) {

    /** 세트 id가 있는 밴 한 칸(밴 행). */
    public record BannedChampion(String setId, String champion) {
    }

    public static List<ChampionPickBan> compute(List<PlayerSetRecord> picks, List<BannedChampion> bans) {
        Map<String, Tally> tallies = new TreeMap<>();
        for (PlayerSetRecord pick : picks) {
            Tally tally = tallies.computeIfAbsent(pick.champion(), k -> new Tally());
            tally.picks++;
            if (pick.win()) {
                tally.wins++;
            } else {
                tally.losses++;
            }
            tally.setIds.add(pick.setId());
        }
        for (BannedChampion ban : bans) {
            Tally tally = tallies.computeIfAbsent(ban.champion(), k -> new Tally());
            tally.bans++;
            tally.setIds.add(ban.setId());
        }
        return tallies.entrySet().stream()
                .map(e -> new ChampionPickBan(e.getKey(), e.getValue().picks, e.getValue().bans,
                        e.getValue().wins, e.getValue().losses, e.getValue().setIds.size()))
                .toList();
    }

    private static final class Tally {
        int picks;
        int bans;
        int wins;
        int losses;
        final Set<String> setIds = new HashSet<>();
    }
}
