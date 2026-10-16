package com.lolinhouse.domain.set;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 피어리스 규칙: 같은 대결의 이전(삭제되지 않은) 세트에서 양 팀이 픽한 챔피언은 다시 쓸 수 없다. 밴은 제한하지 않는다.
 * 위반은 경고일 뿐 저장을 막지 않는다(인식 오류나 초기화 누락일 수 있어서). 초기화는 새 대결을 만드는 것이라 제한이 비어 시작한다.
 * 설명: docs/study/06-domain-set-rules.md (유저플로우 §5.5, §6.2)
 */
public final class Fearless {

    private Fearless() {
    }

    /** 이미 치른 세트의 픽 10개와 밴 10개. 밴은 받아 두지만 제한 계산에는 쓰지 않는다. */
    public record PlayedSet(List<String> picks, List<String> bans) {

        public PlayedSet {
            picks = SetDraft.unmodifiableCopy(picks);
            bans = SetDraft.unmodifiableCopy(bans);
        }
    }

    /** 이전 세트들의 픽을 합친 제한 챔피언 집합(수정 불가). 삭제된 세트는 호출하는 쪽에서 빼고 넘긴다. */
    public static Set<String> restrictedChampions(List<PlayedSet> earlierSets) {
        Set<String> restricted = new HashSet<>();
        earlierSets.forEach(set -> restricted.addAll(set.picks()));
        return Collections.unmodifiableSet(restricted);
    }

    /** 새 세트의 픽 중 제한에 걸린 챔피언(픽 순서대로, 중복 없이). */
    public static List<String> violations(Set<String> restricted, Collection<String> newPicks) {
        return newPicks.stream().filter(restricted::contains).distinct().toList();
    }
}
