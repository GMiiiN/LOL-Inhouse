package com.lolinhouse.domain.player;

/**
 * 역할: 이미 알고 있는 선수의 게임 닉네임 하나. 미등록 선수의 닉네임도 포함한다.
 * 설명: docs/study/07-domain-player-stats.md
 */
public record KnownAlias(String playerId, String nickname, boolean registered) {
}
