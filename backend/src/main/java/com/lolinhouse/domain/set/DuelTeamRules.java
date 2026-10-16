package com.lolinhouse.domain.set;

import com.lolinhouse.domain.DuelTeam;
import com.lolinhouse.domain.Side;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * 대결 안의 1팀/2팀 규칙: 세트별 기대 진영, 위쪽 팀이 1팀인지 판별, 업로드 화면 기본 진영, 진영 불일치 경고.
 * 설명: docs/study/06-domain-set-rules.md (유저플로우 §1, §5.1)
 */
public final class DuelTeamRules {

    private static final int TEAM_SIZE = 5;
    /** 직전 1팀과 이만큼 이상 겹치면 같은 팀으로 본다(같은 라인 교환이 있어도 5명 중 다수는 그대로). */
    private static final int MIN_OVERLAP_FOR_TEAM1 = 3;

    private DuelTeamRules() {
    }

    /** 위쪽 팀의 판별 결과와 직전 1팀과의 겹침 인원(확인 화면에 보여준다). */
    public record TeamDetection(DuelTeam team, int overlap) {
    }

    /** 1팀은 홀수 세트에 블루, 짝수 세트에 레드. 2팀은 반대. */
    public static Side expectedSide(DuelTeam team, int setNo) {
        if (setNo < 1) {
            throw new IllegalArgumentException("세트 번호는 1 이상이어야 합니다: " + setNo);
        }
        Side team1Side = setNo % 2 == 1 ? Side.BLUE : Side.RED;
        return team == DuelTeam.TEAM1 ? team1Side : team1Side.opposite();
    }

    /** 대결의 1세트는 블루 팀이 1팀이라서, 위쪽 팀이 블루면 1팀이다. */
    public static DuelTeam teamForFirstSet(Side topSide) {
        return topSide == Side.BLUE ? DuelTeam.TEAM1 : DuelTeam.TEAM2;
    }

    /** 2세트 이후: 직전 세트 1팀 선수와 3명 이상 겹치면 위쪽 팀은 1팀, 아니면 2팀. */
    public static TeamDetection detectTopTeam(Set<String> previousTeam1PlayerIds, Set<String> topTeamPlayerIds) {
        requireFivePlayers(previousTeam1PlayerIds, "직전 세트 1팀");
        requireFivePlayers(topTeamPlayerIds, "위쪽 팀");
        Set<String> common = new HashSet<>(previousTeam1PlayerIds);
        common.retainAll(topTeamPlayerIds);
        DuelTeam team = common.size() >= MIN_OVERLAP_FOR_TEAM1 ? DuelTeam.TEAM1 : DuelTeam.TEAM2;
        return new TeamDetection(team, common.size());
    }

    /**
     * 업로드 화면의 진영 기본값. "캡처한 사람의 팀 = 직전 세트의 위쪽 팀"이라고 가정한다.
     * 첫 세트는 기본값이 없다(previousTopTeam은 null이어도 된다).
     */
    public static Optional<Side> defaultTopSide(int setNo, DuelTeam previousTopTeam) {
        if (setNo == 1) {
            return Optional.empty();
        }
        if (previousTopTeam == null) {
            throw new IllegalArgumentException("2세트부터는 직전 세트의 위쪽 팀이 필요합니다.");
        }
        return Optional.of(expectedSide(previousTopTeam, setNo));
    }

    /** 판별한 위쪽 팀의 기대 진영과 고른 진영이 다르면 경고 문구를 돌려준다(저장은 막지 않는다). */
    public static Optional<String> sideWarning(DuelTeam topTeam, int setNo, Side chosenTopSide) {
        Side expected = expectedSide(topTeam, setNo);
        if (expected == chosenTopSide) {
            return Optional.empty();
        }
        return Optional.of(topTeam.label() + "은 " + setNo + "세트에 " + expected.label()
                + " 진영이 규칙인데 " + chosenTopSide.label() + "로 선택되었습니다. 진영이나 캡처한 팀을 확인하세요.");
    }

    private static void requireFivePlayers(Set<String> playerIds, String teamName) {
        if (playerIds == null || playerIds.size() != TEAM_SIZE) {
            throw new IllegalArgumentException(teamName + " 선수는 중복 없이 5명이어야 합니다.");
        }
    }
}
