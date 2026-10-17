package com.lolinhouse.player;

import java.util.List;

import com.lolinhouse.domain.Position;

/** 역할: 선수 응답 DTO. 프런트 types.ts의 Player와 필드가 같다 */
public record PlayerResponse(
        Long id,
        String displayName,
        String realName,
        boolean registered,
        Position mainPosition,
        List<AliasResponse> aliases) {

    /** 별칭 한 줄. 프런트 types.ts의 PlayerAlias. */
    public record AliasResponse(Long id, String nickname) {

        static AliasResponse from(PlayerAlias alias) {
            return new AliasResponse(alias.id(), alias.nickname());
        }
    }

    static PlayerResponse from(Player player, List<PlayerAlias> aliases) {
        return new PlayerResponse(player.id(), player.displayName(), player.realName(), player.isRegistered(),
                player.mainPosition(), aliases.stream().map(AliasResponse::from).toList());
    }
}
