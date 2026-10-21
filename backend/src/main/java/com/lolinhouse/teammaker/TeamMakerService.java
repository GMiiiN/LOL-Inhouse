package com.lolinhouse.teammaker;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.random.RandomGenerator;
import java.util.stream.Collectors;

import com.lolinhouse.common.web.ApiException;
import com.lolinhouse.common.web.TierTeamErrorCodes;
import com.lolinhouse.domain.Position;
import com.lolinhouse.domain.team.ScoreMatrix;
import com.lolinhouse.domain.team.Slot;
import com.lolinhouse.domain.team.TeamMakeResult;
import com.lolinhouse.domain.team.TeamMakerPlayer;
import com.lolinhouse.domain.team.TeamOption;
import com.lolinhouse.domain.team.TogetherPair;
import com.lolinhouse.domain.tier.LaneTier;
import com.lolinhouse.domain.tier.ScoreTable;
import com.lolinhouse.player.Player;
import com.lolinhouse.player.PlayerRepository;
import com.lolinhouse.teammaker.TeamMakerResponse.MatrixCell;
import com.lolinhouse.teammaker.TeamMakerResponse.Option;
import com.lolinhouse.teammaker.TeamMakerResponse.SlotView;
import com.lolinhouse.tier.PlayerLaneTier;
import com.lolinhouse.tier.PlayerLaneTierRepository;
import com.lolinhouse.tier.ScoreSetting;
import com.lolinhouse.tier.ScoreSettingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 역할: 팀 짜기 계산 한 건의 흐름. 남용 방지(주소별 횟수) → 입력 검증(DB 없이) → 계산 자리 확보 →
 * 한 읽기 트랜잭션에서 티어·점수표 스냅샷 → 도메인 계산(트랜잭션 밖) → 후보마다 무작위 좌우 교체 → 응답(scoreMatrix 포함).
 * 오류 코드: 설계 §4.3, §11 P2-7.
 */
@Service
public class TeamMakerService {

    static final int PARTICIPANTS = 10;
    static final int MAX_TOGETHER_PAIRS = 5;
    static final int MAX_FIXED_PER_LANE = 2;

    private final PlayerRepository playerRepository;
    private final PlayerLaneTierRepository laneTierRepository;
    private final ScoreSettingRepository scoreSettingRepository;
    private final TeamEngine engine;
    private final RandomGenerator random;
    private final TeamMakerGuard guard;
    private final TransactionTemplate readTransaction;

    public TeamMakerService(PlayerRepository playerRepository, PlayerLaneTierRepository laneTierRepository,
            ScoreSettingRepository scoreSettingRepository, TeamEngine engine, RandomGenerator random,
            TeamMakerGuard guard, PlatformTransactionManager transactionManager) {
        this.playerRepository = playerRepository;
        this.laneTierRepository = laneTierRepository;
        this.scoreSettingRepository = scoreSettingRepository;
        this.engine = engine;
        this.random = random;
        this.guard = guard;
        this.readTransaction = new TransactionTemplate(transactionManager);
        this.readTransaction.setReadOnly(true);
    }

    /** 조회 시점의 참가자 티어와 점수표. 옵션과 scoreMatrix가 같은 값에서 나온다. */
    private record Snapshot(Map<Long, TeamMakerPlayer> players, ScoreTable table) {
    }

    public TeamMakerResponse auto(TeamMakerAutoRequest request, String clientAddress) {
        guard.checkRate(clientAddress);
        List<Long> ids = request.participantIds() == null ? List.of() : request.participantIds();
        if (ids.size() != PARTICIPANTS) {
            throw ApiException.badRequest(TierTeamErrorCodes.TEAM_PARTICIPANT_COUNT,
                    "참가자를 정확히 10명 골라 주세요. 지금 " + ids.size() + "명이에요.").with("count", ids.size());
        }
        requireNoNulls(ids);
        requireDistinct(ids);
        Set<TogetherPair> together = parsePairs(request.together(), new HashSet<>(ids), "같은 팀");
        Set<TogetherPair> apart = parseApart(request.apart(), new HashSet<>(ids), together);

        Map<Long, Position> fixedLanes = parseFixedLanes(request.fixedLanes(), new HashSet<>(ids));

        boolean fixed = !fixedLanes.isEmpty();
        return compute(ids, snapshot -> {
            List<TeamMakerPlayer> ordered = ids.stream().map(snapshot.players()::get).toList();
            return engine.auto(ordered, together, apart, snapshot.table(), fixedLanes);
        }, fixed ? TierTeamErrorCodes.TEAM_FIXED_LANE_BLOCKED : TierTeamErrorCodes.TEAM_NO_VALID_SPLIT,
                fixed ? "라인 고정 때문에 가능한 조합이 없어요. 고정한 라인 때문에 다른 사람들이 갈 라인이 모자라요. 고정을 하나씩 풀어 보세요."
                        : "가능한 조합이 없어요. 고른 사람들의 라인이 겹쳐서 양 팀 모두 5개 라인을 채울 수 없거나, 같은 팀 조건 때문에 막혀요.");
    }

    public TeamMakerResponse manual(TeamMakerManualRequest request, String clientAddress) {
        guard.checkRate(clientAddress);
        Map<Position, List<Long>> picks = new EnumMap<>(Position.class);
        List<Long> all = new ArrayList<>();
        for (Position position : Position.values()) {
            List<Long> pair = request.picks() == null ? null : request.picks().get(position);
            if (pair == null || pair.size() != 2 || pair.contains(null)) {
                throw ApiException.badRequest(TierTeamErrorCodes.TEAM_LANE_PICK_INCOMPLETE,
                        position.label() + " 라인에 정확히 2명을 골라 주세요.").with("position", position.name());
            }
            picks.put(position, pair);
            all.addAll(pair);
        }
        requireDistinct(all);
        Set<TogetherPair> together = parsePairs(request.together(), new HashSet<>(all), "같은 팀");
        Set<TogetherPair> apart = parseApart(request.apart(), new HashSet<>(all), together);

        return compute(all, snapshot -> {
            Map<Position, List<TeamMakerPlayer>> resolved = new EnumMap<>(Position.class);
            for (Position position : Position.values()) {
                List<TeamMakerPlayer> pair = new ArrayList<>();
                for (Long id : picks.get(position)) {
                    TeamMakerPlayer player = snapshot.players().get(id);
                    if (player.tierOf(position) == null) {
                        throw ApiException.badRequest(TierTeamErrorCodes.TEAM_LANE_TIER_MISSING,
                                position.label() + " 라인 티어가 없는 사람이 있어요.")
                                .with("position", position.name()).with("playerId", id);
                    }
                    pair.add(player);
                }
                resolved.put(position, pair);
            }
            return engine.manual(resolved, together, apart, snapshot.table());
        }, TierTeamErrorCodes.TEAM_PAIR_UNSATISFIABLE,
                "같은 팀 조건을 만족하는 조합이 없어요. 같은 라인의 두 사람은 같은 팀이 될 수 없어요.");
    }

    private TeamMakerResponse compute(List<Long> participantIds, Function<Snapshot, TeamMakeResult> run,
            String failureCode, String failureDetail) {
        guard.acquire();
        try {
            Snapshot snapshot = readTransaction.execute(status -> loadSnapshot(participantIds));
            TeamMakeResult result = run.apply(snapshot);
            if (!result.isSuccess()) {
                throw ApiException.unprocessable(failureCode, failureDetail);
            }
            return toResponse(result.options(), snapshot);
        } finally {
            guard.release();
        }
    }

    private Snapshot loadSnapshot(List<Long> participantIds) {
        Map<Long, Player> found = playerRepository.findAllById(new LinkedHashSet<>(participantIds)).stream()
                .collect(Collectors.toMap(Player::id, Function.identity()));
        for (Long id : participantIds) {
            if (!found.containsKey(id)) {
                throw ApiException.badRequest(TierTeamErrorCodes.TEAM_PLAYER_NOT_FOUND,
                        "없는 선수가 있어요.").with("playerId", id);
            }
        }
        Map<Long, Map<Position, LaneTier>> lanesByPlayer = new LinkedHashMap<>();
        for (PlayerLaneTier row : laneTierRepository.findByPlayerIdIn(found.keySet())) {
            lanesByPlayer.computeIfAbsent(row.playerId(), key -> new EnumMap<>(Position.class))
                    .put(row.position(), row.laneTier());
        }
        Map<Long, TeamMakerPlayer> players = new LinkedHashMap<>();
        for (Long id : participantIds) {
            players.put(id, new TeamMakerPlayer(id, lanesByPlayer.getOrDefault(id, Map.of())));
        }
        ScoreTable table = scoreSettingRepository.findById(ScoreSetting.SINGLE_ID)
                .map(ScoreSetting::toTable).orElse(ScoreTable.DEFAULT);
        return new Snapshot(players, table);
    }

    private TeamMakerResponse toResponse(List<TeamOption> options, Snapshot snapshot) {
        List<Option> views = new ArrayList<>();
        for (TeamOption option : options) {
            TeamOption placed = random.nextBoolean() ? option.swapSides() : option;
            views.add(new Option(slotViews(placed.blue()), slotViews(placed.red()), placed.blueSum(),
                    placed.redSum(), placed.diff(), placed.gap(), placed.cost(), placed.total()));
        }
        Map<Long, Map<Position, MatrixCell>> matrix = new LinkedHashMap<>();
        ScoreMatrix.of(snapshot.players().values(), snapshot.table()).cells().forEach((id, row) -> {
            Map<Position, MatrixCell> cells = new EnumMap<>(Position.class);
            row.forEach((position, cell) -> cells.put(position, new MatrixCell(cell.score(),
                    cell.tier() == null ? null : cell.tier().tier(),
                    cell.tier() == null ? null : cell.tier().sub(), cell.hasTier())));
            matrix.put(id, cells);
        });
        return new TeamMakerResponse(views, matrix);
    }

    private static List<SlotView> slotViews(List<Slot> slots) {
        return slots.stream().map(slot -> new SlotView(slot.playerId(), slot.position(),
                slot.tier() == null ? null : slot.tier().tier(),
                slot.tier() == null ? null : slot.tier().sub(), slot.score())).toList();
    }

    private static void requireNoNulls(List<Long> ids) {
        for (Long id : ids) {
            if (id == null) {
                throw ApiException.badRequest(TierTeamErrorCodes.TEAM_PLAYER_NOT_FOUND, "없는 선수가 있어요.");
            }
        }
    }

    private static void requireDistinct(List<Long> ids) {
        Set<Long> seen = new HashSet<>();
        for (Long id : ids) {
            if (!seen.add(id)) {
                throw ApiException.badRequest(TierTeamErrorCodes.TEAM_DUPLICATE_PARTICIPANT,
                        "같은 사람을 두 번 골랐어요.").with("playerId", id);
            }
        }
    }

    /** apart 쌍 검증: together와 같은 규칙에 더해, 같은 쌍이 together에도 있으면 400 TEAM_PAIR_INVALID. */
    private static Set<TogetherPair> parseApart(List<List<Long>> raw, Set<Long> participants,
            Set<TogetherPair> together) {
        Set<TogetherPair> apart = parsePairs(raw, participants, "다른 팀");
        for (TogetherPair pair : apart) {
            if (together.contains(pair)) {
                throw pairInvalid("같은 두 사람을 같은 팀 조건과 다른 팀 조건에 함께 넣을 수 없어요.");
            }
        }
        return apart;
    }

    /** 쌍 검증: 최대 5쌍, 각 쌍은 서로 다른 참가자 2명. 순서만 다른 같은 쌍은 하나로 합친다. label은 오류 문구용. */
    private static Set<TogetherPair> parsePairs(List<List<Long>> raw, Set<Long> participants, String label) {
        if (raw == null || raw.isEmpty()) {
            return Set.of();
        }
        if (raw.size() > MAX_TOGETHER_PAIRS) {
            throw pairInvalid(label + " 조건은 최대 5쌍까지예요.");
        }
        Set<TogetherPair> pairs = new LinkedHashSet<>();
        for (List<Long> pair : raw) {
            if (pair == null || pair.size() != 2 || pair.contains(null)) {
                throw pairInvalid(label + " 조건은 두 사람씩 짝지어야 해요.");
            }
            long a = pair.get(0);
            long b = pair.get(1);
            if (a == b) {
                throw pairInvalid("자기 자신과는 " + label + " 조건으로 묶을 수 없어요.");
            }
            if (!participants.contains(a) || !participants.contains(b)) {
                throw pairInvalid(label + " 조건에 참가자가 아닌 사람이 있어요.");
            }
            pairs.add(new TogetherPair(a, b));
        }
        return pairs;
    }

    /** 라인 고정 검증: 값 null은 무시, 키는 참가자여야 하고, 한 라인에 3명 이상이면 라인 순서(탑부터)로 처음 넘친 라인을 알린다. */
    private static Map<Long, Position> parseFixedLanes(Map<Long, Position> raw, Set<Long> participants) {
        if (raw == null || raw.isEmpty()) {
            return Map.of();
        }
        Map<Long, Position> fixed = new LinkedHashMap<>();
        for (Map.Entry<Long, Position> entry : raw.entrySet()) {
            if (entry.getValue() == null) {
                continue;
            }
            if (entry.getKey() == null || !participants.contains(entry.getKey())) {
                throw ApiException.badRequest(TierTeamErrorCodes.TEAM_LANE_FIX_INVALID,
                        "라인 고정에 참가자가 아닌 사람이 있어요.");
            }
            fixed.put(entry.getKey(), entry.getValue());
        }
        for (Position position : Position.values()) {
            long count = fixed.values().stream().filter(position::equals).count();
            if (count > MAX_FIXED_PER_LANE) {
                throw ApiException.badRequest(TierTeamErrorCodes.TEAM_LANE_FIX_INVALID,
                        position.label() + "에 " + count + "명이 고정돼 있어요. 한 라인은 양 팀 합쳐 2명까지만 고정할 수 있어요.")
                        .with("position", position.name()).with("count", count);
            }
        }
        return fixed;
    }

    private static ApiException pairInvalid(String detail) {
        return ApiException.badRequest(TierTeamErrorCodes.TEAM_PAIR_INVALID, detail);
    }
}
