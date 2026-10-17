package com.lolinhouse.domain.stats;

/**
 * 역할: 누적 K/D/A 합계. 비율은 총(K+A) ÷ 총D로 계산하고, 데스가 0이면 PERFECT(숫자 아님)로 다룬다.
 * 판마다의 KDA를 평균 내지 않기 위해 합계만 들고 있다. 합계가 int를 넘어도 깨지지 않도록 long을 쓴다.
 */
public record Kda(long kills, long deaths, long assists) {

    public static final Kda ZERO = new Kda(0, 0, 0);

    public Kda plus(Kda other) {
        return new Kda(kills + other.kills, deaths + other.deaths, assists + other.assists);
    }

    public boolean isPerfect() {
        return deaths == 0;
    }

    /** 숫자 비율. PERFECT에는 정의되지 않으므로 먼저 isPerfect()로 확인해야 한다. */
    public double ratio() {
        if (isPerfect()) {
            throw new IllegalStateException("데스가 0인 KDA는 PERFECT라 숫자 비율이 없습니다.");
        }
        return (kills + assists) / (double) deaths;
    }
}
