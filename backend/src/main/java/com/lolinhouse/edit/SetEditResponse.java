package com.lolinhouse.edit;

import java.util.List;

import com.lolinhouse.domain.DuelTeam;
import com.lolinhouse.domain.set.FearlessConflicts.Conflict;
import com.lolinhouse.registration.SetRegistrationResponse.PlayerRef;

/** 역할: 세트 수정 성공(200) 응답. createdPlayers·addedAliases는 등록 응답과 같은 모양이다. */
public record SetEditResponse(long setId, long duelId, int setNo, DuelTeam topTeam, List<String> warnings,
        List<Conflict> fearlessConflicts, List<PlayerRef> createdPlayers, List<PlayerRef> addedAliases) {
}
