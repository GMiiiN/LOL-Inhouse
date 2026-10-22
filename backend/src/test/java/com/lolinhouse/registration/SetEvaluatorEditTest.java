package com.lolinhouse.registration;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.lolinhouse.domain.DuelTeam;
import com.lolinhouse.domain.Side;
import com.lolinhouse.domain.set.FearlessChain.ChainDuel;
import com.lolinhouse.domain.set.FearlessConflicts.SetPick;
import com.lolinhouse.domain.set.TeamChangeDetector.Kind;
import com.lolinhouse.registration.RegistrationFixtures.Req;
import org.junit.jupiter.api.Test;

import static com.lolinhouse.registration.RegistrationFixtures.catalog;
import static com.lolinhouse.registration.RegistrationFixtures.champion;
import static com.lolinhouse.registration.RegistrationFixtures.existing;
import static com.lolinhouse.registration.RegistrationFixtures.players;
import static com.lolinhouse.registration.RegistrationFixtures.withChampion;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 역할: SetEvaluator.evaluateEdit(저장된 세트 수정 평가) 규칙 - A/B를 수정 전 같은 세트의 A팀에 고정, 팀 변경 감지 없음,
 * 경기 시간 모름, 앞쪽 피어리스 오류와 뒤쪽 경고, 앞뒤 세트 A팀 경고. DB 없이 돈다.
 */
class SetEvaluatorEditTest {

    private static final Set<String> OLD_TEAM1 = Set.of("1", "2", "3", "4", "5");
    private static final List<ChainDuel> ONE_DUEL = List.of(new ChainDuel(1L, 1, false));

    private final SetEvaluator evaluator = new SetEvaluator();

    private EditContext context(int setNo, Set<String> previous, Set<String> next, List<SetPick> others) {
        return new EditContext(50L, 1L, setNo, OLD_TEAM1, previous, next, ONE_DUEL, others);
    }

    private EditResult evaluate(Req request, EditContext context) {
        return evaluator.evaluateEdit(request.build(), context, players(), catalog(), fingerprint -> Optional.empty());
    }

    @Test
    void 위쪽_팀이_수정_전_A팀과_같으면_A팀이고_팀_변경_감지는_없다() {
        EditResult result = evaluate(new Req(), context(3, null, null, List.of()));

        assertThat(result.evaluation().errors()).isEmpty();
        assertThat(result.evaluation().setNo()).isEqualTo(3);
        assertThat(result.evaluation().topTeam()).isEqualTo(DuelTeam.TEAM1);
        assertThat(result.evaluation().detection().overlap()).isEqualTo(5);
        assertThat(result.evaluation().teamChange().kind()).isEqualTo(Kind.NONE);
    }

    @Test
    void 한_명을_바꿔도_A팀_판별은_유지된다() {
        Req request = new Req().topRow(0, row -> existing(row, 11));

        EditResult result = evaluate(request, context(3, null, null, List.of()));

        assertThat(result.evaluation().topTeam()).isEqualTo(DuelTeam.TEAM1);
        assertThat(result.evaluation().detection().overlap()).isEqualTo(4);
    }

    @Test
    void 위쪽_팀을_통째로_B팀_선수로_바꾸면_선수를_따라가_B팀이_된다() {
        Req request = new Req();
        for (int i = 0; i < 5; i++) {
            long topId = 6 + i;
            long bottomId = 1 + i;
            request.topRow(i, row -> existing(row, topId));
            request.bottomRow(i, row -> existing(row, bottomId));
        }

        EditResult result = evaluate(request, context(3, null, null, List.of()));

        assertThat(result.evaluation().topTeam()).isEqualTo(DuelTeam.TEAM2);
        assertThat(result.evaluation().team1Side()).isEqualTo(Side.RED);
    }

    @Test
    void 첫_세트의_진영을_바꾸면_같은_A팀을_유지하고_진영_경고만_낸다() {
        Req request = new Req();
        request.topSide = Side.RED;

        EditResult result = evaluate(request, context(1, null, null, List.of()));

        assertThat(result.evaluation().topTeam()).isEqualTo(DuelTeam.TEAM1);
        assertThat(result.evaluation().team1Side()).isEqualTo(Side.RED);
        assertThat(result.evaluation().errors()).isEmpty();
        assertThat(result.evaluation().warnings()).anyMatch(w -> w.contains("1세트") && w.contains("블루"));
    }

    @Test
    void 경기_시간이_빈_문자열이면_모름으로_지문에_물음표가_들어간다() {
        Req request = new Req();
        request.duration = "";

        EditResult result = evaluate(request, context(1, null, null, List.of()));

        assertThat(result.evaluation().errors()).isEmpty();
        assertThat(result.evaluation().fingerprint()).contains("|?|");
    }

    @Test
    void 경기_시간_형식이_틀리면_오류() {
        Req request = new Req();
        request.duration = "25분";

        EditResult result = evaluate(request, context(1, null, null, List.of()));

        assertThat(result.evaluation().errors()).anyMatch(e -> e.contains("경기 시간 형식"));
        assertThat(result.evaluation().fingerprint()).isNull();
    }

    @Test
    void 앞_세트와_픽이_겹치면_오류_뒤_세트와_겹치면_경고와_충돌_목록() {
        List<SetPick> others = List.of(new SetPick(10L, 1L, 1, champion(0)), new SetPick(11L, 1L, 4, champion(5)));
        Req request = new Req(); // 위쪽 픽 00~04, 아래쪽 픽 05~09

        EditResult result = evaluate(request, context(3, null, null, others));

        assertThat(result.evaluation().errors()).anyMatch(e -> e.contains("피어리스 위반") && e.contains(champion(0)));
        assertThat(result.evaluation().errors()).noneMatch(e -> e.contains(champion(5)));
        assertThat(result.fearlessConflicts()).hasSize(1);
        assertThat(result.fearlessConflicts().get(0).setId()).isEqualTo(11L);
        assertThat(result.fearlessConflicts().get(0).championKey()).isEqualTo(champion(5));
        assertThat(result.evaluation().warnings()).anyMatch(w -> w.contains("뒤쪽 세트") && w.contains(champion(5)));
    }

    @Test
    void 뒤_세트와만_겹치면_오류_없이_통과한다() {
        List<SetPick> others = List.of(new SetPick(11L, 1L, 4, champion(2)));

        EditResult result = evaluate(new Req(), context(3, null, null, others));

        assertThat(result.evaluation().errors()).isEmpty();
        assertThat(result.fearlessConflicts()).hasSize(1);
    }

    @Test
    void 새_A팀이_앞_세트_A팀과_3명_미만_겹치면_경고() {
        Set<String> previous = Set.of("1", "2", "21", "22", "23");

        EditResult result = evaluate(new Req(), context(3, previous, null, List.of()));

        assertThat(result.evaluation().errors()).isEmpty();
        assertThat(result.evaluation().warnings()).anyMatch(w -> w.contains("앞뒤 세트와 A팀 구성이 맞지 않습니다"));
    }

    @Test
    void 앞뒤_세트_A팀과_3명_이상_겹치면_경고가_없다() {
        Set<String> previous = Set.of("1", "2", "3", "21", "22");

        EditResult result = evaluate(new Req(), context(3, previous, OLD_TEAM1, List.of()));

        assertThat(result.evaluation().warnings()).noneMatch(w -> w.contains("앞뒤 세트"));
    }

    @Test
    void 선수를_안_골랐으면_판별_보류와_오류() {
        Req request = new Req().topRow(0, RegistrationFixtures::unresolved);

        EditResult result = evaluate(request, context(3, null, null, List.of()));

        assertThat(result.evaluation().topTeam()).isNull();
        assertThat(result.evaluation().detection().method()).isEqualTo(DetectionMethod.PENDING);
        assertThat(result.evaluation().errors()).isNotEmpty();
    }

    @Test
    void 자기_세트_챔피언을_그대로_두면_충돌이_없다() {
        Req request = new Req().topRow(0, row -> withChampion(row, champion(12)));

        EditResult result = evaluate(request, context(3, null, null, List.of()));

        assertThat(result.fearlessConflicts()).isEmpty();
    }
}
