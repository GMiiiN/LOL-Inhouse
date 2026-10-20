package com.lolinhouse.registration;

import java.time.LocalDate;
import java.util.List;

import com.lolinhouse.domain.DuelTeam;
import com.lolinhouse.domain.Side;
import com.lolinhouse.session.FearlessMode;

/** 역할: GET registration-context 응답. 업로드 화면의 머리말(대결 N, N세트로 등록), 기본 진영, 피어리스 제한 챔피언. */
public record RegistrationContextResponse(Long duelId, Long sessionId, LocalDate sessionStartDate, int duelSeq,
        FearlessMode fearlessMode, int nextSetNo, Side defaultTopSide, List<String> restrictedChampions,
        List<RegisteredSetSummary> registeredSets) {

    /** 이미 등록된 유효 세트 한 건. */
    public record RegisteredSetSummary(Long id, int setNo, Side team1Side, DuelTeam winnerTeam, DuelTeam topTeam) {
    }
}
