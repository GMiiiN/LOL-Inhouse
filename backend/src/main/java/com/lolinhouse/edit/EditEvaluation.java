package com.lolinhouse.edit;

import java.util.List;

import com.lolinhouse.domain.DuelTeam;
import com.lolinhouse.domain.Side;
import com.lolinhouse.domain.set.FearlessConflicts.Conflict;
import com.lolinhouse.domain.set.TeamChangeDetector.TeamChange;
import com.lolinhouse.registration.DuplicateSet;
import com.lolinhouse.registration.Evaluation;
import com.lolinhouse.registration.Evaluation.Detection;

/** 역할: 수정 검사 결과 = Evaluation의 모든 필드 + 뒤쪽 세트와 겹친 픽 목록(항상 배열). teamChange는 항상 NONE이다. */
public record EditEvaluation(int setNo, DuelTeam topTeam, Detection detection, Side team1Side, String fingerprint,
        DuplicateSet duplicateSet, List<String> errors, List<String> warnings, TeamChange teamChange,
        List<Conflict> fearlessConflicts) {

    static EditEvaluation of(Evaluation e, List<Conflict> conflicts) {
        return new EditEvaluation(e.setNo(), e.topTeam(), e.detection(), e.team1Side(), e.fingerprint(),
                e.duplicateSet(), e.errors(), e.warnings(), e.teamChange(), List.copyOf(conflicts));
    }
}
