package com.lolinhouse.domain.stats;

/**
 * 역할: 한 선수의 챔피언 하나에 대한 요약
 */
public record ChampionStats(String champion, StatSummary summary) {
}
