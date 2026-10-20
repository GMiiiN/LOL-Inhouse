package com.lolinhouse.domain.set;

import com.lolinhouse.domain.Position;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 직전 세트와 이번 세트를 비교해 "팀이 바뀌었는지"를 분류하는 순수 함수(팀 변경 감지).
 * 선수는 id 문자열, 팀은 선수 id → 포지션 맵이다. 이번 세트는 결과창의 위쪽/아래쪽 팀 그대로 받고,
 * 직전 1팀/2팀과 짝을 짓는 방향은 겹치는 인원이 많은 쪽으로 정한다(어느 쪽이 1팀이든 비교된다).
 * 분류 기준: docs/design/m4-design.md §11.1 표.
 */
public final class TeamChangeDetector {

    private static final int TEAM_SIZE = 5;

    private TeamChangeDetector() {
    }

    public enum Kind {
        /** 팀 구성과 각자의 포지션이 직전과 같음 */
        NONE,
        /** 10명·포지션은 같고, 같은 포지션끼리 팀이 맞바뀐 쌍이 있음(피어리스를 유지하면 같은 대결) */
        SAME_LANE_SWAP,
        /** 팀 구성은 같은데 포지션이 바뀐 선수가 있음(피어리스 초기화 → 새 대결) */
        POSITION_CHANGE,
        /** 그 밖: 라인이 다른 교환, 새 사람 투입 등 */
        RESHUFFLE,
        /** 선수가 아직 확정되지 않아 판단을 보류 */
        PENDING
    }

    /**
     * @param kind    분류
     * @param swaps   SAME_LANE_SWAP일 때 맞바뀐 쌍의 수, 그 밖에는 0
     * @param details 화면에 보여줄 설명(선수 이름은 넣지 않는다)
     */
    public record TeamChange(Kind kind, int swaps, List<String> details) {

        public TeamChange {
            details = List.copyOf(details);
        }
    }

    /**
     * @param previousTeam1 직전 세트 1팀(선수 id → 포지션). null이면 비교할 직전 세트가 없다는 뜻이라 NONE
     * @param previousTeam2 직전 세트 2팀
     * @param currentTop    이번 세트 위쪽 팀. 선수가 5명이 아니거나 id·포지션이 비어 있으면(미확정) PENDING
     * @param currentBottom 이번 세트 아래쪽 팀
     */
    public static TeamChange detect(Map<String, Position> previousTeam1, Map<String, Position> previousTeam2,
                                    Map<String, Position> currentTop, Map<String, Position> currentBottom) {
        if (!isComplete(currentTop) || !isComplete(currentBottom)) {
            return new TeamChange(Kind.PENDING, 0, List.of("선수를 모두 고르면 팀 변경을 감지합니다."));
        }
        if (previousTeam1 == null || previousTeam2 == null) {
            return new TeamChange(Kind.NONE, 0, List.of());
        }

        int straight = overlap(currentTop, previousTeam1) + overlap(currentBottom, previousTeam2);
        int crossed = overlap(currentTop, previousTeam2) + overlap(currentBottom, previousTeam1);
        // 직전 1팀에 해당하는 이번 팀(sameAs1)과 직전 2팀에 해당하는 이번 팀(sameAs2)
        boolean topIsTeam1Side = straight >= crossed;
        Map<String, Position> sameAs1 = topIsTeam1Side ? currentTop : currentBottom;
        Map<String, Position> sameAs2 = topIsTeam1Side ? currentBottom : currentTop;

        if (sameAs1.keySet().equals(previousTeam1.keySet()) && sameAs2.keySet().equals(previousTeam2.keySet())) {
            return sameTeams(previousTeam1, previousTeam2, sameAs1, sameAs2);
        }
        return teamsChanged(previousTeam1, previousTeam2, sameAs1, sameAs2, Math.max(straight, crossed));
    }

    // 팀 구성이 같을 때: 포지션만 비교한다
    private static TeamChange sameTeams(Map<String, Position> previous1, Map<String, Position> previous2,
                                        Map<String, Position> current1, Map<String, Position> current2) {
        List<Position> changedPositions = new ArrayList<>();
        collectChangedPositions(previous1, current1, changedPositions);
        collectChangedPositions(previous2, current2, changedPositions);
        if (changedPositions.isEmpty()) {
            return new TeamChange(Kind.NONE, 0, List.of());
        }
        return new TeamChange(Kind.POSITION_CHANGE, 0,
                List.of("팀 구성은 같지만 포지션이 바뀐 선수가 " + changedPositions.size() + "명 있습니다."));
    }

    // 팀 구성이 바뀌었을 때: 10명이 같고 포지션도 모두 같으면 맞라인 교환, 아니면 재구성
    private static TeamChange teamsChanged(Map<String, Position> previous1, Map<String, Position> previous2,
                                           Map<String, Position> current1, Map<String, Position> current2,
                                           int unmoved) {
        Set<String> previousPlayers = new HashSet<>(previous1.keySet());
        previousPlayers.addAll(previous2.keySet());
        Set<String> currentPlayers = new HashSet<>(current1.keySet());
        currentPlayers.addAll(current2.keySet());

        if (previousPlayers.equals(currentPlayers)
                && keepsPreviousPositions(current1, previous1, previous2)
                && keepsPreviousPositions(current2, previous1, previous2)) {
            // 포지션이 모두 그대로이고 팀만 바뀌었으니, 직전 2팀에서 넘어온 선수 수 = 맞바뀐 쌍의 수
            int swaps = (int) current1.keySet().stream().filter(id -> !previous1.containsKey(id)).count();
            return new TeamChange(Kind.SAME_LANE_SWAP, swaps,
                    List.of("같은 포지션끼리 팀을 맞바꾼 쌍이 " + swaps + "건 있습니다."));
        }
        int moved = 2 * TEAM_SIZE - unmoved;
        return new TeamChange(Kind.RESHUFFLE, 0,
                List.of("팀 구성이 크게 바뀌었습니다(직전 팀에서 벗어난 선수 " + moved + "명)."));
    }

    // 현재 팀의 모든 선수가 직전에 맡던 포지션을 그대로 맡았는가(직전에 어느 팀이었는지는 묻지 않는다)
    private static boolean keepsPreviousPositions(Map<String, Position> current, Map<String, Position> previous1,
                                                  Map<String, Position> previous2) {
        return current.entrySet().stream().allMatch(entry -> {
            Position before = previous1.containsKey(entry.getKey())
                    ? previous1.get(entry.getKey()) : previous2.get(entry.getKey());
            return entry.getValue() == before;
        });
    }

    private static void collectChangedPositions(Map<String, Position> previous, Map<String, Position> current,
                                                List<Position> changed) {
        current.forEach((id, position) -> {
            if (previous.get(id) != position) {
                changed.add(position);
            }
        });
    }

    private static int overlap(Map<String, Position> current, Map<String, Position> previous) {
        return (int) current.keySet().stream().filter(previous::containsKey).count();
    }

    private static boolean isComplete(Map<String, Position> team) {
        return team != null && team.size() == TEAM_SIZE
                && team.entrySet().stream().allMatch(entry -> entry.getKey() != null && entry.getValue() != null);
    }
}
