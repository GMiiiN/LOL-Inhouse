package com.lolinhouse.domain.set;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.lolinhouse.domain.set.FearlessChain.ChainDuel;

/**
 * 역할: 저장된 세트의 피어리스 관계 계산(순수). 세트 B가 세트 A의 제한을 받으려면 B의 대결 체인(FearlessChain)에 A의 대결이 있고,
 * A가 더 앞(앞선 대결이거나 같은 대결의 작은 번호)이어야 한다. 삭제·복구·수정(PR-B)·대결 삭제(PR-C)가 함께 쓴다.
 * 입력 픽은 모두 유효(삭제되지 않은) 세트의 것이어야 한다.
 */
public final class FearlessConflicts {

    private FearlessConflicts() {
    }

    /** 유효 세트의 픽 한 개. */
    public record SetPick(long setId, long duelId, int setNo, String championKey) {
    }

    /** 다른 세트와 겹친 챔피언 한 건. duelSeq는 화면 표시용 대결 번호. */
    public record Conflict(long setId, long duelId, int duelSeq, int setNo, String championKey) {
    }

    /** 대결 duelId의 setNo번 세트 시점에 이미 제한된 챔피언(앞선 체인 대결 전부 + 같은 대결의 작은 번호 세트 픽), 키 오름차순. */
    public static List<String> restrictedAt(List<ChainDuel> sessionDuels, long duelId, int setNo,
            Collection<SetPick> validPicks) {
        List<Long> chain = FearlessChain.chainDuelIds(sessionDuels, duelId);
        int own = chain.size() - 1;
        TreeSet<String> restricted = new TreeSet<>();
        for (SetPick pick : validPicks) {
            int index = chain.indexOf(pick.duelId());
            if (index < 0) {
                continue;
            }
            if (index < own || pick.setNo() < setNo) {
                restricted.add(pick.championKey());
            }
        }
        return List.copyOf(restricted);
    }

    /**
     * 대상 세트(duelId, setNo)의 픽 targetPicks가 다른 유효 세트와 겹치는 목록. 앞쪽·뒤쪽 모두 포함하며
     * RESET으로 끊긴 대결의 세트는 제외한다. 대결 seq, 세트 번호, 챔피언 키 순.
     *
     * @param targetSetId 대상 세트 id(validPicks에 있어도 제외한다)
     */
    public static List<Conflict> conflicts(List<ChainDuel> sessionDuels, long targetSetId, long duelId, int setNo,
            Collection<String> targetPicks, Collection<SetPick> validPicks) {
        Set<String> mine = new HashSet<>(targetPicks);
        Map<Long, Integer> seqs = new HashMap<>();
        sessionDuels.forEach(d -> seqs.put(d.duelId(), d.seq()));
        Map<Long, List<Long>> chains = new HashMap<>();
        List<Conflict> found = new ArrayList<>();
        for (SetPick other : validPicks) {
            if (other.setId() == targetSetId || !mine.contains(other.championKey())
                    || !seqs.containsKey(other.duelId())) {
                continue;
            }
            boolean otherBefore = precedes(chains, sessionDuels, other.duelId(), other.setNo(), duelId, setNo);
            boolean otherAfter = precedes(chains, sessionDuels, duelId, setNo, other.duelId(), other.setNo());
            if (otherBefore || otherAfter) {
                found.add(new Conflict(other.setId(), other.duelId(), seqs.get(other.duelId()), other.setNo(),
                        other.championKey()));
            }
        }
        found.sort(Comparator.comparingInt(Conflict::duelSeq).thenComparingInt(Conflict::setNo)
                .thenComparing(Conflict::championKey));
        return found.stream().distinct().toList();
    }

    /** 대결 삭제가 뒤 대결의 제한 범위를 바꾸는가: 삭제할 대결이 RESET이고 바로 다음 seq 대결이 CONTINUE일 때(설계 §1.2-7). */
    public static boolean deletionChangesChain(List<ChainDuel> sessionDuels, long duelId) {
        List<ChainDuel> ordered = new ArrayList<>(sessionDuels);
        ordered.sort(Comparator.comparingInt(ChainDuel::seq));
        for (int i = 0; i < ordered.size(); i++) {
            if (ordered.get(i).duelId() == duelId) {
                return !ordered.get(i).continuesPrevious() && i + 1 < ordered.size()
                        && ordered.get(i + 1).continuesPrevious();
            }
        }
        throw new IllegalArgumentException("묶음에 없는 대결입니다: " + duelId);
    }

    // earlier 세트가 later 세트의 제한 범위에 들어가는가
    private static boolean precedes(Map<Long, List<Long>> chains, List<ChainDuel> sessionDuels, long earlierDuel,
            int earlierNo, long laterDuel, int laterNo) {
        List<Long> chain = chains.computeIfAbsent(laterDuel, id -> FearlessChain.chainDuelIds(sessionDuels, id));
        if (!chain.contains(earlierDuel)) {
            return false;
        }
        return earlierDuel != laterDuel || earlierNo < laterNo;
    }
}
