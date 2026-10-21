package com.lolinhouse.record;

import java.time.LocalDate;

import com.lolinhouse.domain.DuelTeam;
import com.lolinhouse.domain.Side;
import com.lolinhouse.session.FearlessMode;

/**
 * 역할: 세트 한 줄 프로젝션(세트 + 소속 대결 + 소속 내전 묶음). 삭제된 세트는 로더가 읽지 않는다.
 * JPQL 생성자 표현식이 필드 순서·타입으로 호출하므로 RecordReader의 쿼리와 순서를 맞춘다.
 */
public record SetRow(
        long setId,
        long duelId,
        int setNo,
        Side team1Side,
        DuelTeam winnerTeam,
        LocalDate shotDate,
        Integer durationSec,
        int team1Kills,
        int team1Deaths,
        int team1Assists,
        int team1Gold,
        int team2Kills,
        int team2Deaths,
        int team2Assists,
        int team2Gold,
        int duelSeq,
        FearlessMode fearlessMode,
        long sessionId,
        LocalDate startDate) {
}
