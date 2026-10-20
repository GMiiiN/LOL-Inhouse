package com.lolinhouse.registration;

import java.util.List;

import com.lolinhouse.domain.DuelTeam;
import com.lolinhouse.domain.Side;
import com.lolinhouse.domain.set.TeamChangeDetector.TeamChange;

/**
 * 역할: 저장 명령 하나의 평가 결과(check 응답, 초안 응답 안의 evaluation). errors가 있으면 저장할 수 없고 warnings는 저장을 막지 않는다.
 * 설명: docs/design/m4-design.md §3 4~8단계, §11
 *
 * @param topTeam     위쪽 팀이 A팀/B팀 중 어디인지. 판별 보류면 null
 * @param team1Side   A팀의 진영. topTeam이 null이면 null
 * @param fingerprint 날짜·시간 형식이 틀리면 null
 * @param duplicateSet 같은 지문의 유효 세트(있으면 저장은 409)
 * @param teamChange  직전 세트 대비 팀 변경 감지 결과
 */
public record Evaluation(int setNo, DuelTeam topTeam, Detection detection, Side team1Side, String fingerprint,
        DuplicateSet duplicateSet, List<String> errors, List<String> warnings, TeamChange teamChange) {

    /** overlap은 OVERLAP일 때만 값이 있다. uncertain은 팀이 재구성돼(RESHUFFLE) 판별이 불확실하다는 표시. */
    public record Detection(DetectionMethod method, Integer overlap, boolean uncertain) {
    }
}
