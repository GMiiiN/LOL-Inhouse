package com.lolinhouse.domain.stats;

/**
 * 역할: 한 선수의 챔피언 하나에 대한 요약. 설명: docs/study/07-domain-player-stats.md
 */
public record ChampionStats(String champion, StatSummary summary) {
}
