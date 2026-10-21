package com.lolinhouse.domain.team;

/**
 * 역할: "같은 팀으로 묶을" 두 사람. 순서가 달라도 같은 쌍이 되도록 작은 id를 first에 둔다.
 */
public record TogetherPair(long first, long second) {

    public TogetherPair {
        if (first == second) {
            throw new IllegalArgumentException("자기 자신과는 묶을 수 없습니다.");
        }
        if (first > second) {
            long swap = first;
            first = second;
            second = swap;
        }
    }
}
