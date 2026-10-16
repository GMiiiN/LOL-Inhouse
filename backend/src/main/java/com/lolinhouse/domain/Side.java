package com.lolinhouse.domain;

/**
 * 세트별 진영. 같은 대결 안에서도 세트마다 번갈아 바뀐다(1세트는 1팀이 블루).
 * 설명: docs/study/06-domain-set-rules.md
 */
public enum Side {
    BLUE("블루"),
    RED("레드");

    private final String label;

    Side(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** 반대 진영. 위쪽 팀 진영을 고르면 아래쪽 팀은 항상 반대다. */
    public Side opposite() {
        return this == BLUE ? RED : BLUE;
    }
}
