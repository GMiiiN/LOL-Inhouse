package com.lolinhouse.stats;

import com.lolinhouse.domain.stats.Kda;
import com.lolinhouse.domain.stats.PlayTotals;
import com.lolinhouse.domain.stats.StatSummary;

/**
 * 역할: 공개 통계 응답의 공통 요약. 정수 합계와 서버가 판정한 값(승률, KDA 비율)을 함께 보내고 평균·% 표기는 화면이 만든다.
 * kda.ratio가 null이면 PERFECT(데스 0)이거나 sets = 0이다. 화면은 sets를 먼저 보고 구분한다(Kda.ZERO가 PERFECT로 판정되는 함정).
 */
public record SummaryView(int sets, int wins, int losses, double winRate, KdaView kda, long cs, long gold) {

    public record KdaView(long kills, long deaths, long assists, Double ratio) {

        static KdaView from(Kda kda, int sets) {
            Double ratio = sets == 0 || kda.isPerfect() ? null : kda.ratio();
            return new KdaView(kda.kills(), kda.deaths(), kda.assists(), ratio);
        }
    }

    public static SummaryView from(StatSummary summary, PlayTotals totals) {
        return new SummaryView(summary.sets(), summary.wins(), summary.losses(), summary.winRate(),
                KdaView.from(summary.kda(), summary.sets()), totals.cs(), totals.gold());
    }
}
