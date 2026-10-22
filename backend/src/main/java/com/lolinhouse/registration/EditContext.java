package com.lolinhouse.registration;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import com.lolinhouse.domain.set.FearlessChain.ChainDuel;
import com.lolinhouse.domain.set.FearlessConflicts.SetPick;

/**
 * 역할: 저장된 세트를 수정·검사할 때 평가기가 보는 값 묶음(DB를 읽어 만든다). 세트 번호는 저장된 번호 그대로다.
 *
 * @param oldTeam1Ids      수정 전 이 세트 A팀(TEAM1) 선수 id — 위쪽 팀이 A팀인지 판별하는 기준
 * @param previousTeam1Ids 같은 대결의 앞 유효 세트 A팀 선수 id(없으면 null). 앞뒤 A/B 구성 경고용
 * @param nextTeam1Ids     같은 대결의 뒤 유효 세트 A팀 선수 id(없으면 null)
 * @param sessionDuels     묶음의 모든 대결(피어리스 체인 계산용)
 * @param otherPicks       묶음의 다른 유효 세트들의 픽(이 세트 제외)
 */
public record EditContext(long setId, long duelId, int setNo, Set<String> oldTeam1Ids, Set<String> previousTeam1Ids,
        Set<String> nextTeam1Ids, List<ChainDuel> sessionDuels, Collection<SetPick> otherPicks) {
}
