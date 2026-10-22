package com.lolinhouse.domain.team;

import com.lolinhouse.domain.tier.LaneTier;
import com.lolinhouse.domain.tier.TierSub;

/**
 * 역할: 수동 모드에서 한 라인의 두 사람 중 티어가 없는 사람의 이번 계산용 티어를 정한다(DB 저장 없음).
 * 담당자가 "누가 더 잘하는지 + 몇 티어 차이인지"를 입력한다. 점수는 호출 쪽이 기존 점수표(ScoreTable.points)로 계산한다.
 */
public final class TierGapEstimator {

    public static final int MAX_GAP = 5;

    private TierGapEstimator() {
    }

    /**
     * 한 라인의 두 사람의 티어 [첫째, 둘째]. 없으면 null. 둘 다 있으면 입력을 그대로 돌려주고 gap은 무시한다.
     * 한 명만 없으면 상대 티어 기준(더 잘하면 tier - n, 못하면 tier + n, sub는 상대와 같게, 1~6으로 자름).
     * 둘 다 없으면 못하는 쪽 6티어 MID, 잘하는 쪽 (6 - n)티어 MID(비슷함이면 둘 다 6티어 MID).
     */
    public static LaneTier[] estimate(LaneTier first, LaneTier second, TierGap gap) {
        if (first != null && second != null) {
            return new LaneTier[] {first, second};
        }
        int n = gap.stronger() == null ? 0 : gap.tiers();
        int strongerIndex = gap.stronger() == null ? -1 : gap.stronger();
        if (first == null && second == null) {
            LaneTier weak = new LaneTier(LaneTier.MAX_TIER, TierSub.MID);
            LaneTier strong = new LaneTier(LaneTier.MAX_TIER - n, TierSub.MID);
            return new LaneTier[] {strongerIndex == 0 ? strong : weak, strongerIndex == 1 ? strong : weak};
        }
        boolean firstMissing = first == null;
        LaneTier base = firstMissing ? second : first;
        boolean missingIsStronger = strongerIndex == (firstMissing ? 0 : 1);
        int tier = missingIsStronger ? base.tier() - n : base.tier() + n;
        tier = Math.max(LaneTier.MIN_TIER, Math.min(LaneTier.MAX_TIER, tier));
        LaneTier estimated = new LaneTier(tier, base.sub());
        return firstMissing ? new LaneTier[] {estimated, second} : new LaneTier[] {first, estimated};
    }

    /** 입력한 값(stronger는 picks 인덱스 0·1·null, tiers는 0~5)이 규칙에 맞는지. */
    public static boolean isValid(TierGap gap) {
        return gap != null && (gap.stronger() == null || gap.stronger() == 0 || gap.stronger() == 1)
                && gap.tiers() >= 0 && gap.tiers() <= MAX_GAP;
    }
}
