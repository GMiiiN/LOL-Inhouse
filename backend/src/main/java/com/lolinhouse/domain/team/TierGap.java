package com.lolinhouse.domain.team;

/**
 * 역할: 한 라인 두 사람의 티어 단계 차이 입력. stronger는 그 라인 picks 배열의 인덱스(0 = 첫째, 1 = 둘째), null = 비슷함. tiers는 0~5.
 */
public record TierGap(Integer stronger, int tiers) {
}
