package com.lolinhouse.domain;

/**
 * 대결 안에서 이어지는 팀 구분(내전에서 실제로 쓰는 이름 "A팀/B팀").
 * 점수와 피어리스 제한은 이 팀 기준으로 이어지고, 진영(Side)은 세트마다 따로 바뀐다.
 * 결과창의 "1번 팀"(=화면 위쪽 팀)과는 다른 개념이다.
 */
public enum DuelTeam {
    TEAM1("A팀"),
    TEAM2("B팀");

    private final String label;

    DuelTeam(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public DuelTeam other() {
        return this == TEAM1 ? TEAM2 : TEAM1;
    }
}
