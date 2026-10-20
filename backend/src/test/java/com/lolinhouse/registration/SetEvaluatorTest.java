package com.lolinhouse.registration;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.lolinhouse.domain.DuelTeam;
import com.lolinhouse.domain.Position;
import com.lolinhouse.domain.Side;
import com.lolinhouse.domain.set.TeamChangeDetector.Kind;
import com.lolinhouse.ingest.extract.ExtractedScoreboard.TopResult;
import com.lolinhouse.registration.RegistrationFixtures.Req;
import org.junit.jupiter.api.Test;

import static com.lolinhouse.registration.RegistrationFixtures.catalog;
import static com.lolinhouse.registration.RegistrationFixtures.champion;
import static com.lolinhouse.registration.RegistrationFixtures.existing;
import static com.lolinhouse.registration.RegistrationFixtures.firstSet;
import static com.lolinhouse.registration.RegistrationFixtures.newPlayer;
import static com.lolinhouse.registration.RegistrationFixtures.players;
import static com.lolinhouse.registration.RegistrationFixtures.secondSet;
import static com.lolinhouse.registration.RegistrationFixtures.unresolved;
import static com.lolinhouse.registration.RegistrationFixtures.withAlias;
import static com.lolinhouse.registration.RegistrationFixtures.withChampion;
import static com.lolinhouse.registration.RegistrationFixtures.withPosition;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 역할: SetEvaluator(저장 명령 평가) 규칙 검증 — A팀/B팀 판별, 선수 확정(resolution), 새 선수·별칭 규칙, 챔피언 id, 피어리스,
 * 지문과 중복, 팀 변경 감지, 그리고 이상한 입력(00:00, 5:7, 빈 밴)이 예외가 아니라 위반으로 나오는지. DB 없이 돈다.
 */
class SetEvaluatorTest {

    private final SetEvaluator evaluator = new SetEvaluator();

    private Evaluation evaluate(Req request, DuelState state) {
        return evaluate(request, state, fingerprint -> Optional.empty());
    }

    private Evaluation evaluate(Req request, DuelState state, DuplicateLookup lookup) {
        return evaluator.evaluate(request.build(), state, players(), catalog(), lookup);
    }

    // ----- A팀/B팀 판별 -----

    @Test
    void 첫_세트는_위쪽_팀이_블루면_A팀이다() {
        Evaluation evaluation = evaluate(new Req(), firstSet());

        assertThat(evaluation.errors()).isEmpty();
        assertThat(evaluation.setNo()).isEqualTo(1);
        assertThat(evaluation.topTeam()).isEqualTo(DuelTeam.TEAM1);
        assertThat(evaluation.team1Side()).isEqualTo(Side.BLUE);
        assertThat(evaluation.detection().method()).isEqualTo(DetectionMethod.FIRST_SET);
        assertThat(evaluation.teamChange().kind()).isEqualTo(Kind.NONE);
    }

    @Test
    void 첫_세트는_위쪽_팀이_레드면_B팀이고_A팀은_블루다() {
        Req request = new Req();
        request.topSide = Side.RED;

        Evaluation evaluation = evaluate(request, firstSet());

        assertThat(evaluation.topTeam()).isEqualTo(DuelTeam.TEAM2);
        assertThat(evaluation.team1Side()).isEqualTo(Side.BLUE);
    }

    @Test
    void 두번째_세트는_직전_A팀과_3명_이상_겹치면_A팀이고_겹침_인원을_돌려준다() {
        Req request = new Req();
        request.topSide = Side.RED; // 2세트에서 A팀은 레드가 규칙

        Evaluation evaluation = evaluate(request, secondSet());

        assertThat(evaluation.topTeam()).isEqualTo(DuelTeam.TEAM1);
        assertThat(evaluation.detection().method()).isEqualTo(DetectionMethod.OVERLAP);
        assertThat(evaluation.detection().overlap()).isEqualTo(5);
        assertThat(evaluation.team1Side()).isEqualTo(Side.RED);
        assertThat(evaluation.warnings()).noneMatch(w -> w.contains("진영"));
    }

    @Test
    void 위쪽_팀이_직전_B팀_선수들이면_B팀이다() {
        Req request = new Req();
        for (int i = 0; i < 5; i++) {
            long topPlayer = 6 + i;
            long bottomPlayer = 1 + i;
            request.topRow(i, row -> existing(row, topPlayer));
            request.bottomRow(i, row -> existing(row, bottomPlayer));
        }
        // 위쪽 배치(원딜 먼저)에서 직전 B팀 선수의 포지션이 달라지므로 포지션 변경 경고가 같이 나올 수 있다
        Evaluation evaluation = evaluate(request, secondSet());

        assertThat(evaluation.topTeam()).isEqualTo(DuelTeam.TEAM2);
        assertThat(evaluation.detection().overlap()).isZero();
    }

    @Test
    void 진영이_규칙과_다르면_경고일_뿐_오류가_아니다() {
        Evaluation evaluation = evaluate(new Req(), secondSet()); // A팀이 2세트에 블루

        assertThat(evaluation.errors()).isEmpty();
        assertThat(evaluation.warnings()).anyMatch(w -> w.contains("A팀") && w.contains("레드"));
    }

    // ----- 선수 확정 -----

    @Test
    void 선수를_안_골랐으면_판별을_보류하고_저장_오류를_낸다() {
        Req request = new Req();
        request.topRow(2, RegistrationFixtures::unresolved);

        Evaluation evaluation = evaluate(request, secondSet());

        assertThat(evaluation.topTeam()).isNull();
        assertThat(evaluation.team1Side()).isNull();
        assertThat(evaluation.detection().method()).isEqualTo(DetectionMethod.PENDING);
        assertThat(evaluation.teamChange().kind()).isEqualTo(Kind.PENDING);
        assertThat(evaluation.errors()).anyMatch(e -> e.contains("선수를") && e.contains("골라야"));
        assertThat(evaluation.errors()).anyMatch(e -> e.contains("A팀/B팀을 판별"));
    }

    @Test
    void 첫_세트라도_UNRESOLVED_행이_있으면_오류다() {
        Req request = new Req();
        request.bottomRow(0, RegistrationFixtures::unresolved);

        Evaluation evaluation = evaluate(request, firstSet());

        assertThat(evaluation.topTeam()).isEqualTo(DuelTeam.TEAM1);
        assertThat(evaluation.errors()).anyMatch(e -> e.contains("골라야"));
    }

    @Test
    void 새_선수는_자리표시_id로_판별하고_직전_팀과_겹치지_않는다() {
        Req request = new Req();
        request.topSide = Side.RED;
        request.topRow(4, row -> newPlayer(row, "신입"));

        Evaluation evaluation = evaluate(request, secondSet());

        assertThat(evaluation.errors()).isEmpty();
        assertThat(evaluation.topTeam()).isEqualTo(DuelTeam.TEAM1); // 4명이 겹침
        assertThat(evaluation.detection().overlap()).isEqualTo(4);
    }

    @Test
    void 같은_닉네임_키로_새_선수_두_명은_오류다() {
        Req request = new Req();
        request.topRow(0, row -> newPlayer(row, "Newbie"));
        request.bottomRow(0, row -> newPlayer(row, "newbie"));

        assertThat(evaluate(request, firstSet()).errors()).anyMatch(e -> e.contains("두 명 만들 수 없습니다"));
    }

    @Test
    void 새_선수_닉네임이_이미_다른_선수의_별칭이면_오류다() {
        Req request = new Req();
        request.topRow(0, row -> newPlayer(row, "Taken"));

        Evaluation evaluation = evaluator.evaluate(request.build(), firstSet(), players(Map.of("taken", 7L)),
                catalog(), fingerprint -> Optional.empty());

        assertThat(evaluation.errors()).anyMatch(e -> e.contains("이미 다른 선수의 별칭"));
    }

    @Test
    void 잘린_닉네임으로_새_선수를_만들면_오류다() {
        Req request = new Req();
        request.topRow(0, row -> newPlayer(row, "긴닉네…"));

        assertThat(evaluate(request, firstSet()).errors()).anyMatch(e -> e.contains("잘린 닉네임"));
    }

    @Test
    void 잘린_닉네임을_별칭으로_추가하면_오류다() {
        Req request = new Req();
        request.topRow(0, row -> withAlias(row, "긴닉네…"));

        assertThat(evaluate(request, firstSet()).errors()).anyMatch(e -> e.contains("잘린 닉네임") && e.contains("별칭"));
    }

    @Test
    void 전체_닉네임으로_고친_별칭_추가는_통과한다() {
        Req request = new Req();
        request.topRow(0, row -> withAlias(row, "긴닉네임전체"));

        assertThat(evaluate(request, firstSet()).errors()).isEmpty();
    }

    @Test
    void 존재하지_않는_선수_id는_오류다() {
        Req request = new Req();
        request.topRow(0, row -> existing(row, 999));

        assertThat(evaluate(request, firstSet()).errors()).anyMatch(e -> e.contains("존재하지 않는 선수"));
    }

    @Test
    void 같은_선수가_두_행에_있으면_오류다() {
        Req request = new Req();
        request.bottomRow(0, row -> existing(row, 1));

        assertThat(evaluate(request, firstSet()).errors()).anyMatch(e -> e.contains("중복 없이"));
    }

    // ----- 챔피언 -----

    @Test
    void 카탈로그에_없는_챔피언_id는_오류다() {
        Req request = new Req();
        request.topRow(0, row -> withChampion(row, "NoSuchChampion"));
        request.bottom.bans.set(0, "AnotherUnknown");

        Evaluation evaluation = evaluate(request, firstSet());

        assertThat(evaluation.errors()).anyMatch(e -> e.contains("NoSuchChampion") && e.contains("AnotherUnknown"));
    }

    @Test
    void 피어리스_제한_챔피언을_고르면_오류다() {
        Evaluation evaluation = evaluate(new Req(), secondSet(Set.of(champion(0))));

        assertThat(evaluation.errors()).anyMatch(e -> e.contains("피어리스") && e.contains(champion(0)));
    }

    @Test
    void 이어가기로_제한된_챔피언도_새_대결_첫_세트에서_오류다() {
        DuelState continued = DuelState.firstSet(null, Set.of(champion(3)));

        assertThat(evaluate(new Req(), continued).errors()).anyMatch(e -> e.contains("피어리스"));
    }

    @Test
    void 빈_밴과_빈_픽은_오류이지_예외가_아니다() {
        Req request = new Req();
        request.top.bans.set(1, null);
        request.bottom.bans.set(2, " ");
        request.topRow(0, row -> withChampion(row, null));

        Evaluation evaluation = evaluate(request, firstSet());

        assertThat(evaluation.errors()).anyMatch(e -> e.contains("밴 10개"));
        assertThat(evaluation.errors()).anyMatch(e -> e.contains("픽 챔피언이 비어"));
    }

    // ----- 날짜·시간·지문 -----

    @Test
    void 잘못된_경기_시간은_위반이고_지문은_null이다() {
        for (String duration : new String[] {"00:00", "5:7", "25:60", "abc", ""}) {
            Req request = new Req();
            request.duration = duration;

            Evaluation evaluation = evaluate(request, firstSet());

            assertThat(evaluation.errors()).as(duration).anyMatch(e -> e.contains("경기 시간"));
            assertThat(evaluation.fingerprint()).as(duration).isNull();
            assertThat(evaluation.duplicateSet()).isNull();
        }
    }

    @Test
    void 잘못된_날짜는_위반이다() {
        for (String date : new String[] {"2026-02-31", "2026/10/12", "", "abc"}) {
            Req request = new Req();
            request.shotDate = date;

            Evaluation evaluation = evaluate(request, firstSet());

            assertThat(evaluation.errors()).as(date).anyMatch(e -> e.contains("날짜"));
            assertThat(evaluation.fingerprint()).as(date).isNull();
        }
    }

    @Test
    void 지문은_위쪽_아래쪽_순서와_무관하다() {
        Req normal = new Req();
        Req swapped = new Req();
        // 위쪽/아래쪽 내용을 통째로 맞바꾼 같은 경기(반대편 캡처)
        for (int i = 0; i < 5; i++) {
            RowInput topRow = normal.top.rows.get(i);
            RowInput bottomRow = normal.bottom.rows.get(i);
            swapped.top.rows.set(i, bottomRow);
            swapped.bottom.rows.set(i, topRow);
        }
        swapped.top.sync();
        swapped.bottom.sync();

        assertThat(evaluate(swapped, firstSet()).fingerprint()).isEqualTo(evaluate(normal, firstSet()).fingerprint());
    }

    @Test
    void 같은_지문의_유효_세트가_있으면_duplicateSet을_채우고_오류도_계속_계산한다() {
        Req request = new Req();
        request.top.bans.set(0, null);
        DuplicateLookup existing = fingerprint -> Optional.of(new DuplicateSet(55L, 9L, 3));

        Evaluation evaluation = evaluate(request, firstSet(), existing);

        assertThat(evaluation.duplicateSet()).isEqualTo(new DuplicateSet(55L, 9L, 3));
        assertThat(evaluation.errors()).anyMatch(e -> e.contains("밴 10개"));
    }

    @Test
    void 중복이_없으면_duplicateSet은_null이고_지문은_날짜_시간_합계로_만든다() {
        Evaluation evaluation = evaluate(new Req(), firstSet());

        assertThat(evaluation.duplicateSet()).isNull();
        assertThat(evaluation.fingerprint()).startsWith("2026-10-12|1542|");
    }

    // ----- 팀 변경 감지 -----

    @Test
    void 맞라인_교환은_SAME_LANE_SWAP이고_저장_오류가_아니다() {
        Req request = new Req();
        request.topSide = Side.RED;
        // 서폿끼리 맞교환: 위쪽 서폿 자리에 직전 B팀 서폿(10), 아래쪽 서폿 자리에 직전 A팀 서폿(5)
        request.topRow(4, row -> existing(row, 10));
        request.bottomRow(4, row -> existing(row, 5));

        Evaluation evaluation = evaluate(request, secondSet());

        assertThat(evaluation.errors()).isEmpty();
        assertThat(evaluation.teamChange().kind()).isEqualTo(Kind.SAME_LANE_SWAP);
        assertThat(evaluation.teamChange().swaps()).isEqualTo(1);
        assertThat(evaluation.detection().uncertain()).isFalse();
    }

    @Test
    void 같은_팀_안_포지션_변경은_POSITION_CHANGE다() {
        Req request = new Req();
        request.topSide = Side.RED;
        request.topRow(1, row -> withPosition(row, Position.JUNGLE));
        request.topRow(2, row -> withPosition(row, Position.TOP));

        Evaluation evaluation = evaluate(request, secondSet());

        assertThat(evaluation.teamChange().kind()).isEqualTo(Kind.POSITION_CHANGE);
        assertThat(evaluation.detection().uncertain()).isFalse();
    }

    @Test
    void 재구성이면_판별이_불확실하다고_표시하고_진영_경고는_내지_않는다() {
        Req request = new Req(); // 블루 = 규칙과 다르지만 팀이 재구성돼 진영 경고가 의미 없다
        request.topRow(4, row -> existing(row, 11));
        request.topRow(3, row -> existing(row, 12));

        Evaluation evaluation = evaluate(request, secondSet());

        assertThat(evaluation.teamChange().kind()).isEqualTo(Kind.RESHUFFLE);
        assertThat(evaluation.detection().uncertain()).isTrue();
        assertThat(evaluation.warnings()).noneMatch(w -> w.contains("진영"));
    }

    @Test
    void 정상_입력은_오류와_경고가_없다() {
        Req request = new Req();
        request.topResult = TopResult.LOSS;

        Evaluation evaluation = evaluate(request, firstSet());

        assertThat(evaluation.errors()).isEmpty();
        assertThat(evaluation.warnings()).isEmpty();
        assertThat(evaluation.fingerprint()).isNotNull();
    }
}
