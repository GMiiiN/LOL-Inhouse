package com.lolinhouse.session;

import java.time.Instant;
import java.time.LocalDate;

import com.lolinhouse.domain.DuelTeam;
import com.lolinhouse.domain.Side;

/**
 * 역할: 관리자 묶음 상세의 대결별 세트 한 줄. source는 image_key가 있으면 IMAGE(결과창 업로드), 없으면 IMPORT(가져오기).
 * 양 팀 킬은 결과창에 표시된 합계. deleted = 삭제 표시된 세트(복구 가능).
 */
public record AdminSetSummary(Long setId, int setNo, Side team1Side, DuelTeam winnerTeam, Integer durationSec,
        LocalDate shotDate, int team1Kills, int team2Kills, Source source, Instant createdAt, boolean deleted) {

    public enum Source { IMAGE, IMPORT }

    static AdminSetSummary from(GameSet set) {
        return new AdminSetSummary(set.id(), set.setNo(), set.team1Side(), set.winnerTeam(), set.durationSec(),
                set.shotDate(), set.team1Totals().kills(), set.team2Totals().kills(),
                set.imageKey() == null ? Source.IMPORT : Source.IMAGE, set.createdAt(), set.isDeleted());
    }
}
