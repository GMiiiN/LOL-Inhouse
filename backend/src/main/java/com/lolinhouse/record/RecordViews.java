package com.lolinhouse.record;

import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import com.lolinhouse.domain.DuelTeam;

/**
 * 역할: 화면 조립에 쓰는 순수 계산 모음(DB 없음, 단위 테스트 대상). 세트 시점 피어리스 제한, 대결 점수,
 * 대결 상세의 제한 나누기(이어온 것 / 이번 대결 픽). 필터는 RecordFilter가 맡는다.
 */
final class RecordViews {

    private RecordViews() {
    }

    /**
     * 한 세트 시점의 피어리스 제한: 체인에서 앞선 대결들의 모든 유효 세트 픽 + 같은 대결의 더 작은 세트 번호 픽(중복 제거, 키 오름차순).
     * 표시 필터로 줄이지 않은 체인 전체의 픽을 넣어야 한다.
     *
     * @param chainDuelIds 체인 대결 id, 오래된 것부터, 마지막이 이 세트의 대결 (FearlessChain.chainDuelIds 결과)
     * @param duelId       이 세트의 대결 id
     * @param setNo        이 세트의 번호
     * @param chainPicks   체인 대결들의 유효 세트 픽 전부
     */
    static List<String> fearlessRestrictedAt(List<Long> chainDuelIds, long duelId, int setNo,
            Collection<PickRow> chainPicks) {
        int own = chainDuelIds.indexOf(duelId);
        TreeSet<String> restricted = new TreeSet<>();
        for (PickRow pick : chainPicks) {
            int index = chainDuelIds.indexOf(pick.duelId());
            if (index < 0) {
                continue;
            }
            if (index < own || (index == own && pick.setNo() < setNo)) {
                restricted.add(pick.championKey());
            }
        }
        return List.copyOf(restricted);
    }

    /** 대결 점수: 유효 세트의 winnerTeam 개수. 두 팀 키가 항상 들어 있다. */
    static Map<DuelTeam, Integer> score(Collection<DuelTeam> winners) {
        Map<DuelTeam, Integer> score = new EnumMap<>(DuelTeam.class);
        for (DuelTeam team : DuelTeam.values()) {
            score.put(team, 0);
        }
        winners.forEach(winner -> score.merge(winner, 1, Integer::sum));
        return score;
    }

    /** 대결 상세용 "다음 세트 기준" 제한 나누기: 체인에서 앞선 대결의 픽(이어온 것)과 이 대결의 픽. 각각 중복 제거, 키 오름차순. */
    static DuelDetailResponse.Fearless fearlessOfDuel(List<Long> chainDuelIds, long duelId,
            Collection<PickRow> chainPicks) {
        int own = chainDuelIds.indexOf(duelId);
        TreeSet<String> inherited = new TreeSet<>();
        TreeSet<String> picked = new TreeSet<>();
        for (PickRow pick : chainPicks) {
            int index = chainDuelIds.indexOf(pick.duelId());
            if (index < 0) {
                continue;
            }
            if (index < own) {
                inherited.add(pick.championKey());
            } else if (index == own) {
                picked.add(pick.championKey());
            }
        }
        return new DuelDetailResponse.Fearless(List.copyOf(inherited), List.copyOf(picked));
    }
}
