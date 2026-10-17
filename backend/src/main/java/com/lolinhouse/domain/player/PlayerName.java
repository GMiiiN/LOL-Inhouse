package com.lolinhouse.domain.player;

/**
 * 역할: 선수의 표시 이름 규칙. 미등록이면 게임 닉네임, 등록하면 실명을 보여준다.
 * 선수 ID는 그대로라 등록해도 전적이 갈라지지 않는다(저장은 M3).
 */
public record PlayerName(String gameNickname, String realName) {

    public static PlayerName unregistered(String gameNickname) {
        return new PlayerName(gameNickname, null);
    }

    public boolean isRegistered() {
        return realName != null;
    }

    public String displayName() {
        return isRegistered() ? realName : gameNickname;
    }

    /** 미등록 선수를 실명으로 등록한 새 값을 돌려준다(원본은 불변). */
    public PlayerName register(String realName) {
        if (realName == null || realName.isBlank()) {
            throw new IllegalArgumentException("실명이 비어 있습니다.");
        }
        return new PlayerName(gameNickname, realName.strip());
    }
}
