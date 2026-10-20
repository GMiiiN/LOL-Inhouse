package com.lolinhouse.domain.set;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 피어리스 이어가기 체인: 같은 내전 묶음에서 이 대결부터 seq를 거슬러 올라가 처음 만나는 RESET 대결까지.
 * 그 대결들의 유효 세트 픽 전부가 이 대결의 제한 챔피언이다(마지막 초기화 이후 전부).
 * 묶음의 첫 대결은 항상 체인의 시작이다. 근거: docs/design/m4-design.md §11.1
 */
public final class FearlessChain {

    private FearlessChain() {
    }

    /** 체인 계산에 필요한 대결 정보. continuesPrevious=false가 RESET(제한 없이 시작), true가 CONTINUE. */
    public record ChainDuel(long duelId, int seq, boolean continuesPrevious) {
    }

    /**
     * @param duelsInSession 같은 묶음의 대결들(순서 무관)
     * @param targetDuelId   기준 대결 id
     * @return 체인에 속한 대결 id, 오래된 것부터(마지막이 기준 대결)
     */
    public static List<Long> chainDuelIds(List<ChainDuel> duelsInSession, long targetDuelId) {
        List<ChainDuel> ordered = new ArrayList<>(duelsInSession);
        ordered.sort(Comparator.comparingInt(ChainDuel::seq));
        int index = -1;
        for (int i = 0; i < ordered.size(); i++) {
            if (ordered.get(i).duelId() == targetDuelId) {
                index = i;
            }
        }
        if (index < 0) {
            throw new IllegalArgumentException("묶음에 없는 대결입니다: " + targetDuelId);
        }
        List<Long> chain = new ArrayList<>();
        chain.add(ordered.get(index).duelId());
        while (index > 0 && ordered.get(index).continuesPrevious()) {
            index--;
            chain.add(ordered.get(index).duelId());
        }
        Collections.reverse(chain);
        return chain;
    }
}
