package com.lolinhouse.domain.set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lolinhouse.domain.DuelTeam;
import com.lolinhouse.domain.Side;
import com.lolinhouse.domain.set.DuelTeamRules.TeamDetection;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DuelTeamRulesTest {

    // 샘플 1·2(2026/10/11)의 위쪽 팀 = p1~p5, 아래쪽 팀 = p6~p10. 샘플 1을 1세트로 보고 위쪽 팀 진영을 블루로 가정
    private static final Set<String> SET1_TEAM1 = Set.of("p1", "p2", "p3", "p4", "p5");

    @Test
    void 팀1은_홀수_세트에_블루_짝수_세트에_레드다() {
        assertThat(DuelTeamRules.expectedSide(DuelTeam.TEAM1, 1)).isEqualTo(Side.BLUE);
        assertThat(DuelTeamRules.expectedSide(DuelTeam.TEAM1, 2)).isEqualTo(Side.RED);
        assertThat(DuelTeamRules.expectedSide(DuelTeam.TEAM1, 3)).isEqualTo(Side.BLUE);
        assertThat(DuelTeamRules.expectedSide(DuelTeam.TEAM1, 4)).isEqualTo(Side.RED);
    }

    @Test
    void 팀2는_팀1의_반대_진영이다() {
        assertThat(DuelTeamRules.expectedSide(DuelTeam.TEAM2, 1)).isEqualTo(Side.RED);
        assertThat(DuelTeamRules.expectedSide(DuelTeam.TEAM2, 2)).isEqualTo(Side.BLUE);
        assertThat(DuelTeamRules.expectedSide(DuelTeam.TEAM2, 3)).isEqualTo(Side.RED);
    }

    @Test
    void 세트_번호가_1보다_작으면_예외() {
        assertThatThrownBy(() -> DuelTeamRules.expectedSide(DuelTeam.TEAM1, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("세트 번호");
    }

    @Test
    void 첫_세트는_위쪽_팀이_블루이면_1팀이다() {
        assertThat(DuelTeamRules.teamForFirstSet(Side.BLUE)).isEqualTo(DuelTeam.TEAM1);
        assertThat(DuelTeamRules.teamForFirstSet(Side.RED)).isEqualTo(DuelTeam.TEAM2);
    }

    @Test
    void 직전_1팀과_3명_이상_겹치면_위쪽_팀은_1팀이다() {
        // 샘플 3(2026/10/12) 위쪽 팀: p1, p5, p10, p4, p8 -> 샘플 1 위쪽 팀(p1~p5)과 p1·p4·p5 3명이 겹친다
        TeamDetection detection =
                DuelTeamRules.detectTopTeam(SET1_TEAM1, Set.of("p1", "p5", "p10", "p4", "p8"));

        assertThat(detection.team()).isEqualTo(DuelTeam.TEAM1);
        assertThat(detection.overlap()).isEqualTo(3);
    }

    @Test
    void 겹치는_선수가_2명_이하이면_위쪽_팀은_2팀이다() {
        TeamDetection detection =
                DuelTeamRules.detectTopTeam(SET1_TEAM1, Set.of("p1", "p5", "p10", "p9", "p8"));

        assertThat(detection.team()).isEqualTo(DuelTeam.TEAM2);
        assertThat(detection.overlap()).isEqualTo(2);
    }

    @Test
    void 완전히_같은_팀이면_겹침은_5명이고_1팀이다() {
        TeamDetection detection = DuelTeamRules.detectTopTeam(SET1_TEAM1, SET1_TEAM1);

        assertThat(detection.team()).isEqualTo(DuelTeam.TEAM1);
        assertThat(detection.overlap()).isEqualTo(5);
    }

    @Test
    void 상대_팀이_위쪽에_오면_겹침이_0명이고_2팀이다() {
        TeamDetection detection =
                DuelTeamRules.detectTopTeam(SET1_TEAM1, Set.of("p6", "p7", "p8", "p9", "p10"));

        assertThat(detection.team()).isEqualTo(DuelTeam.TEAM2);
        assertThat(detection.overlap()).isZero();
    }

    @Test
    void 선수가_5명이_아니면_판별할_수_없어_예외() {
        assertThatThrownBy(() -> DuelTeamRules.detectTopTeam(SET1_TEAM1, Set.of("p1", "p2", "p3", "p4")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("5명");
        assertThatThrownBy(() -> DuelTeamRules.detectTopTeam(Set.of("p1"), SET1_TEAM1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("5명");
    }

    @Test
    void 첫_세트에는_기본_진영이_없다() {
        assertThat(DuelTeamRules.defaultTopSide(1, null)).isEmpty();
    }

    @Test
    void 직전_세트의_위쪽_팀을_모르면_기본_진영이_없다() {
        // 수동 입력 세트는 top_team이 NULL이라 다음 세트 기본값을 줄 수 없다
        assertThat(DuelTeamRules.defaultTopSide(2, null)).isEmpty();
    }

    @Test
    void 직전_세트의_위쪽_팀이_1팀이었으면_다음_세트_기본_진영은_번갈아_바뀐다() {
        // 1세트 위쪽 팀 = 1팀 -> 2세트에도 위쪽에 1팀이 온다고 가정하면 1팀은 레드
        assertThat(DuelTeamRules.defaultTopSide(2, DuelTeam.TEAM1)).isEqualTo(Optional.of(Side.RED));
        assertThat(DuelTeamRules.defaultTopSide(3, DuelTeam.TEAM1)).isEqualTo(Optional.of(Side.BLUE));
    }

    @Test
    void 직전_세트의_위쪽_팀이_2팀이었으면_그_팀_기준으로_기본_진영을_정한다() {
        assertThat(DuelTeamRules.defaultTopSide(2, DuelTeam.TEAM2)).isEqualTo(Optional.of(Side.BLUE));
    }

    @Test
    void 고른_진영이_규칙과_같으면_경고가_없다() {
        assertThat(DuelTeamRules.sideWarning(DuelTeam.TEAM1, 3, Side.BLUE)).isEmpty();
        assertThat(DuelTeamRules.sideWarning(DuelTeam.TEAM2, 2, Side.BLUE)).isEmpty();
    }

    @Test
    void 고른_진영이_규칙과_다르면_경고한다() {
        Optional<String> warning = DuelTeamRules.sideWarning(DuelTeam.TEAM1, 2, Side.BLUE);

        assertThat(warning).isPresent();
        assertThat(warning.get()).contains("1팀", "2세트", "레드", "블루");
    }
}
