package com.lolinhouse.domain.set;

import com.lolinhouse.domain.Side;
import java.util.ArrayList;
import java.util.List;

/**
 * 팀별 밴(화면 순서)을 드래프트 순서 B1 R1 B2 R2 B3 R3 B4 R4 B5 R5 로 바꾼다.
 * 밴은 세트마다 반드시 10개라서 비거나 모자라면 예외다.
 */
public final class BanOrder {

    private static final int BANS_PER_TEAM = 5;

    private BanOrder() {
    }

    /** 밴 한 턴: 턴 번호(1~10), 밴한 진영, 챔피언 키. */
    public record BanTurn(int turn, Side side, String championKey) {

        /** "B1", "R3" 같은 표기. */
        public String label() {
            return (side == Side.BLUE ? "B" : "R") + ((turn + 1) / 2);
        }
    }

    /**
     * @param topSide    위쪽 팀 진영(아래쪽 팀은 반대)
     * @param topBans    위쪽 팀 밴 5개. 화면 순서(윗줄 3개 왼→오, 아랫줄 2개 왼→오)
     * @param bottomBans 아래쪽 팀 밴 5개. 같은 화면 순서
     */
    public static List<BanTurn> build(Side topSide, List<String> topBans, List<String> bottomBans) {
        if (topSide == null) {
            throw new IllegalArgumentException("위쪽 팀 진영을 선택해야 합니다.");
        }
        validate(topBans);
        validate(bottomBans);

        List<String> blueBans = topSide == Side.BLUE ? topBans : bottomBans;
        List<String> redBans = topSide == Side.BLUE ? bottomBans : topBans;

        List<BanTurn> turns = new ArrayList<>();
        for (int i = 0; i < BANS_PER_TEAM; i++) {
            // k번째 밴끼리 짝을 지어 블루 먼저: 턴 2k-1 = Bk, 턴 2k = Rk
            turns.add(new BanTurn(2 * i + 1, Side.BLUE, blueBans.get(i)));
            turns.add(new BanTurn(2 * i + 2, Side.RED, redBans.get(i)));
        }
        return turns;
    }

    private static void validate(List<String> bans) {
        if (bans == null || bans.size() != BANS_PER_TEAM) {
            throw new IllegalArgumentException("팀당 밴은 정확히 5개여야 합니다.");
        }
        for (String ban : bans) {
            if (ban == null || ban.isBlank()) {
                throw new IllegalArgumentException("비어 있는 밴 챔피언이 있습니다. 밴 10개는 모두 필수입니다.");
            }
        }
    }
}
