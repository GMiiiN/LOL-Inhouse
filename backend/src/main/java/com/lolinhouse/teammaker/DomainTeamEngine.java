package com.lolinhouse.teammaker;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.lolinhouse.domain.Position;
import com.lolinhouse.domain.team.TeamMakeResult;
import com.lolinhouse.domain.team.TeamMaker;
import com.lolinhouse.domain.team.TeamMakerPlayer;
import com.lolinhouse.domain.team.TogetherPair;
import com.lolinhouse.domain.tier.ScoreTable;
import org.springframework.stereotype.Component;

/** 역할: 운영용 TeamEngine. 도메인 TeamMaker에 그대로 위임한다. */
@Component
class DomainTeamEngine implements TeamEngine {

    @Override
    public TeamMakeResult auto(List<TeamMakerPlayer> ordered, Set<TogetherPair> together, Set<TogetherPair> apart,
            ScoreTable table, Map<Long, Position> fixedLanes) {
        return TeamMaker.auto(ordered, together, apart, table, fixedLanes);
    }

    @Override
    public TeamMakeResult manual(Map<Position, List<TeamMakerPlayer>> picks, Set<TogetherPair> together,
            Set<TogetherPair> apart, ScoreTable table) {
        return TeamMaker.manual(picks, together, apart, table);
    }
}
