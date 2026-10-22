package com.lolinhouse.edit;

import java.util.List;

import com.lolinhouse.domain.set.FearlessConflicts.Conflict;

/** 역할: 복구 응답. 피어리스 충돌은 복구를 막지 않고 warnings(문장)와 fearlessConflicts(세트 링크용)로 알린다. */
public record RestoreResponse(long setId, List<String> warnings, List<Conflict> fearlessConflicts) {
}
