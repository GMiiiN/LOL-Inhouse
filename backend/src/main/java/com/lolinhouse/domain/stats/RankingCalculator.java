package com.lolinhouse.domain.stats;

import com.lolinhouse.domain.Position;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 역할: 세트 기록 전체에서 전체 랭킹 또는 포지션별 랭킹을 만든다.
 * 정렬: 선택한 키 → 승률 → 참여 세트 수 → KDA(모두 내림차순). 모두 같으면 공동 순위(1,1,3).
 * 최소 표본(minSets) 미달 선수는 목록 맨 뒤에 순위 없이 둔다.
 */
public final class RankingCalculator {

    /** 최소 표본 기본값(사용자 결정 2026-10-16: 단위는 세트, 5세트). 메서드마다 minSets로 바꿀 수 있다. */
    public static final int DEFAULT_MIN_SETS = 5;

    private RankingCalculator() {
    }

    public static List<RankingEntry> overall(List<PlayerSetRecord> records) {
        return overall(records, DEFAULT_MIN_SETS);
    }

    public static List<RankingEntry> overall(List<PlayerSetRecord> records, int minSets) {
        return overall(records, minSets, RankingKey.WIN_RATE);
    }

    public static List<RankingEntry> overall(List<PlayerSetRecord> records, int minSets, RankingKey key) {
        return rank(records, minSets, key);
    }

    public static List<RankingEntry> byPosition(List<PlayerSetRecord> records, Position position) {
        return byPosition(records, position, DEFAULT_MIN_SETS);
    }

    public static List<RankingEntry> byPosition(List<PlayerSetRecord> records, Position position, int minSets) {
        return byPosition(records, position, minSets, RankingKey.WIN_RATE);
    }

    /** 해당 포지션으로 실제 뛴 세트만 사용한다. */
    public static List<RankingEntry> byPosition(List<PlayerSetRecord> records, Position position, int minSets,
                                                RankingKey key) {
        List<PlayerSetRecord> inPosition = records.stream()
                .filter(record -> record.position() == position)
                .toList();
        return rank(inPosition, minSets, key);
    }

    private static List<RankingEntry> rank(List<PlayerSetRecord> records, int minSets, RankingKey key) {
        Map<String, List<PlayerSetRecord>> byPlayer = new LinkedHashMap<>();
        for (PlayerSetRecord record : records) {
            byPlayer.computeIfAbsent(record.playerId(), id -> new ArrayList<>()).add(record);
        }

        Comparator<RankingEntry> bestFirst = Comparator.comparing(RankingEntry::summary, summaryComparator(key)).reversed();
        List<RankingEntry> ranked = new ArrayList<>();
        List<RankingEntry> unranked = new ArrayList<>();
        byPlayer.forEach((playerId, playerRecords) -> {
            StatSummary summary = StatSummary.of(playerRecords);
            (summary.sets() >= minSets ? ranked : unranked)
                    .add(new RankingEntry(playerId, summary, RankingEntry.UNRANKED));
        });
        ranked.sort(bestFirst);
        unranked.sort(bestFirst);

        List<RankingEntry> result = new ArrayList<>();
        for (int i = 0; i < ranked.size(); i++) {
            RankingEntry current = ranked.get(i);
            boolean tiedWithPrevious = i > 0 && bestFirst.compare(ranked.get(i - 1), current) == 0;
            // 공동 순위: 앞 사람과 완전 동률이면 같은 순위, 아니면 자기 위치(1,1,3 방식)
            int rank = tiedWithPrevious ? result.get(i - 1).rank() : i + 1;
            result.add(new RankingEntry(current.playerId(), current.summary(), rank));
        }
        result.addAll(unranked);
        return result;
    }

    /** 오름차순 비교(클수록 좋음). 선택한 키를 먼저 보고, 이어서 기본 동률 규칙(승률 → 세트 수 → KDA). */
    private static Comparator<StatSummary> summaryComparator(RankingKey key) {
        Comparator<StatSummary> byWinRate = Comparator.comparingDouble(StatSummary::winRate);
        Comparator<StatSummary> bySets = Comparator.comparingInt(StatSummary::sets);
        Comparator<StatSummary> byKda = (a, b) -> compareKda(a.kda(), b.kda());

        Comparator<StatSummary> primary = switch (key) {
            case WIN_RATE -> byWinRate;
            case KDA -> byKda;
            case SETS -> bySets;
        };
        return primary.thenComparing(byWinRate).thenComparing(bySets).thenComparing(byKda);
    }

    /**
     * KDA 비교의 유일한 자리. 사용자 결정(2026-10-16): 정렬값 = (K+A) / max(D, 1).
     * 데스 0(PERFECT)은 데스 1로 보고 정렬만 하고, 화면에는 계속 "PERFECT"로 표시한다.
     */
    private static int compareKda(Kda a, Kda b) {
        return Double.compare(sortValue(a), sortValue(b));
    }

    private static double sortValue(Kda kda) {
        return (kda.kills() + kda.assists()) / (double) Math.max(kda.deaths(), 1);
    }
}
