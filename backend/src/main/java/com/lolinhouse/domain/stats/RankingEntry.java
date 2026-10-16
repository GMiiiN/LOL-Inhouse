package com.lolinhouse.domain.stats;

/**
 * 역할: 랭킹 한 줄. rank가 UNRANKED(0)면 최소 표본 미달이라 기록만 보여주고 순위는 없다.
 * 설명: docs/study/07-domain-player-stats.md
 */
public record RankingEntry(String playerId, StatSummary summary, int rank) {

    public static final int UNRANKED = 0;

    public boolean isRanked() {
        return rank != UNRANKED;
    }
}
