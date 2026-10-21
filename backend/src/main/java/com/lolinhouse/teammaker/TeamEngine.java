package com.lolinhouse.teammaker;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.lolinhouse.domain.Position;
import com.lolinhouse.domain.team.TeamMakeResult;
import com.lolinhouse.domain.team.TeamMakerPlayer;
import com.lolinhouse.domain.team.TogetherPair;
import com.lolinhouse.domain.tier.ScoreTable;

/**
 * 역할: 서비스가 도메인 계산(TeamMaker)을 부르는 얇은 경계. 서비스 테스트가 계산 결과를 주입하거나 계산을 일부러 지연시킬 수 있게 둔다.
 * together는 같은 팀, apart는 다른 팀이어야 하는 쌍.
 */
public interface TeamEngine {

    TeamMakeResult auto(List<TeamMakerPlayer> ordered, Set<TogetherPair> together, Set<TogetherPair> apart,
            ScoreTable table, Map<Long, Position> fixedLanes);

    TeamMakeResult manual(Map<Position, List<TeamMakerPlayer>> picks, Set<TogetherPair> together,
            Set<TogetherPair> apart, ScoreTable table);
}
