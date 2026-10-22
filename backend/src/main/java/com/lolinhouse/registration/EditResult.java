package com.lolinhouse.registration;

import java.util.List;

import com.lolinhouse.domain.set.FearlessConflicts.Conflict;

/** 역할: 수정 평가 결과. fearlessConflicts는 뒤쪽 세트와 겹친 픽만이다(앞쪽 겹침은 evaluation.errors에 있다). */
public record EditResult(Evaluation evaluation, List<Conflict> fearlessConflicts) {
}
