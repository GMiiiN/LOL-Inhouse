package com.lolinhouse.domain.stats;

import java.util.Collection;

/**
 * 역할: 선수 기록 묶음의 CS·골드 합계(평균은 화면이 세트 수로 나눠 만든다). StatSummary는 바꾸지 않고 따로 둔다.
 */
public record PlayTotals(long cs, long gold) {

    public static PlayTotals of(Collection<PlayerSetRecord> records) {
        long cs = 0;
        long gold = 0;
        for (PlayerSetRecord record : records) {
            cs += record.cs();
            gold += record.gold();
        }
        return new PlayTotals(cs, gold);
    }
}
