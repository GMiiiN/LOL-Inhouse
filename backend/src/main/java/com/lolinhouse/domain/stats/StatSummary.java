package com.lolinhouse.domain.stats;

import java.util.Collection;

/**
 * 역할: 세트 기록 묶음의 요약(참여 세트 수, 승수, 누적 KDA). 전체·포지션별·챔피언별이 모두 이 모양이다.
 * 승률은 세트 기준(wins ÷ sets)이며 세트가 0이면 0.0이다.
 * 설명: docs/study/07-domain-player-stats.md
 */
public record StatSummary(int sets, int wins, Kda kda) {

    public static StatSummary of(Collection<PlayerSetRecord> records) {
        int wins = 0;
        Kda total = Kda.ZERO;
        for (PlayerSetRecord record : records) {
            if (record.win()) {
                wins++;
            }
            total = total.plus(new Kda(record.kills(), record.deaths(), record.assists()));
        }
        return new StatSummary(records.size(), wins, total);
    }

    public int losses() {
        return sets - wins;
    }

    public double winRate() {
        return sets == 0 ? 0.0 : wins / (double) sets;
    }
}
