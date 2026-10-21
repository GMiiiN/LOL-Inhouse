package com.lolinhouse.domain.team;

import java.util.List;

/**
 * 역할: 팀 짜기 방안 하나. blue/red는 라인 순서(탑~서폿) 슬롯 5개씩이고 블루 칸이 A팀이다.
 * cost = diff * 3 + gap, total = blueSum + redSum.
 */
public record TeamOption(List<Slot> blue, List<Slot> red, int blueSum, int redSum, int diff, int gap, int cost,
        int total) {

    public TeamOption {
        blue = List.copyOf(blue);
        red = List.copyOf(red);
    }

    /** 블루와 레드를 통째로 맞바꾼 방안(서비스가 무작위로 좌우를 바꿀 때 쓴다). diff·gap·cost·total은 그대로다. */
    public TeamOption swapSides() {
        return new TeamOption(red, blue, redSum, blueSum, diff, gap, cost, total);
    }
}
