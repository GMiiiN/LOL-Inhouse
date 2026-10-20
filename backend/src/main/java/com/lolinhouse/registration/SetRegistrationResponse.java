package com.lolinhouse.registration;

import java.util.List;

import com.lolinhouse.domain.DuelTeam;

/** 역할: 세트 저장 성공(201) 응답. duelId는 "새 대결로 등록"이면 새로 만든 대결의 id. 설명: docs/design/m4-design.md §4.1 */
public record SetRegistrationResponse(Long setId, Long duelId, int setNo, DuelTeam topTeam, List<String> warnings,
        List<PlayerRef> createdPlayers, List<PlayerRef> addedAliases) {

    /** 선수 id와 그때 쓴 닉네임(정규화 표기). */
    public record PlayerRef(Long playerId, String nickname) {
    }
}
