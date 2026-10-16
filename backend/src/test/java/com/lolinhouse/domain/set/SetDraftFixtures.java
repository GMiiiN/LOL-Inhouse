package com.lolinhouse.domain.set;

import com.lolinhouse.domain.Position;
import com.lolinhouse.domain.Side;
import com.lolinhouse.domain.set.SetDraft.DraftPlayer;
import com.lolinhouse.domain.set.SetDraft.DraftTeam;
import java.util.ArrayList;
import java.util.List;

/**
 * 테스트용 세트 초안. 숫자는 결과창 샘플(2026-10-11, 10-12)의 실제 K/D/A·골드이고, 선수는 닉네임 대신 p1~p11 같은 id를 쓴다.
 * 챔피언은 샘플 이미지의 실제 챔피언이 아니라 구분용 임의 키다.
 */
final class SetDraftFixtures {

    private SetDraftFixtures() {
    }

    /** 샘플 1: 2026/10/11 18:14, 위쪽 팀 26/8/24 골드 39852, 아래쪽 팀 8/26/11 골드 29846, 캡처한 사람 원딜. */
    static SetDraft sample1() {
        DraftTeam top = team(List.of("p1", "p2", "p3", "p4", "p5"),
                PositionAssignment.topTeamPositions(Position.ADC), "top",
                new int[][] {{6, 3, 4}, {7, 2, 1}, {6, 1, 4}, {4, 2, 3}, {3, 0, 12}},
                new int[] {8423, 9391, 7960, 7847, 6231}, 26, 8, 24, 39852);
        DraftTeam bottom = team(List.of("p6", "p7", "p8", "p9", "p10"),
                PositionAssignment.bottomTeamPositions(), "bottom",
                new int[][] {{0, 6, 3}, {5, 7, 2}, {0, 4, 2}, {3, 6, 0}, {0, 3, 4}},
                new int[] {5348, 7457, 5504, 7442, 4095}, 8, 26, 11, 29846);
        return new SetDraft(Side.BLUE, Position.ADC, top, bottom, bans());
    }

    /** 샘플 3: 2026/10/12 25:42, 위쪽 팀 16/32/21 골드 44767, 아래쪽 팀 32/16/41 골드 60381, 캡처한 사람 미드. */
    static SetDraft sample3() {
        DraftTeam top = team(List.of("p1", "p5", "p10", "p4", "p8"),
                PositionAssignment.topTeamPositions(Position.MID), "top",
                new int[][] {{2, 6, 3}, {5, 11, 2}, {2, 5, 2}, {6, 5, 4}, {1, 5, 10}},
                new int[] {7662, 9426, 8025, 12917, 6737}, 16, 32, 21, 44767);
        DraftTeam bottom = team(List.of("p7", "p2", "p9", "p11", "p3"),
                PositionAssignment.bottomTeamPositions(), "bottom",
                new int[][] {{11, 3, 4}, {4, 0, 11}, {7, 5, 5}, {5, 0, 10}, {5, 8, 11}},
                new int[] {12840, 13203, 10421, 14523, 9394}, 32, 16, 41, 60381);
        return new SetDraft(Side.RED, Position.MID, top, bottom, bans());
    }

    static DraftTeam team(List<String> playerIds, List<Position> positions, String championPrefix,
                          int[][] kda, int[] golds, int displayedKills, int displayedDeaths,
                          int displayedAssists, int displayedGold) {
        List<DraftPlayer> players = new ArrayList<>();
        for (int i = 0; i < playerIds.size(); i++) {
            players.add(new DraftPlayer(playerIds.get(i), positions.get(i), championPrefix + (i + 1),
                    kda[i][0], kda[i][1], kda[i][2], golds[i]));
        }
        return new DraftTeam(players, displayedKills, displayedDeaths, displayedAssists, displayedGold);
    }

    static List<String> bans() {
        return List.of("ban1", "ban2", "ban3", "ban4", "ban5", "ban6", "ban7", "ban8", "ban9", "ban10");
    }
}
