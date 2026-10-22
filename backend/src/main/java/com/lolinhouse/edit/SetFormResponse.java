package com.lolinhouse.edit;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.lolinhouse.domain.set.FearlessConflicts.Conflict;

/**
 * 역할: 수정 화면의 첫 응답. form은 저장된 세트를 등록 명령 모양으로 펼친 것(그대로 check/PUT에 보낼 수 있다),
 * restrictedChampions는 이 세트 시점에 앞쪽에서 이미 제한된 챔피언, fearlessConflicts는 지금 뒤쪽 세트와 겹친 픽이다.
 */
public record SetFormResponse(long setId, long duelId, int duelSeq, long sessionId, LocalDate sessionStartDate,
        int setNo, boolean deleted, Instant updatedAt, boolean hasImage, SetEditRequest form,
        EditEvaluation evaluation, List<String> restrictedChampions, List<Conflict> fearlessConflicts) {
}
