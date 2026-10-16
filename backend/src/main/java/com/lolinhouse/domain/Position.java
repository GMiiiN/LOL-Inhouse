package com.lolinhouse.domain;

/**
 * 실제 포지션(라인). 선언 순서가 곧 "기본 라인 순서"(탑→정글→미드→원딜→서폿)이고,
 * 결과창 아래쪽 팀의 행 순서·확인 화면의 정렬 순서로 쓰인다.
 * 설명: docs/study/06-domain-set-rules.md
 */
public enum Position {
    TOP("탑"),
    JUNGLE("정글"),
    MID("미드"),
    ADC("원딜"),
    SUPPORT("서폿");

    private final String label;

    Position(String label) {
        this.label = label;
    }

    /** 화면에 보여줄 한글 이름. */
    public String label() {
        return label;
    }
}
