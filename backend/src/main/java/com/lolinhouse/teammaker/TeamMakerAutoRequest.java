package com.lolinhouse.teammaker;

import java.util.List;
import java.util.Map;

import com.lolinhouse.domain.Position;

/**
 * 역할: POST /api/team-maker/auto 요청(공개). participantIds는 클릭한 순서 그대로 10개(정렬하면 동점 처리가 달라진다, 설계 R2).
 * together는 같은 팀으로 묶을 쌍 [a, b] 최대 5개.
 * apart는 꼭 다른 팀으로 나눌 쌍 [a, b] 최대 5개(생략 가능).
 * fixedLanes는 {선수 id: 고정 라인}(생략·null·빈 맵 = 고정 없음, 값 null은 그 사람만 고정 없음). 개수·중복·쌍 유효성은 서비스가 검사한다(TEAM_*).
 */
public record TeamMakerAutoRequest(List<Long> participantIds, List<List<Long>> together,
        List<List<Long>> apart, Map<Long, Position> fixedLanes) {
}
