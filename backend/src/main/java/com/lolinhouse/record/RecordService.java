package com.lolinhouse.record;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.lolinhouse.common.web.ApiException;
import com.lolinhouse.common.web.PublicReadErrorCodes;
import com.lolinhouse.domain.DuelTeam;
import com.lolinhouse.domain.set.FearlessChain;
import com.lolinhouse.domain.set.FearlessChain.ChainDuel;
import com.lolinhouse.domain.stats.DuelPersonalResult;
import com.lolinhouse.record.DuelDetailResponse.Personal;
import com.lolinhouse.record.RecordPageResponse.DuelBlock;
import com.lolinhouse.record.RecordPageResponse.SessionBlock;
import com.lolinhouse.record.SetView.PlayerLine;
import com.lolinhouse.record.SetView.TeamView;
import com.lolinhouse.session.Duel;
import com.lolinhouse.session.DuelRepository;
import com.lolinhouse.session.FearlessMode;
import com.lolinhouse.session.GameSet;
import com.lolinhouse.session.GameSetRepository;
import com.lolinhouse.session.InhouseSession;
import com.lolinhouse.session.InhouseSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 역할: 공개 기록 조회(내전 기록 페이지, 대결 상세, 세트 상세). 읽기는 RecordReader·SetViewAssembler의 프로젝션 쿼리만 쓰고
 * 세트 수와 무관한 고정 쿼리 수로 끝난다. 대결 점수는 필터와 무관하게 그 대결의 유효 세트 전체로 센다(대결 종료 확정은 미정이라 "승리"로 단정하지 않는다).
 * 설명: docs/design/m5-design.md §1.2~1.4, §10
 */
@Service
public class RecordService {

    private final RecordReader reader;
    private final SetViewAssembler assembler;
    private final DuelRepository duels;
    private final InhouseSessionRepository sessions;
    private final GameSetRepository gameSets;

    public RecordService(RecordReader reader, SetViewAssembler assembler, DuelRepository duels,
            InhouseSessionRepository sessions, GameSetRepository gameSets) {
        this.reader = reader;
        this.assembler = assembler;
        this.duels = duels;
        this.sessions = sessions;
        this.gameSets = gameSets;
    }

    /**
     * 묶음 단위 커서 페이지. before가 있으면 시작일이 before보다 이른 묶음만, 조건에 맞는 세트가 있는 묶음 limit개를 돌려준다.
     * 더 있으면 nextBefore = 마지막 묶음의 시작일(시작일은 UNIQUE라 건너뛰는 묶음이 없다).
     */
    @Transactional(readOnly = true)
    public RecordPageResponse page(RecordFilter filter, LocalDate before, int limit) {
        LocalDate to = filter.to();
        if (before != null && (to == null || to.isAfter(before.minusDays(1)))) {
            to = before.minusDays(1);
        }
        List<SetRow> rows = reader.setsInPeriod(filter.from(), to);

        Set<Long> matching = matchingSetIds(filter, rows);
        Map<Long, List<SetRow>> rowsBySession = new LinkedHashMap<>();
        for (SetRow row : rows) {
            rowsBySession.computeIfAbsent(row.sessionId(), id -> new ArrayList<>()).add(row);
        }
        List<List<SetRow>> pageSessions = rowsBySession.values().stream()
                .filter(sessionRows -> sessionRows.stream().anyMatch(r -> matching.contains(r.setId())))
                .limit(limit + 1L).collect(Collectors.toCollection(ArrayList::new));
        LocalDate nextBefore = null;
        if (pageSessions.size() > limit) {
            pageSessions.remove(limit);
            nextBefore = pageSessions.get(limit - 1).get(0).startDate();
        }

        List<Long> pageSetIds = pageSessions.stream().flatMap(List::stream).map(SetRow::setId)
                .filter(matching::contains).toList();
        Map<Long, SetView> views = assembler.assemble(pageSetIds);

        List<SessionBlock> blocks = new ArrayList<>();
        for (List<SetRow> sessionRows : pageSessions) {
            blocks.add(new SessionBlock(sessionRows.get(0).sessionId(), sessionRows.get(0).startDate(),
                    duelBlocks(sessionRows, matching, views)));
        }
        return new RecordPageResponse(blocks, nextBefore);
    }

    @Transactional(readOnly = true)
    public SetView set(long setId) {
        SetView view = assembler.assemble(List.of(setId)).get(setId);
        if (view == null) {
            throw ApiException.notFound(PublicReadErrorCodes.SET_NOT_FOUND, "세트를 찾을 수 없습니다: " + setId);
        }
        return view;
    }

    @Transactional(readOnly = true)
    public DuelDetailResponse duel(long duelId) {
        Duel duel = duels.findById(duelId).orElseThrow(
                () -> ApiException.notFound("DUEL_NOT_FOUND", "대결을 찾을 수 없습니다: " + duelId));
        InhouseSession session = sessions.findById(duel.sessionId()).orElseThrow(
                () -> ApiException.notFound("DUEL_NOT_FOUND", "대결을 찾을 수 없습니다: " + duelId));
        List<Long> setIds = gameSets.findByDuelIdAndDeletedAtIsNullOrderBySetNoAsc(duelId).stream()
                .map(GameSet::id).toList();
        Map<Long, SetView> views = assembler.assemble(setIds);
        List<SetView> sets = views.values().stream().sorted(Comparator.comparingInt(SetView::setNo)).toList();

        List<ChainDuel> sessionDuels = reader.duelsOfSessions(List.of(duel.sessionId())).stream()
                .map(d -> new ChainDuel(d.duelId(), d.seq(), d.fearlessMode() == FearlessMode.CONTINUE)).toList();
        List<Long> chain = FearlessChain.chainDuelIds(sessionDuels, duelId);
        DuelDetailResponse.Fearless fearless = RecordViews.fearlessOfDuel(chain, duelId, reader.picksOfDuels(chain));

        return new DuelDetailResponse(duelId, duel.seq(), duel.fearlessMode(),
                RecordViews.score(sets.stream().map(SetView::winnerTeam).toList()), sets.size(), session.id(),
                session.startDate(), sets, personal(sets), fearless);
    }

    private Set<Long> matchingSetIds(RecordFilter filter, List<SetRow> rows) {
        Set<Long> all = rows.stream().map(SetRow::setId).collect(Collectors.toCollection(LinkedHashSet::new));
        if (filter.playerId() == null && filter.champion() == null) {
            return all;
        }
        Map<Long, List<PlayerRow>> players = reader.playersOfSets(all).stream()
                .collect(Collectors.groupingBy(PlayerRow::setId));
        Map<Long, List<BanRow>> bans = reader.bansOfSets(all).stream()
                .collect(Collectors.groupingBy(BanRow::setId));
        return all.stream().filter(id -> filter.matchesSet(players.getOrDefault(id, List.of()),
                bans.getOrDefault(id, List.of()))).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static List<DuelBlock> duelBlocks(List<SetRow> sessionRows, Set<Long> matching,
            Map<Long, SetView> views) {
        Map<Long, List<SetRow>> byDuel = sessionRows.stream().collect(Collectors.groupingBy(SetRow::duelId));
        List<DuelBlock> blocks = new ArrayList<>();
        for (List<SetRow> duelRows : byDuel.values()) {
            List<SetView> shown = duelRows.stream().filter(r -> matching.contains(r.setId()))
                    .sorted(Comparator.comparingInt(SetRow::setNo)).map(r -> views.get(r.setId())).toList();
            if (shown.isEmpty()) {
                continue;
            }
            SetRow first = duelRows.get(0);
            blocks.add(new DuelBlock(first.duelId(), first.duelSeq(), first.fearlessMode(),
                    RecordViews.score(duelRows.stream().map(SetRow::winnerTeam).toList()), duelRows.size(), shown));
        }
        blocks.sort(Comparator.comparingInt(DuelBlock::seq));
        return blocks;
    }

    /** 대결에 뛴 선수별 팀 목록·세트 승패. 맞라인 교환이면 한 선수가 두 팀으로 나온다. */
    private static List<Personal> personal(List<SetView> sets) {
        Map<Long, PlayerLine> lines = new LinkedHashMap<>();
        Map<Long, Set<DuelTeam>> teams = new LinkedHashMap<>();
        Map<Long, int[]> record = new LinkedHashMap<>();
        for (SetView set : sets) {
            for (TeamView team : set.teams()) {
                for (PlayerLine line : team.players()) {
                    lines.putIfAbsent(line.playerId(), line);
                    teams.computeIfAbsent(line.playerId(), id -> new LinkedHashSet<>()).add(team.team());
                    record.computeIfAbsent(line.playerId(), id -> new int[2])[team.won() ? 0 : 1]++;
                }
            }
        }
        List<Personal> result = new ArrayList<>();
        lines.forEach((playerId, line) -> {
            int[] wl = record.get(playerId);
            result.add(new Personal(playerId, line.displayName(), line.registered(),
                    List.copyOf(teams.get(playerId)), wl[0], wl[1], new DuelPersonalResult(wl[0], wl[1]).result()));
        });
        return result;
    }
}
