package com.lolinhouse.record;

import com.lolinhouse.domain.Position;

/**
 * 역할: 선수 이름 프로젝션. 화면 표시 이름은 displayName()이며 별칭·요주 여부는 읽지 않는다.
 * domain.player.PlayerName(닉네임 정규화 값)과는 다른 타입이다.
 */
public record PlayerNameRow(long id, String gameNickname, String realName, Position mainPosition) {

    /** 등록 선수는 실명, 미등록 선수는 게임 닉네임(Player.displayName과 같은 규칙). */
    public String displayName() {
        return realName != null ? realName : gameNickname;
    }

    public boolean registered() {
        return realName != null;
    }
}
