package com.lolinhouse.domain.tier;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.lolinhouse.domain.Position;
import com.lolinhouse.domain.tier.TierRecommender.Direction;
import com.lolinhouse.domain.tier.TierRecommender.Lane;
import com.lolinhouse.domain.tier.TierRecommender.Played;
import com.lolinhouse.domain.tier.TierRecommender.Recommendation;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** 역할: 티어 조정 추천의 점수 경계, 한 단계 이동 경계, 최소 경기, 라인 평균 기본값, 기준일 필터를 DB 없이 확인한다. */
class TierRecommenderTest {

    private static final LocalDate DAY = LocalDate.of(2140, 1, 10);

    private static Lane lane(long player, Position position, int tier, TierSub sub, LocalDate changedOn) {
        return new Lane(player, "p" + player, position, new LaneTier(tier, sub), changedOn);
    }

    /** 한 선수만 있으면 라인 평균 = 본인 KDA라 KDA 항은 0이고 승률 항만 남는다. */
    private static List<Played> games(long player, Position position, LocalDate date, int wins, int losses) {
        List<Played> list = new ArrayList<>();
        for (int i = 0; i < wins + losses; i++) {
            list.add(new Played(player, position, date, i < wins, 5, 2, 5));
        }
        return list;
    }

    @Test
    void 승률_항만으로_3전_전승은_한_단계_위_전패는_한_단계_아래() {
        List<Recommendation> up = TierRecommender.recommend(List.of(lane(1, Position.TOP, 3, TierSub.MID, null)),
                games(1, Position.TOP, DAY, 3, 0));
        List<Recommendation> down = TierRecommender.recommend(List.of(lane(1, Position.TOP, 3, TierSub.MID, null)),
                games(1, Position.TOP, DAY, 0, 3));

        assertThat(up).singleElement().satisfies(r -> {
            assertThat(r.direction()).isEqualTo(Direction.UP);
            assertThat(r.to()).isEqualTo(new LaneTier(3, TierSub.HIGH));
            assertThat(r.score()).isCloseTo(0.4286, within(0.0001));
            assertThat(r.games()).isEqualTo(3);
            assertThat(r.wins()).isEqualTo(3);
        });
        assertThat(down).singleElement().satisfies(r -> {
            assertThat(r.direction()).isEqualTo(Direction.DOWN);
            assertThat(r.to()).isEqualTo(new LaneTier(3, TierSub.LOW));
        });
    }

    @Test
    void 점수가_경계_안쪽이면_추천이_없다() {
        assertThat(TierRecommender.recommend(List.of(lane(1, Position.TOP, 3, TierSub.MID, null)),
                games(1, Position.TOP, DAY, 2, 1))).isEmpty();
    }

    @Test
    void 점수_0_35와_마이너스_0_35는_포함이고_바로_안쪽은_제외다() {
        // 4경기 2승이면 승률 항 0, KDA 항 = clamp(ratio-1) * 0.5
        assertThat(TierRecommender.score(2, 4, 1.7, 1.0)).isEqualTo(0.35);
        assertThat(TierRecommender.score(2, 4, 0.3, 1.0)).isEqualTo(-0.35);
        assertThat(TierRecommender.score(2, 4, 1.69, 1.0)).isLessThan(0.35);
        assertThat(TierRecommender.score(2, 4, 0.31, 1.0)).isGreaterThan(-0.35);
    }

    @Test
    void 정확히_경계_점수면_추천에_포함된다() {
        // 선수 1: 4경기 2승, K+A=17, D=10 -> 1.7. 선수 2: K+A=3, D=10. 라인 평균 = 20/20 = 1.0 -> score 0.35
        List<Played> played = new ArrayList<>();
        int[][] one = {{9, 3, 0}, {8, 3, 0}, {0, 2, 0}, {0, 2, 0}};
        for (int i = 0; i < 4; i++) {
            played.add(new Played(1, Position.MID, DAY, i < 2, one[i][0], one[i][1], one[i][2]));
        }
        played.add(new Played(2, Position.MID, DAY, true, 3, 10, 0));

        List<Recommendation> result = TierRecommender.recommend(List.of(lane(1, Position.MID, 3, TierSub.MID, null)),
                played);

        assertThat(result).singleElement().satisfies(r -> {
            assertThat(r.score()).isEqualTo(0.35);
            assertThat(r.direction()).isEqualTo(Direction.UP);
        });
    }

    @Test
    void 경기가_3미만이면_제외한다() {
        assertThat(TierRecommender.recommend(List.of(lane(1, Position.TOP, 3, TierSub.MID, null)),
                games(1, Position.TOP, DAY, 2, 0))).isEmpty();
    }

    @Test
    void 다른_라인으로_뛴_기록은_세지_않는다() {
        assertThat(TierRecommender.recommend(List.of(lane(1, Position.TOP, 3, TierSub.MID, null)),
                games(1, Position.MID, DAY, 3, 0))).isEmpty();
    }

    @Test
    void 한_단계_위_경계는_1티어_상에서_없고_상은_위_티어의_하로_간다() {
        assertThat(TierRecommender.step(new LaneTier(1, TierSub.HIGH), Direction.UP)).isEmpty();
        assertThat(TierRecommender.step(new LaneTier(2, TierSub.HIGH), Direction.UP))
                .contains(new LaneTier(1, TierSub.LOW));
        assertThat(TierRecommender.step(new LaneTier(1, TierSub.LOW), Direction.UP))
                .contains(new LaneTier(1, TierSub.MID));
        assertThat(TierRecommender.step(new LaneTier(1, TierSub.MID), Direction.UP))
                .contains(new LaneTier(1, TierSub.HIGH));
    }

    @Test
    void 한_단계_아래_경계는_6티어_하에서_없고_하는_아래_티어의_상으로_간다() {
        assertThat(TierRecommender.step(new LaneTier(6, TierSub.LOW), Direction.DOWN)).isEmpty();
        assertThat(TierRecommender.step(new LaneTier(5, TierSub.LOW), Direction.DOWN))
                .contains(new LaneTier(6, TierSub.HIGH));
        assertThat(TierRecommender.step(new LaneTier(6, TierSub.HIGH), Direction.DOWN))
                .contains(new LaneTier(6, TierSub.MID));
        assertThat(TierRecommender.step(new LaneTier(6, TierSub.MID), Direction.DOWN))
                .contains(new LaneTier(6, TierSub.LOW));
    }

    @Test
    void 경계_티어에서는_점수가_높아도_추천하지_않는다() {
        assertThat(TierRecommender.recommend(List.of(lane(1, Position.TOP, 1, TierSub.HIGH, null)),
                games(1, Position.TOP, DAY, 3, 0))).isEmpty();
        assertThat(TierRecommender.recommend(List.of(lane(1, Position.TOP, 6, TierSub.LOW, null)),
                games(1, Position.TOP, DAY, 0, 3))).isEmpty();
    }

    @Test
    void 라인_평균은_전체_기록의_KA합_나누기_D합이고_D가_0이면_2점5다() {
        List<Played> played = List.of(new Played(1, Position.TOP, DAY, true, 3, 1, 1),
                new Played(2, Position.TOP, DAY, true, 1, 3, 3),
                new Played(3, Position.MID, DAY, true, 4, 0, 1));

        assertThat(TierRecommender.laneAverages(played)).containsEntry(Position.TOP, 2.0)
                .containsEntry(Position.MID, 2.5);
    }

    @Test
    void 기준일_이후_기록만_세고_기준일_당일은_제외한다() {
        List<Played> played = new ArrayList<>(games(1, Position.TOP, DAY, 3, 0));
        Lane onDay = lane(1, Position.TOP, 3, TierSub.MID, DAY);
        Lane dayBefore = lane(1, Position.TOP, 3, TierSub.MID, DAY.minusDays(1));

        assertThat(TierRecommender.recommend(List.of(onDay), played)).isEmpty();
        assertThat(TierRecommender.recommend(List.of(dayBefore), played)).hasSize(1);
    }

    @Test
    void 기준일이_없으면_전체_기록을_본다() {
        List<Played> played = games(1, Position.TOP, LocalDate.of(2000, 1, 1), 3, 0);

        assertThat(TierRecommender.recommend(List.of(lane(1, Position.TOP, 3, TierSub.MID, null)), played))
                .hasSize(1);
    }

    @Test
    void 점수_절댓값_큰_순서로_정렬한다() {
        List<Played> played = new ArrayList<>();
        played.addAll(games(1, Position.TOP, DAY, 3, 0));
        played.addAll(games(2, Position.JUNGLE, DAY, 0, 6));
        List<Lane> lanes = List.of(lane(1, Position.TOP, 3, TierSub.MID, null),
                lane(2, Position.JUNGLE, 3, TierSub.MID, null));

        assertThat(TierRecommender.recommend(lanes, played)).extracting(Recommendation::playerId)
                .containsExactly(2L, 1L);
    }

    @Test
    void KDA_항은_마이너스_1에서_1로_자른다() {
        // 2승 2패(승률 항 0). KDA가 라인 평균의 3배여도 +0.5까지만 -> score 0.5
        assertThat(TierRecommender.score(2, 4, 30.0, 1.0)).isEqualTo(0.5);
        assertThat(TierRecommender.score(2, 4, 0.0, 1.0)).isEqualTo(-0.5);
        assertThat(Optional.of(TierRecommender.score(2, 4, 1.0, 0.0))).contains(0.0);
    }
}
