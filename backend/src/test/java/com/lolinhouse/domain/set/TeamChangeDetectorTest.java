package com.lolinhouse.domain.set;

import static org.assertj.core.api.Assertions.assertThat;

import com.lolinhouse.domain.Position;
import com.lolinhouse.domain.set.TeamChangeDetector.Kind;
import com.lolinhouse.domain.set.TeamChangeDetector.TeamChange;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 역할: 팀 변경 감지(직전 세트 대비 NONE / SAME_LANE_SWAP / POSITION_CHANGE / RESHUFFLE / PENDING) 규칙 검증.
 * 선수는 a~j, 팀은 "탑·정글·미드·원딜·서폿" 순서로 id를 나열해 만든다. 직전 1팀 = a~e, 2팀 = f~j.
 */
class TeamChangeDetectorTest {

    private static final Map<String, Position> PREV_TEAM1 = team("a", "b", "c", "d", "e");
    private static final Map<String, Position> PREV_TEAM2 = team("f", "g", "h", "i", "j");

    /** 탑→서폿 순서대로 선수 id를 배정한 팀. */
    private static Map<String, Position> team(String top, String jungle, String mid, String adc, String support) {
        Map<String, Position> team = new LinkedHashMap<>();
        team.put(top, Position.TOP);
        team.put(jungle, Position.JUNGLE);
        team.put(mid, Position.MID);
        team.put(adc, Position.ADC);
        team.put(support, Position.SUPPORT);
        return team;
    }

    private static TeamChange detect(Map<String, Position> top, Map<String, Position> bottom) {
        return TeamChangeDetector.detect(PREV_TEAM1, PREV_TEAM2, top, bottom);
    }

    // ----- NONE -----

    @Test
    void 팀과_포지션이_그대로면_NONE() {
        assertThat(detect(PREV_TEAM1, PREV_TEAM2).kind()).isEqualTo(Kind.NONE);
    }

    @Test
    void 위쪽_팀이_직전_2팀이어도_구성이_같으면_NONE() {
        assertThat(detect(PREV_TEAM2, PREV_TEAM1).kind()).isEqualTo(Kind.NONE);
    }

    @Test
    void 화면_행_순서가_달라도_포지션이_같으면_NONE() {
        Map<String, Position> shuffledRows = new LinkedHashMap<>();
        shuffledRows.put("e", Position.SUPPORT);
        shuffledRows.put("a", Position.TOP);
        shuffledRows.put("d", Position.ADC);
        shuffledRows.put("b", Position.JUNGLE);
        shuffledRows.put("c", Position.MID);

        assertThat(detect(shuffledRows, PREV_TEAM2).kind()).isEqualTo(Kind.NONE);
    }

    @Test
    void 직전_세트가_없으면_비교하지_않고_NONE() {
        TeamChange change = TeamChangeDetector.detect(null, null, PREV_TEAM1, PREV_TEAM2);

        assertThat(change.kind()).isEqualTo(Kind.NONE);
    }

    // ----- SAME_LANE_SWAP -----

    @Test
    void 같은_포지션끼리_팀을_맞바꾸면_SAME_LANE_SWAP_1건() {
        // 서폿 e <-> j
        TeamChange change = detect(team("a", "b", "c", "d", "j"), team("f", "g", "h", "i", "e"));

        assertThat(change.kind()).isEqualTo(Kind.SAME_LANE_SWAP);
        assertThat(change.swaps()).isEqualTo(1);
        assertThat(change.details()).isNotEmpty();
    }

    @Test
    void 위쪽_팀이_직전_2팀_쪽이어도_맞라인_교환을_센다() {
        // 위쪽 = 직전 2팀 기반(서폿 e가 들어옴), 아래쪽 = 직전 1팀 기반(서폿 j가 들어옴)
        TeamChange change = detect(team("f", "g", "h", "i", "e"), team("a", "b", "c", "d", "j"));

        assertThat(change.kind()).isEqualTo(Kind.SAME_LANE_SWAP);
        assertThat(change.swaps()).isEqualTo(1);
    }

    @Test
    void 두_쌍을_맞바꾸면_2건() {
        // 정글 b <-> g, 원딜 d <-> i
        TeamChange change = detect(team("a", "g", "c", "i", "e"), team("f", "b", "h", "d", "j"));

        assertThat(change.kind()).isEqualTo(Kind.SAME_LANE_SWAP);
        assertThat(change.swaps()).isEqualTo(2);
    }

    @Test
    void 세_쌍을_맞바꾸면_더_많이_남은_쪽을_기준으로_2건이다() {
        // 탑·정글·미드를 맞바꾸면 위쪽 팀은 직전 2팀 3명 + 직전 1팀 2명. 가까운 쪽(직전 2팀)을 기준으로 2건
        TeamChange change = detect(team("f", "g", "h", "d", "e"), team("a", "b", "c", "i", "j"));

        assertThat(change.kind()).isEqualTo(Kind.SAME_LANE_SWAP);
        assertThat(change.swaps()).isEqualTo(2);
    }

    // ----- POSITION_CHANGE -----

    @Test
    void 같은_팀_안에서_탑과_미드가_바뀌면_POSITION_CHANGE() {
        TeamChange change = detect(team("c", "b", "a", "d", "e"), PREV_TEAM2);

        assertThat(change.kind()).isEqualTo(Kind.POSITION_CHANGE);
        assertThat(change.details()).isNotEmpty();
    }

    @Test
    void 위쪽_아래쪽이_뒤바뀐_상태에서도_포지션_변경을_감지한다() {
        // 아래쪽 팀이 직전 2팀인데 정글과 서폿이 서로 바뀜
        TeamChange change = detect(team("f", "j", "h", "i", "g"), PREV_TEAM1);

        assertThat(change.kind()).isEqualTo(Kind.POSITION_CHANGE);
    }

    // ----- RESHUFFLE -----

    @Test
    void 라인이_다른_교환은_RESHUFFLE() {
        // a(탑)가 정글로 g와 맞바꿈: 선수 10명은 같지만 포지션이 달라졌다
        TeamChange change = detect(team("g", "b", "c", "d", "e"), team("f", "a", "h", "i", "j"));

        assertThat(change.kind()).isEqualTo(Kind.RESHUFFLE);
    }

    @Test
    void 맞라인_교환과_포지션_변경이_섞이면_RESHUFFLE() {
        // 서폿 e <-> j 맞교환 + 같은 팀 안 탑/미드 변경
        TeamChange change = detect(team("c", "b", "a", "d", "j"), team("f", "g", "h", "i", "e"));

        assertThat(change.kind()).isEqualTo(Kind.RESHUFFLE);
    }

    @Test
    void 새_사람이_투입되면_RESHUFFLE() {
        TeamChange change = detect(team("a", "b", "c", "d", "z"), PREV_TEAM2);

        assertThat(change.kind()).isEqualTo(Kind.RESHUFFLE);
    }

    @Test
    void 새_선수_자리표시_id도_새_사람으로_본다() {
        TeamChange change = detect(team("a", "b", "c", "d", "new:top:5"), PREV_TEAM2);

        assertThat(change.kind()).isEqualTo(Kind.RESHUFFLE);
    }

    @Test
    void 팀이_통째로_섞이면_RESHUFFLE() {
        TeamChange change = detect(team("a", "h", "b", "i", "j"), team("f", "c", "g", "d", "e"));

        assertThat(change.kind()).isEqualTo(Kind.RESHUFFLE);
    }

    // ----- PENDING -----

    @Test
    void 선수가_정해지지_않았으면_PENDING() {
        Map<String, Position> withMissing = new HashMap<>(PREV_TEAM1);
        withMissing.remove("e");
        withMissing.put(null, Position.SUPPORT);

        assertThat(detect(withMissing, PREV_TEAM2).kind()).isEqualTo(Kind.PENDING);
    }

    @Test
    void 팀_인원이_5명이_아니면_PENDING() {
        Map<String, Position> tooFew = new HashMap<>(PREV_TEAM1);
        tooFew.remove("e");

        assertThat(detect(tooFew, PREV_TEAM2).kind()).isEqualTo(Kind.PENDING);
    }

    @Test
    void 팀_정보가_null이면_PENDING() {
        assertThat(detect(null, PREV_TEAM2).kind()).isEqualTo(Kind.PENDING);
        assertThat(detect(PREV_TEAM1, null).kind()).isEqualTo(Kind.PENDING);
    }

    @Test
    void 선수가_미확정이면_직전_세트가_없어도_PENDING이다() {
        TeamChange change = TeamChangeDetector.detect(null, null, null, PREV_TEAM2);

        assertThat(change.kind()).isEqualTo(Kind.PENDING);
    }
}
