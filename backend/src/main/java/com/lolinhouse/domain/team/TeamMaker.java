package com.lolinhouse.domain.team;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.lolinhouse.domain.Position;
import com.lolinhouse.domain.tier.LaneTier;
import com.lolinhouse.domain.tier.ScoreTable;

/**
 * 역할: 팀 짜기 계산(아티팩트 makeTeams와 같은 결과). 무작위를 쓰지 않고 좌우 교체 전 상위 4개까지 돌려준다.
 * 입력 검증(인원수·중복·쌍 유효성)은 서비스가 끝낸 뒤 부른다. 규칙: 설계 §1.5~§1.8, 골든 픽스처 golden-v2.json.
 */
public final class TeamMaker {

    /** 결과로 돌려줄 최대 방안 수. */
    public static final int MAX_OPTIONS = 4;

    private static final Position[] LANES = Position.values();
    private static final int LANE_COUNT = LANES.length;
    private static final int TEAM_SIZE = 5;
    private static final int PLAYER_COUNT = 10;

    /** 5명을 5라인에 놓는 120가지 순열(사전식). perm[i] = 팀 안 i번째 사람이 맡는 라인 번호. */
    private static final int[][] PERMS = buildPerms();

    private TeamMaker() {
    }

    /**
     * 자동 모드. ordered는 10명이고 순서가 결과에 영향을 준다(0번은 항상 블루). 분할 126가지 x 라인 배정 120가지를 모두 본다.
     * together에서 참가자가 아닌 사람이 낀 쌍은 무시한다(서비스가 미리 거른다).
     */
    public static TeamMakeResult auto(List<TeamMakerPlayer> ordered, Set<TogetherPair> together, ScoreTable table) {
        return auto(ordered, together, table, Map.of());
    }

    /**
     * 라인 고정이 있는 자동 모드. 고정한 사람은 고정 라인에만 놓이고, 그 라인 티어가 없으면 6티어 하 점수와 tier null이다.
     * 한 라인에 3명 이상 고정했는지는 서비스가 미리 거른다(여기서는 받은 대로 계산해 결과 0개가 된다).
     */
    public static TeamMakeResult auto(List<TeamMakerPlayer> ordered, Set<TogetherPair> together, ScoreTable table,
            Map<Long, Position> fixedLanes) {
        return auto(ordered, together, Set.of(), table, fixedLanes);
    }

    /** apart 쌍은 서로 다른 팀이어야 유효하다. apart가 비면 위 메서드와 결과가 완전히 같다. */
    public static TeamMakeResult auto(List<TeamMakerPlayer> ordered, Set<TogetherPair> together,
            Set<TogetherPair> apart, ScoreTable table, Map<Long, Position> fixedLanes) {
        if (ordered.size() != PLAYER_COUNT) {
            throw new IllegalArgumentException("자동 모드는 정확히 10명이어야 합니다: " + ordered.size());
        }
        int[][] pairIndexes = pairIndexes(ordered, together);
        int[][] apartIndexes = pairIndexes(ordered, apart);
        Map<Integer, List<Assignment>> cache = new HashMap<>();
        List<Candidate> candidates = new ArrayList<>();
        for (int mask = 0; mask < (1 << PLAYER_COUNT); mask++) {
            if ((mask & 1) == 0 || Integer.bitCount(mask) != TEAM_SIZE) {
                continue;
            }
            if (!satisfied(mask, pairIndexes) || !separated(mask, apartIndexes)) {
                continue;
            }
            List<Assignment> blue = cache.computeIfAbsent(mask, m -> assignments(ordered, m, table, fixedLanes));
            List<Assignment> red = cache.computeIfAbsent(~mask & ((1 << PLAYER_COUNT) - 1),
                    m -> assignments(ordered, m, table, fixedLanes));
            if (blue.isEmpty() || red.isEmpty()) {
                continue;
            }
            candidates.add(evaluate(blue, red));
        }
        if (candidates.isEmpty()) {
            return TeamMakeResult.failure(TeamMakeResult.Failure.NO_VALID_SPLIT);
        }
        return TeamMakeResult.success(top(candidates));
    }

    /**
     * 수동 모드. picks는 라인마다 [첫째, 둘째] 2명이다. 라인별 블루 쪽 선택 16가지를 본다.
     * 그 라인 티어가 없는 사람은 대체 점수(6티어 하)로 계산한다(서비스가 미리 거른다).
     */
    public static TeamMakeResult manual(Map<Position, List<TeamMakerPlayer>> picks, Set<TogetherPair> together,
            ScoreTable table) {
        return manual(picks, together, Set.of(), table);
    }

    /** apart 쌍은 서로 다른 팀이어야 유효하다. apart가 비면 위 메서드와 결과가 완전히 같다. */
    public static TeamMakeResult manual(Map<Position, List<TeamMakerPlayer>> picks, Set<TogetherPair> together,
            Set<TogetherPair> apart, ScoreTable table) {
        Set<Long> participantIds = new HashSet<>();
        picks.values().forEach(pair -> pair.forEach(p -> participantIds.add(p.id())));
        Set<TogetherPair> relevant = participantsOnly(together, participantIds);
        Set<TogetherPair> relevantApart = participantsOnly(apart, participantIds);
        List<Candidate> candidates = new ArrayList<>();
        for (int c = 1; c < (1 << LANE_COUNT); c += 2) {
            Slot[] blue = new Slot[LANE_COUNT];
            Slot[] red = new Slot[LANE_COUNT];
            for (int lane = 0; lane < LANE_COUNT; lane++) {
                Position position = LANES[lane];
                List<TeamMakerPlayer> pair = picks.get(position);
                boolean firstIsBlue = ((c >> lane) & 1) == 1;
                Slot first = slot(pair.get(0), position, table);
                Slot second = slot(pair.get(1), position, table);
                blue[lane] = firstIsBlue ? first : second;
                red[lane] = firstIsBlue ? second : first;
            }
            if (!satisfied(blue, relevant) || !separated(blue, relevantApart)) {
                continue;
            }
            candidates.add(candidate(new Assignment(blue), new Assignment(red)));
        }
        if (candidates.isEmpty()) {
            return TeamMakeResult.failure(TeamMakeResult.Failure.PAIR_UNSATISFIABLE);
        }
        return TeamMakeResult.success(top(candidates));
    }

    // ---- 내부 ----

    /** 한 팀의 라인 배정 하나: 라인 순서 슬롯 5개와 합계. */
    private record Assignment(Slot[] slots, int sum) {

        Assignment(Slot[] slots) {
            this(slots, sumOf(slots));
        }

        private static int sumOf(Slot[] slots) {
            int total = 0;
            for (Slot slot : slots) {
                total += slot.score();
            }
            return total;
        }
    }

    private record Candidate(Assignment blue, Assignment red, int diff, int gap, int cost, int total) {

        TeamOption toOption() {
            return new TeamOption(List.of(blue.slots()), List.of(red.slots()), blue.sum(), red.sum(), diff, gap, cost,
                    total);
        }
    }

    private static Slot slot(TeamMakerPlayer player, Position position, ScoreTable table) {
        LaneTier tier = player.tierOf(position);
        return new Slot(player.id(), position, tier,
                tier == null ? table.fallbackPoints(position) : table.points(tier, position));
    }

    /** mask의 켜진 비트(오름차순)가 가리키는 5명을 5라인에 놓는 모든 경우. 고정한 사람은 고정 라인에만 놓고(티어 없으면 대체 점수),
     * 고정 안 한 사람은 그 라인 티어가 없으면 그 순열을 버린다. */
    private static List<Assignment> assignments(List<TeamMakerPlayer> ordered, int mask, ScoreTable table,
            Map<Long, Position> fixedLanes) {
        List<TeamMakerPlayer> team = new ArrayList<>(TEAM_SIZE);
        for (int i = 0; i < PLAYER_COUNT; i++) {
            if (((mask >> i) & 1) == 1) {
                team.add(ordered.get(i));
            }
        }
        List<Assignment> out = new ArrayList<>();
        for (int[] perm : PERMS) {
            Slot[] slots = new Slot[LANE_COUNT];
            boolean ok = true;
            for (int i = 0; i < TEAM_SIZE; i++) {
                Position position = LANES[perm[i]];
                TeamMakerPlayer player = team.get(i);
                Position fixed = fixedLanes.get(player.id());
                if (fixed != null && fixed != position) {
                    ok = false;
                    break;
                }
                LaneTier tier = player.tierOf(position);
                if (tier == null && fixed == null) {
                    ok = false;
                    break;
                }
                slots[perm[i]] = slot(player, position, table);
            }
            if (ok) {
                out.add(new Assignment(slots));
            }
        }
        return out;
    }

    /** 분할 하나의 최선: cost가 작은 쪽, 같으면 total이 큰 쪽, 완전히 같으면 먼저 찾은 쪽. */
    private static Candidate evaluate(List<Assignment> blues, List<Assignment> reds) {
        Candidate best = null;
        for (Assignment a : blues) {
            for (Assignment b : reds) {
                int diff = Math.abs(a.sum() - b.sum());
                int gap = 0;
                for (int lane = 0; lane < LANE_COUNT; lane++) {
                    gap = Math.max(gap, Math.abs(a.slots()[lane].score() - b.slots()[lane].score()));
                }
                int cost = cost(diff, gap);
                int total = a.sum() + b.sum();
                if (best == null || cost < best.cost() || (cost == best.cost() && total > best.total())) {
                    best = new Candidate(a, b, diff, gap, cost, total);
                }
            }
        }
        return best;
    }

    /** cost = diff x 3 + gap. */
    private static int cost(int diff, int gap) {
        return diff * 3 + gap;
    }

    private static Candidate candidate(Assignment blue, Assignment red) {
        int diff = Math.abs(blue.sum() - red.sum());
        int gap = 0;
        for (int lane = 0; lane < LANE_COUNT; lane++) {
            gap = Math.max(gap, Math.abs(blue.slots()[lane].score() - red.slots()[lane].score()));
        }
        return new Candidate(blue, red, diff, gap, cost(diff, gap), blue.sum() + red.sum());
    }

    /** cost 오름차순, total 내림차순의 안정 정렬 뒤 앞 4개. */
    private static List<TeamOption> top(List<Candidate> candidates) {
        candidates.sort(Comparator.comparingInt(Candidate::cost).thenComparing(Comparator.comparingInt(Candidate::total).reversed()));
        return candidates.stream().limit(MAX_OPTIONS).map(Candidate::toOption).toList();
    }

    private static Set<TogetherPair> participantsOnly(Set<TogetherPair> pairs, Set<Long> participantIds) {
        Set<TogetherPair> out = new HashSet<>();
        for (TogetherPair pair : pairs) {
            if (participantIds.contains(pair.first()) && participantIds.contains(pair.second())) {
                out.add(pair);
            }
        }
        return out;
    }

    /** 쌍을 (참가자 번호, 참가자 번호)로 바꾼다. 참가자가 아닌 사람이 낀 쌍은 뺀다. */
    private static int[][] pairIndexes(List<TeamMakerPlayer> ordered, Set<TogetherPair> together) {
        Map<Long, Integer> indexById = new HashMap<>();
        for (int i = 0; i < ordered.size(); i++) {
            indexById.put(ordered.get(i).id(), i);
        }
        return together.stream()
                .filter(p -> indexById.containsKey(p.first()) && indexById.containsKey(p.second()))
                .map(p -> new int[] {indexById.get(p.first()), indexById.get(p.second())})
                .toArray(int[][]::new);
    }

    private static boolean satisfied(int mask, int[][] pairIndexes) {
        for (int[] pair : pairIndexes) {
            if (((mask >> pair[0]) & 1) != ((mask >> pair[1]) & 1)) {
                return false;
            }
        }
        return true;
    }

    private static boolean separated(int mask, int[][] pairIndexes) {
        for (int[] pair : pairIndexes) {
            if (((mask >> pair[0]) & 1) == ((mask >> pair[1]) & 1)) {
                return false;
            }
        }
        return true;
    }

    /** 수동용: apart 쌍이 블루·레드로 갈렸는지 본다. */
    private static boolean separated(Slot[] blue, Set<TogetherPair> apart) {
        for (TogetherPair pair : apart) {
            boolean firstBlue = false;
            boolean secondBlue = false;
            for (Slot slot : blue) {
                firstBlue |= slot.playerId() == pair.first();
                secondBlue |= slot.playerId() == pair.second();
            }
            if (firstBlue == secondBlue) {
                return false;
            }
        }
        return true;
    }

    /** 수동용: 블루에 앉은 사람 기준으로 쌍이 같은 쪽인지 본다(참가자끼리의 쌍만 넘어온다). */
    private static boolean satisfied(Slot[] blue, Set<TogetherPair> together) {
        for (TogetherPair pair : together) {
            boolean firstBlue = false;
            boolean secondBlue = false;
            for (Slot slot : blue) {
                firstBlue |= slot.playerId() == pair.first();
                secondBlue |= slot.playerId() == pair.second();
            }
            if (firstBlue != secondBlue) {
                return false;
            }
        }
        return true;
    }

    private static int[][] buildPerms() {
        List<int[]> out = new ArrayList<>();
        permute(new int[LANE_COUNT], 0, new boolean[LANE_COUNT], out);
        return out.toArray(new int[0][]);
    }

    private static void permute(int[] current, int depth, boolean[] used, List<int[]> out) {
        if (depth == LANE_COUNT) {
            out.add(current.clone());
            return;
        }
        for (int x = 0; x < LANE_COUNT; x++) {
            if (!used[x]) {
                used[x] = true;
                current[depth] = x;
                permute(current, depth + 1, used, out);
                used[x] = false;
            }
        }
    }
}
