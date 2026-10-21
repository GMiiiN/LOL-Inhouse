package com.lolinhouse.domain.tier;

/**
 * 역할: 티어 안의 우열(상·중·하). rankBonus는 변동 방향을 정할 때만 쓰는 순위 보정이다(설계 §1.1, §1.4).
 */
public enum TierSub {
    HIGH("상", 2),
    MID("중", 1),
    LOW("하", 0);

    private final String label;
    private final int rankBonus;

    TierSub(String label, int rankBonus) {
        this.label = label;
        this.rankBonus = rankBonus;
    }

    /** 화면에 보여줄 한글 이름(상·중·하). */
    public String label() {
        return label;
    }

    public int rankBonus() {
        return rankBonus;
    }
}
