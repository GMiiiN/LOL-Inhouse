package com.lolinhouse.edit;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.lolinhouse.domain.DuelTeam;
import com.lolinhouse.domain.Side;
import com.lolinhouse.domain.set.FearlessConflicts.Conflict;

/**
 * 역할: 삭제된 세트 목록의 한 줄. blockers는 복구를 막는 유효 세트 id(없으면 null),
 * fearlessConflicts는 복구하면 겹치는 챔피언(경고용, 복구를 막지 않는다). team1Kills/team2Kills는 결과창 표시 합계.
 */
public record DeletedSetRow(long setId, long duelId, int duelSeq, long sessionId, LocalDate sessionStartDate,
        int setNo, LocalDate shotDate, Integer durationSec, DuelTeam winnerTeam, Side team1Side, int team1Kills,
        int team2Kills, Instant deletedAt, Blockers blockers, List<Conflict> fearlessConflicts) {

    /** 복구를 막는 유효 세트: 같은 지문, 같은 대결 같은 번호. */
    public record Blockers(Long fingerprintSetId, Long setNoSetId) {
    }
}
