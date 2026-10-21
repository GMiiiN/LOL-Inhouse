package com.lolinhouse.domain.tier;

import java.util.Objects;

/**
 * 역할: 라인 티어의 마지막 변경(▲▼ 표시용) = 방향 + 변경 전 값. 다음 변경 때까지 유지된다(설계 §10 Q2, §11).
 */
public record LastChange(Direction direction, LaneTier from) {

    /** 방향. 변경 전보다 rank가 크면 UP. */
    public enum Direction { UP, DOWN }

    public LastChange {
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(from, "from");
    }

    /**
     * 라인 티어를 저장할 때 마지막 변경이 어떻게 되는지 정한다.
     * previous가 null(새 라인) → null, 같은 값 저장 → existing 그대로, 값이 다르면 rank 비교로 방향을 정해 새 LastChange를 돌려준다.
     * 반환 null = 표시할 변경 없음.
     */
    public static LastChange afterSave(LaneTier previous, LaneTier next, LastChange existing) {
        if (previous == null) {
            return null;
        }
        if (previous.equals(next)) {
            return existing;
        }
        return new LastChange(next.rank() > previous.rank() ? Direction.UP : Direction.DOWN, previous);
    }
}
