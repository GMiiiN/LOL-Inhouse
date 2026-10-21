package com.lolinhouse.record;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.lolinhouse.domain.DuelTeam;
import com.lolinhouse.domain.set.FearlessChain;
import com.lolinhouse.domain.set.FearlessChain.ChainDuel;
import com.lolinhouse.record.SetView.BanView;
import com.lolinhouse.record.SetView.Displayed;
import com.lolinhouse.record.SetView.PlayerLine;
import com.lolinhouse.record.SetView.TeamView;
import com.lolinhouse.session.FearlessMode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 역할: 세트 id 목록을 화면용 SetView로 조립하는 공개 진입점. 기록 페이지·대결 상세·세트 상세·프로필 최근 경기·홈이 모두 이 서비스를 쓴다.
 * 세트·선수·밴·이름·대결·체인 픽을 IN으로 일괄 로딩하므로 쿼리 수는 세트 수와 무관하게 6개 이하다.
 * 피어리스 체인은 표시용으로 고른 세트가 아니라 같은 묶음의 모든 대결과 유효 세트로 계산한다(표시 필터로 줄이지 않는다).
 * 없거나 삭제된 세트 id는 결과 맵에 없다. 결과 순서는 최근 세트부터(시작일, 대결 seq, 세트 번호 내림차순).
 * 설명: docs/design/m5-design.md §10-3
 */
@Service
public class SetViewAssembler {

    private final RecordReader reader;

    public SetViewAssembler(RecordReader reader) {
        this.reader = reader;
    }

    @Transactional(readOnly = true)
    public Map<Long, SetView> assemble(Collection<Long> setIds) {
        List<SetRow> sets = reader.setsByIds(setIds);
        if (sets.isEmpty()) {
            return Map.of();
        }
        List<Long> foundIds = sets.stream().map(SetRow::setId).toList();
        Map<Long, List<PlayerRow>> playersBySet = reader.playersOfSets(foundIds).stream()
                .collect(Collectors.groupingBy(PlayerRow::setId));
        Map<Long, List<BanRow>> bansBySet = reader.bansOfSets(foundIds).stream()
                .collect(Collectors.groupingBy(BanRow::setId));
        Set<Long> playerIds = playersBySet.values().stream().flatMap(List::stream).map(PlayerRow::playerId)
                .collect(Collectors.toSet());
        Map<Long, PlayerNameRow> names = reader.namesOf(playerIds);

        Set<Long> sessionIds = sets.stream().map(SetRow::sessionId).collect(Collectors.toSet());
        Map<Long, List<ChainDuel>> chainDuelsBySession = reader.duelsOfSessions(sessionIds).stream()
                .collect(Collectors.groupingBy(DuelRow::sessionId, Collectors.mapping(
                        d -> new ChainDuel(d.duelId(), d.seq(), d.fearlessMode() == FearlessMode.CONTINUE),
                        Collectors.toList())));
        Map<Long, List<Long>> chainByDuel = new HashMap<>();
        Set<Long> chainDuelIds = new LinkedHashSet<>();
        for (SetRow set : sets) {
            chainByDuel.computeIfAbsent(set.duelId(), duelId -> {
                List<Long> chain = FearlessChain.chainDuelIds(chainDuelsBySession.get(set.sessionId()), duelId);
                chainDuelIds.addAll(chain);
                return chain;
            });
        }
        Map<Long, List<PickRow>> picksByDuel = reader.picksOfDuels(chainDuelIds).stream()
                .collect(Collectors.groupingBy(PickRow::duelId));

        Map<Long, SetView> views = new LinkedHashMap<>();
        for (SetRow set : sets) {
            List<Long> chain = chainByDuel.get(set.duelId());
            List<PickRow> chainPicks = chain.stream().flatMap(id -> picksByDuel.getOrDefault(id, List.of()).stream())
                    .toList();
            views.put(set.setId(), toView(set, playersBySet.getOrDefault(set.setId(), List.of()),
                    bansBySet.getOrDefault(set.setId(), List.of()), names,
                    RecordViews.fearlessRestrictedAt(chain, set.duelId(), set.setNo(), chainPicks)));
        }
        return views;
    }

    private static SetView toView(SetRow set, List<PlayerRow> players, List<BanRow> bans,
            Map<Long, PlayerNameRow> names, List<String> restricted) {
        List<TeamView> teams = new ArrayList<>();
        for (DuelTeam team : DuelTeam.values()) {
            List<PlayerLine> lines = players.stream().filter(p -> p.team() == team)
                    .sorted(Comparator.comparing(PlayerRow::position))
                    .map(p -> toLine(p, names.get(p.playerId()))).toList();
            Displayed displayed = team == DuelTeam.TEAM1
                    ? new Displayed(set.team1Kills(), set.team1Deaths(), set.team1Assists(), set.team1Gold())
                    : new Displayed(set.team2Kills(), set.team2Deaths(), set.team2Assists(), set.team2Gold());
            teams.add(new TeamView(team, team == DuelTeam.TEAM1 ? set.team1Side() : set.team1Side().opposite(),
                    set.winnerTeam() == team, displayed, lines));
        }
        List<BanView> banViews = bans.stream().sorted(Comparator.comparingInt(BanRow::turn))
                .map(b -> new BanView(b.turn(), b.side(), b.championKey())).toList();
        return new SetView(set.setId(), set.setNo(), set.duelId(), set.duelSeq(), set.sessionId(), set.startDate(),
                set.shotDate(), set.durationSec(), set.team1Side(), set.winnerTeam(), teams, banViews, restricted);
    }

    private static PlayerLine toLine(PlayerRow row, PlayerNameRow name) {
        String displayName = name != null ? name.displayName() : "#" + row.playerId();
        boolean registered = name != null && name.registered();
        return new PlayerLine(row.playerId(), displayName, registered, row.position(), row.championKey(),
                row.kills(), row.deaths(), row.assists(), row.cs(), row.gold());
    }
}
