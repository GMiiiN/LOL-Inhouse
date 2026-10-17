package com.lolinhouse.domain.stats;

import com.lolinhouse.domain.Position;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 역할: 한 선수의 프로필 통계(전체, 포지션별, 챔피언별, 참여율, 대결 개인 결과)를 세트 기록 목록에서 계산한다.
 * 모든 메서드는 "전체 기록 + 대상 선수 ID"를 받아 내부에서 그 선수의 기록만 고른다.
 */
public final class PlayerStats {

    private PlayerStats() {
    }

    public static StatSummary overall(List<PlayerSetRecord> records, String playerId) {
        return StatSummary.of(recordsOf(records, playerId));
    }

    /** 실제로 뛴 포지션만 키로 가지며, 키 순서는 탑→서폿이다. */
    public static Map<Position, StatSummary> byPosition(List<PlayerSetRecord> records, String playerId) {
        Map<Position, List<PlayerSetRecord>> grouped = new EnumMap<>(Position.class);
        for (PlayerSetRecord record : recordsOf(records, playerId)) {
            grouped.computeIfAbsent(record.position(), key -> new ArrayList<>()).add(record);
        }
        Map<Position, StatSummary> result = new EnumMap<>(Position.class);
        grouped.forEach((position, group) -> result.put(position, StatSummary.of(group)));
        return result;
    }

    /** 사용 횟수 내림차순, 같으면 챔피언 키 오름차순. */
    public static List<ChampionStats> byChampion(List<PlayerSetRecord> records, String playerId) {
        Map<String, List<PlayerSetRecord>> grouped = recordsOf(records, playerId).stream()
                .collect(Collectors.groupingBy(PlayerSetRecord::champion));
        return grouped.entrySet().stream()
                .map(entry -> new ChampionStats(entry.getKey(), StatSummary.of(entry.getValue())))
                .sorted(Comparator.comparingInt((ChampionStats stats) -> stats.summary().sets()).reversed()
                        .thenComparing(ChampionStats::champion))
                .toList();
    }

    /** 참여율 = 선수의 세트 수 ÷ 같은 기간의 전체 유효 세트 수(totalSets는 호출하는 쪽이 센다). */
    public static double participationRate(List<PlayerSetRecord> records, String playerId, int totalSets) {
        int playerSets = recordsOf(records, playerId).size();
        if (playerSets > totalSets) {
            throw new IllegalArgumentException("선수 세트 수(" + playerSets + ")가 전체 세트 수(" + totalSets + ")보다 큽니다.");
        }
        return totalSets == 0 ? 0.0 : playerSets / (double) totalSets;
    }

    /** 한 대결에서 선수가 직접 뛴 세트의 승·패만 비교한다. 그 대결에 뛴 세트가 없으면 예외. */
    public static DuelPersonalResult duelResult(List<PlayerSetRecord> records, String playerId, String duelId) {
        List<PlayerSetRecord> inDuel = recordsOf(records, playerId).stream()
                .filter(record -> record.duelId().equals(duelId))
                .toList();
        if (inDuel.isEmpty()) {
            throw new IllegalArgumentException("선수 " + playerId + "는 대결 " + duelId + "에서 뛴 세트가 없습니다.");
        }
        int wins = (int) inDuel.stream().filter(PlayerSetRecord::win).count();
        return new DuelPersonalResult(wins, inDuel.size() - wins);
    }

    private static List<PlayerSetRecord> recordsOf(List<PlayerSetRecord> records, String playerId) {
        return records.stream().filter(record -> record.playerId().equals(playerId)).toList();
    }
}
