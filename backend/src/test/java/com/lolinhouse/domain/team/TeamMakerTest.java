package com.lolinhouse.domain.team;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.lolinhouse.domain.Position;
import com.lolinhouse.domain.tier.LaneTier;
import com.lolinhouse.domain.tier.ScoreTable;
import com.lolinhouse.domain.tier.TierSub;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 역할: 팀 짜기 규칙의 손계산 사례(설계 §7.2). 점수는 읽기 쉬운 표 TENS(티어 t = (7-t)*10점, 우열·라인 보정 0)를 쓰는 사례와
 * 기본표 사례로 나눈다.
 */
class TeamMakerTest {

    /** 1티어 60, 2티어 50, 3티어 40 ... 우열과 라인 보정은 0. */
    private static final ScoreTable TENS = new ScoreTable(List.of(60, 50, 40, 30, 20, 10),
            Map.of(TierSub.HIGH, 0, TierSub.MID, 0, TierSub.LOW, 0),
            Map.of(Position.TOP, 0, Position.JUNGLE, 0, Position.MID, 0, Position.ADC, 0, Position.SUPPORT, 0));

    private static LaneTier mid(int tier) {
        return new LaneTier(tier, TierSub.MID);
    }

    /** 라인 -> 티어 쌍을 번갈아 받는다: lanes(TOP, 2, JUNGLE, 3). */
    private static TeamMakerPlayer player(long id, Object... laneAndTier) {
        Map<Position, LaneTier> lanes = new EnumMap<>(Position.class);
        for (int i = 0; i < laneAndTier.length; i += 2) {
            lanes.put((Position) laneAndTier[i], mid((Integer) laneAndTier[i + 1]));
        }
        return new TeamMakerPlayer(id, lanes);
    }

    private static TeamMakerPlayer allLanes(long id, int tier) {
        return player(id, Position.TOP, tier, Position.JUNGLE, tier, Position.MID, tier, Position.ADC, tier,
                Position.SUPPORT, tier);
    }

    private static List<Long> ids(List<Slot> slots) {
        return slots.stream().map(Slot::playerId).toList();
    }

    // ---- 수동 ----

    @Test
    void 수동_탑만_다르면_16가지_모두_cost_32이고_상위_4개는_c_1_3_5_7_순서다() {
        Map<Position, List<TeamMakerPlayer>> picks = new EnumMap<>(Position.class);
        picks.put(Position.TOP, List.of(player(1, Position.TOP, 1), player(2, Position.TOP, 2)));
        picks.put(Position.JUNGLE, List.of(player(3, Position.JUNGLE, 3), player(4, Position.JUNGLE, 3)));
        picks.put(Position.MID, List.of(player(5, Position.MID, 3), player(6, Position.MID, 3)));
        picks.put(Position.ADC, List.of(player(7, Position.ADC, 3), player(8, Position.ADC, 3)));
        picks.put(Position.SUPPORT, List.of(player(9, Position.SUPPORT, 3), player(10, Position.SUPPORT, 3)));

        TeamMakeResult result = TeamMaker.manual(picks, Set.of(), ScoreTable.DEFAULT);

        // 기본표: 탑 60/52, 정글·미드 44, 원딜 40, 서폿 41 -> diff 8, gap 8, cost 32
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.options()).hasSize(4);
        assertThat(result.options()).allSatisfy(o -> {
            assertThat(o.diff()).isEqualTo(8);
            assertThat(o.gap()).isEqualTo(8);
            assertThat(o.cost()).isEqualTo(32);
            assertThat(o.blueSum()).isEqualTo(60 + 44 + 44 + 40 + 41);
            assertThat(o.redSum()).isEqualTo(52 + 44 + 44 + 40 + 41);
            assertThat(o.total()).isEqualTo(450);
            assertThat(o.blue().get(0).playerId()).isEqualTo(1);
        });
        // c=1: 탑 비트만 1 -> 나머지 라인은 둘째가 블루. c=3: 정글 첫째. c=5: 미드 첫째. c=7: 정글·미드 첫째.
        assertThat(ids(result.options().get(0).blue())).containsExactly(1L, 4L, 6L, 8L, 10L);
        assertThat(ids(result.options().get(1).blue())).containsExactly(1L, 3L, 6L, 8L, 10L);
        assertThat(ids(result.options().get(2).blue())).containsExactly(1L, 4L, 5L, 8L, 10L);
        assertThat(ids(result.options().get(3).blue())).containsExactly(1L, 3L, 5L, 8L, 10L);
        assertThat(ids(result.options().get(0).red())).containsExactly(2L, 3L, 5L, 7L, 9L);
    }

    @Test
    void 수동_같은_팀_조건이_걸리면_그_조합을_건너뛴다() {
        Map<Position, List<TeamMakerPlayer>> picks = new EnumMap<>(Position.class);
        picks.put(Position.TOP, List.of(player(1, Position.TOP, 3), player(2, Position.TOP, 3)));
        picks.put(Position.JUNGLE, List.of(player(3, Position.JUNGLE, 3), player(4, Position.JUNGLE, 3)));
        picks.put(Position.MID, List.of(player(5, Position.MID, 3), player(6, Position.MID, 3)));
        picks.put(Position.ADC, List.of(player(7, Position.ADC, 3), player(8, Position.ADC, 3)));
        picks.put(Position.SUPPORT, List.of(player(9, Position.SUPPORT, 3), player(10, Position.SUPPORT, 3)));

        // 1(탑 첫째, 항상 블루)과 4(정글 둘째)는 같은 팀: 정글 첫째가 블루인 c는 모두 탈락 -> 정글 둘째(4)가 블루
        TeamMakeResult result = TeamMaker.manual(picks, Set.of(new TogetherPair(1, 4)), TENS);

        assertThat(result.options()).hasSize(4);
        assertThat(result.options()).allSatisfy(o -> assertThat(ids(o.blue())).contains(1L, 4L));
        // 남는 c는 1,5,9,13,17,... 첫 번째는 c=1
        assertThat(ids(result.options().get(0).blue())).containsExactly(1L, 4L, 6L, 8L, 10L);
        assertThat(ids(result.options().get(1).blue())).containsExactly(1L, 4L, 5L, 8L, 10L);
    }

    @Test
    void 수동_같은_라인의_두_사람을_묶으면_조합이_없다() {
        Map<Position, List<TeamMakerPlayer>> picks = new EnumMap<>(Position.class);
        picks.put(Position.TOP, List.of(player(1, Position.TOP, 3), player(2, Position.TOP, 3)));
        picks.put(Position.JUNGLE, List.of(player(3, Position.JUNGLE, 3), player(4, Position.JUNGLE, 3)));
        picks.put(Position.MID, List.of(player(5, Position.MID, 3), player(6, Position.MID, 3)));
        picks.put(Position.ADC, List.of(player(7, Position.ADC, 3), player(8, Position.ADC, 3)));
        picks.put(Position.SUPPORT, List.of(player(9, Position.SUPPORT, 3), player(10, Position.SUPPORT, 3)));

        TeamMakeResult result = TeamMaker.manual(picks, Set.of(new TogetherPair(3, 4)), TENS);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.failure()).isEqualTo(TeamMakeResult.Failure.PAIR_UNSATISFIABLE);
    }

    // ---- 자동 ----

    @Test
    void 자동_전원이_같으면_마스크_오름차순_앞_4개가_나온다() {
        // 0번(id 1)은 항상 블루. 마스크 31(0~4), 47(0,1,2,3,5), 55(0,1,2,4,5), 59(0,1,3,4,5)
        List<TeamMakerPlayer> ordered = new ArrayList<>();
        for (long id = 1; id <= 10; id++) {
            ordered.add(allLanes(id, 3));
        }

        TeamMakeResult result = TeamMaker.auto(ordered, Set.of(), TENS);

        assertThat(result.options()).hasSize(4);
        assertThat(ids(result.options().get(0).blue())).containsExactly(1L, 2L, 3L, 4L, 5L);
        assertThat(ids(result.options().get(1).blue())).containsExactly(1L, 2L, 3L, 4L, 6L);
        assertThat(ids(result.options().get(2).blue())).containsExactly(1L, 2L, 3L, 5L, 6L);
        assertThat(ids(result.options().get(3).blue())).containsExactly(1L, 2L, 4L, 5L, 6L);
        // 순열 [0,1,2,3,4]가 먼저: 팀 안 i번째 사람이 i번째 라인
        assertThat(result.options().get(0).blue().get(1).position()).isEqualTo(Position.JUNGLE);
        assertThat(result.options().get(0).blue().get(1).playerId()).isEqualTo(2L);
        assertThat(result.options().get(0).cost()).isZero();
        assertThat(result.options().get(0).total()).isEqualTo(400);
    }

    /** 1~5번만 한 팀이 되도록 1번과 2~5번을 묶는다. */
    private static final Set<TogetherPair> FIX_FIRST_FIVE = Set.of(new TogetherPair(1, 2), new TogetherPair(1, 3),
            new TogetherPair(1, 4), new TogetherPair(1, 5));

    private static List<TeamMakerPlayer> fixedSplit(int b0Top, int b0Jg, int b1Top, int b1Jg) {
        List<TeamMakerPlayer> ordered = new ArrayList<>();
        ordered.add(player(1, Position.TOP, b0Top, Position.JUNGLE, b0Jg));
        ordered.add(player(2, Position.TOP, b1Top, Position.JUNGLE, b1Jg));
        ordered.add(player(3, Position.MID, 3));
        ordered.add(player(4, Position.ADC, 3));
        ordered.add(player(5, Position.SUPPORT, 3));
        ordered.add(player(6, Position.TOP, 2));
        ordered.add(player(7, Position.JUNGLE, 2));
        ordered.add(player(8, Position.MID, 3));
        ordered.add(player(9, Position.ADC, 3));
        ordered.add(player(10, Position.SUPPORT, 3));
        return ordered;
    }

    @Test
    void 자동_cost가_같으면_total이_큰_배정을_고른다() {
        // 블루: 1번 탑2(50)/정글2(50), 2번 탑1(60)/정글3(40). 레드 탑·정글 50/50, 나머지 40씩.
        // 배정 (1탑,2정글): 블루 탑 50·정글 40 -> 합 210 vs 220, diff 10, gap 10, cost 40, total 430
        // 배정 (1정글,2탑): 블루 탑 60·정글 50 -> 합 230 vs 220, diff 10, gap 10, cost 40, total 450 -> 이쪽
        TeamMakeResult result = TeamMaker.auto(fixedSplit(2, 2, 1, 3), FIX_FIRST_FIVE, TENS);

        assertThat(result.options()).hasSize(1);
        TeamOption option = result.options().get(0);
        assertThat(option.cost()).isEqualTo(40);
        assertThat(option.total()).isEqualTo(450);
        assertThat(option.blueSum()).isEqualTo(230);
        assertThat(option.redSum()).isEqualTo(220);
        assertThat(option.blue().get(0).playerId()).isEqualTo(2L);
        assertThat(option.blue().get(1).playerId()).isEqualTo(1L);
        assertThat(ids(option.red())).containsExactly(6L, 7L, 8L, 9L, 10L);
    }

    @Test
    void 자동_cost도_total도_같으면_먼저_찾은_배정을_남긴다() {
        // 1번·2번이 탑·정글 모두 2티어: 두 배정이 완전히 같다 -> 사전식 첫 순열 (1번 탑, 2번 정글)
        TeamMakeResult result = TeamMaker.auto(fixedSplit(2, 2, 2, 2), FIX_FIRST_FIVE, TENS);

        TeamOption option = result.options().get(0);
        assertThat(option.cost()).isZero();
        assertThat(option.blue().get(0).playerId()).isEqualTo(1L);
        assertThat(option.blue().get(1).playerId()).isEqualTo(2L);
    }

    @Test
    void 자동_그_라인_티어가_없는_순열은_버린다() {
        // 블루 쪽 1번·2번이 탑만 가능하면 정글을 맡을 블루 후보가 없다. 티어 없는 순열을 버리지 않으면 결과가 나와 버린다.
        List<TeamMakerPlayer> onlyTop = fixedSplit(2, 2, 2, 2);
        onlyTop.set(0, player(1, Position.TOP, 2));
        onlyTop.set(1, player(2, Position.TOP, 2));
        TeamMakeResult none = TeamMaker.auto(onlyTop, FIX_FIRST_FIVE, TENS);
        assertThat(none.isSuccess()).isFalse();
        assertThat(none.failure()).isEqualTo(TeamMakeResult.Failure.NO_VALID_SPLIT);
    }

    @Test
    void 자동_같은_팀_조건으로_다른_분할이_모두_막히면_하나만_남는다() {
        TeamMakeResult result = TeamMaker.auto(fixedSplit(2, 2, 1, 3), FIX_FIRST_FIVE, TENS);
        assertThat(result.options()).hasSize(1);
        assertThat(ids(result.options().get(0).blue())).containsExactlyInAnyOrder(1L, 2L, 3L, 4L, 5L);
    }

    @Test
    void 자동_전원이_탑만_가능하면_분할이_없다() {
        List<TeamMakerPlayer> ordered = new ArrayList<>();
        for (long id = 1; id <= 10; id++) {
            ordered.add(player(id, Position.TOP, 3));
        }
        TeamMakeResult result = TeamMaker.auto(ordered, Set.of(), TENS);
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.failure()).isEqualTo(TeamMakeResult.Failure.NO_VALID_SPLIT);
    }

    @Test
    void 자동_같은_팀_조건이_서로_모순이면_분할이_없다() {
        // 1번과 2번을 묶고, 1번과 2번이 함께 있는 마스크는 가능하지만 아래는 2~6번을 한 팀(5명)에 모두 묶어 1번과 못 묶이게 한다
        List<TeamMakerPlayer> ordered = new ArrayList<>();
        for (long id = 1; id <= 10; id++) {
            ordered.add(allLanes(id, 3));
        }
        // 2..6번이 모두 같은 팀이면 그 팀은 5명(2~6)이고, 1번은 반대 팀 -> 그런데 1번이 2번과도 같은 팀이어야 한다
        Set<TogetherPair> together = Set.of(new TogetherPair(2, 3), new TogetherPair(2, 4), new TogetherPair(2, 5),
                new TogetherPair(2, 6), new TogetherPair(1, 2));
        TeamMakeResult result = TeamMaker.auto(ordered, together, TENS);
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.failure()).isEqualTo(TeamMakeResult.Failure.NO_VALID_SPLIT);
    }

    // ---- 라인 고정 (자동) ----

    private static List<TeamMakerPlayer> tenAllLanes(int tier) {
        List<TeamMakerPlayer> ordered = new ArrayList<>();
        for (long id = 1; id <= 10; id++) {
            ordered.add(allLanes(id, tier));
        }
        return ordered;
    }

    private static Slot slotOf(TeamOption option, long playerId) {
        return java.util.stream.Stream.concat(option.blue().stream(), option.red().stream())
                .filter(s -> s.playerId() == playerId).findFirst().orElseThrow();
    }

    @Test
    void 고정한_사람은_모든_방안에서_고정_라인에만_놓인다() {
        TeamMakeResult result = TeamMaker.auto(tenAllLanes(3), Set.of(), TENS, Map.of(2L, Position.MID));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.options()).allSatisfy(o -> assertThat(slotOf(o, 2).position()).isEqualTo(Position.MID));
    }

    @Test
    void 고정이_없으면_3인자_auto와_같다() {
        TeamMakeResult three = TeamMaker.auto(tenAllLanes(3), Set.of(), TENS);
        TeamMakeResult four = TeamMaker.auto(tenAllLanes(3), Set.of(), TENS, Map.of());

        assertThat(four.options()).isEqualTo(three.options());
    }

    @Test
    void 티어_없는_라인에_고정하면_6티어_하_점수와_tier_null이다() {
        List<TeamMakerPlayer> ordered = new ArrayList<>(tenAllLanes(3));
        ordered.set(0, player(1, Position.TOP, 1));

        TeamMakeResult result = TeamMaker.auto(ordered, Set.of(), ScoreTable.DEFAULT, Map.of(1L, Position.ADC));

        // 기본표 6티어 하 = 20 - 3 + 원딜 보정(-4) = 13
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.options()).allSatisfy(o -> {
            Slot slot = slotOf(o, 1);
            assertThat(slot.position()).isEqualTo(Position.ADC);
            assertThat(slot.tier()).isNull();
            assertThat(slot.score()).isEqualTo(13);
        });
    }

    @Test
    void 한_라인에_2명을_고정하면_두_사람은_서로_다른_팀이다() {
        TeamMakeResult result = TeamMaker.auto(tenAllLanes(3), Set.of(), TENS,
                Map.of(1L, Position.TOP, 2L, Position.TOP));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.options()).allSatisfy(o -> {
            assertThat(ids(o.blue()).contains(1L)).isNotEqualTo(ids(o.blue()).contains(2L));
            assertThat(slotOf(o, 1).position()).isEqualTo(Position.TOP);
            assertThat(slotOf(o, 2).position()).isEqualTo(Position.TOP);
        });
    }

    @Test
    void 고정_때문에_자리가_모자라면_NO_VALID_SPLIT이다() {
        List<TeamMakerPlayer> ordered = new ArrayList<>(tenAllLanes(3));
        ordered.set(2, player(3, Position.JUNGLE, 3));
        ordered.set(3, player(4, Position.JUNGLE, 3));

        // 정글 전용 2명 + 정글에 고정한 2명 = 정글 4명, 양 팀 정글 자리는 2개
        TeamMakeResult result = TeamMaker.auto(ordered, Set.of(), TENS, Map.of(1L, Position.JUNGLE, 2L, Position.JUNGLE));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.failure()).isEqualTo(TeamMakeResult.Failure.NO_VALID_SPLIT);
    }

    @Test
    void 점수행렬_티어_없는_칸은_대체_점수와_hasTier_false다() {
        ScoreMatrix matrix = ScoreMatrix.of(List.of(player(1, Position.TOP, 1)), ScoreTable.DEFAULT);
        assertThat(matrix.cells().get(1L).get(Position.TOP).score()).isEqualTo(60);
        assertThat(matrix.cells().get(1L).get(Position.ADC).score()).isEqualTo(13);
        assertThat(matrix.cells().get(1L).get(Position.ADC).hasTier()).isFalse();
    }

    @Test
    void 최악_입력_전원이_5라인_티어를_가져도_수_초_안에_끝난다() {
        java.util.Random random = new java.util.Random(7);
        TierSub[] subs = TierSub.values();
        List<TeamMakerPlayer> ordered = new ArrayList<>();
        for (long id = 1; id <= 10; id++) {
            Map<Position, LaneTier> lanes = new EnumMap<>(Position.class);
            for (Position position : Position.values()) {
                lanes.put(position, new LaneTier(1 + random.nextInt(6), subs[random.nextInt(3)]));
            }
            ordered.add(new TeamMakerPlayer(id, lanes));
        }

        long start = System.nanoTime();
        TeamMakeResult result = TeamMaker.auto(ordered, Set.of(), ScoreTable.DEFAULT);
        long millis = (System.nanoTime() - start) / 1_000_000;

        System.out.println("worst-case auto millis=" + millis);
        assertThat(result.options()).hasSize(4);
        assertThat(millis).isLessThan(5_000);
    }
}
