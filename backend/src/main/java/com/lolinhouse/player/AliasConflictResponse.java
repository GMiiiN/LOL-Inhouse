package com.lolinhouse.player;

/**
 * 역할: 409 ALIAS_CONFLICT 응답의 conflicts 한 건 = "요청한 이 닉네임은 이미 이 선수에게 연결돼 있다".
 * 프런트 types.ts의 AliasConflict와 같다
 */
public record AliasConflictResponse(String nickname, Long playerId, String playerDisplayName) {
}
