package com.lolinhouse.domain.set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lolinhouse.domain.DuelTeam;
import com.lolinhouse.domain.Position;
import com.lolinhouse.domain.Side;
import com.lolinhouse.domain.set.SetDraft.DraftPlayer;
import com.lolinhouse.domain.set.SetDraft.DraftTeam;
import com.lolinhouse.domain.set.SetDraftValidator.ValidationContext;
import com.lolinhouse.domain.set.SetDraftValidator.ValidationResult;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SetDraftValidatorTest {

    // 샘플 1 = 대결 1세트, 위쪽 팀 블루 = 1팀. 제한 챔피언 없음
    private static final ValidationContext FIRST_SET = new ValidationContext(1, DuelTeam.TEAM1, Set.of());

    @Test
    void 샘플1_초안은_오류도_경고도_없다() {
        ValidationResult result = SetDraftValidator.validate(SetDraftFixtures.sample1(), FIRST_SET);

        assertThat(result.errors()).isEmpty();
        assertThat(result.warnings()).isEmpty();
        assertThat(result.canSave()).isTrue();
    }

    @Test
    void 샘플3_초안은_오류도_경고도_없다() {
        // 샘플 3의 위쪽 팀은 레드. 세트 번호 3에서 레드는 2팀이므로 그렇게 맞춘 문맥
        ValidationContext thirdSet = new ValidationContext(3, DuelTeam.TEAM2, Set.of());

        ValidationResult result = SetDraftValidator.validate(SetDraftFixtures.sample3(), thirdSet);

        assertThat(result.errors()).isEmpty();
        assertThat(result.warnings()).isEmpty();
    }

    // ----- 오류(저장 차단) -----

    @Test
    void 같은_선수가_두_번_나오면_오류다() {
        SetDraft draft = SetDraftFixtures.sample1();
        DraftTeam bottom = replacePlayerId(draft.bottomTeam(), 0, "p1");

        ValidationResult result = SetDraftValidator.validate(withBottom(draft, bottom), FIRST_SET);

        assertThat(result.errors()).anyMatch(message -> message.contains("선수") && message.contains("10명"));
        assertThat(result.canSave()).isFalse();
    }

    @Test
    void 선수_id가_비어_있으면_오류다() {
        SetDraft draft = SetDraftFixtures.sample1();
        DraftTeam top = replacePlayerId(draft.topTeam(), 2, " ");

        ValidationResult result = SetDraftValidator.validate(withTop(draft, top), FIRST_SET);

        assertThat(result.errors()).anyMatch(message -> message.contains("10명"));
    }

    @Test
    void 팀에_같은_포지션이_두_명이면_오류다() {
        SetDraft draft = SetDraftFixtures.sample1();
        DraftTeam bottom = replacePosition(draft.bottomTeam(), 1, Position.TOP);

        ValidationResult result = SetDraftValidator.validate(withBottom(draft, bottom), FIRST_SET);

        assertThat(result.errors()).anyMatch(message -> message.contains("아래쪽 팀") && message.contains("포지션"));
        assertThat(result.errors()).noneMatch(message -> message.contains("위쪽 팀"));
    }

    @Test
    void 팀_인원이_5명이_아니면_포지션_오류다() {
        SetDraft draft = SetDraftFixtures.sample1();
        List<DraftPlayer> fourPlayers = new ArrayList<>(draft.topTeam().players());
        fourPlayers.remove(4);
        DraftTeam top = new DraftTeam(fourPlayers, 26, 8, 24, 39852);

        ValidationResult result = SetDraftValidator.validate(withTop(draft, top), FIRST_SET);

        assertThat(result.errors()).anyMatch(message -> message.contains("위쪽 팀") && message.contains("포지션"));
        assertThat(result.errors()).anyMatch(message -> message.contains("10명"));
    }

    @Test
    void 밴이_10개가_아니면_오류다() {
        SetDraft draft = SetDraftFixtures.sample1();
        SetDraft nineBans = new SetDraft(draft.topSide(), draft.capturerPosition(),
                draft.topTeam(), draft.bottomTeam(), SetDraftFixtures.bans().subList(0, 9));

        ValidationResult result = SetDraftValidator.validate(nineBans, FIRST_SET);

        assertThat(result.errors()).anyMatch(message -> message.contains("밴 10개"));
    }

    @Test
    void 밴에_빈_칸이_있으면_오류다() {
        SetDraft draft = SetDraftFixtures.sample1();
        List<String> bansWithBlank = Arrays.asList("ban1", "ban2", null, "ban4", "ban5",
                "ban6", "ban7", "", "ban9", "ban10");
        SetDraft withBlank = new SetDraft(draft.topSide(), draft.capturerPosition(),
                draft.topTeam(), draft.bottomTeam(), bansWithBlank);

        ValidationResult result = SetDraftValidator.validate(withBlank, FIRST_SET);

        assertThat(result.errors()).anyMatch(message -> message.contains("밴 10개"));
    }

    @Test
    void 위쪽_팀_진영이_없으면_오류다() {
        SetDraft draft = SetDraftFixtures.sample1();
        SetDraft noSide = new SetDraft(null, draft.capturerPosition(),
                draft.topTeam(), draft.bottomTeam(), draft.bans());

        ValidationResult result = SetDraftValidator.validate(noSide, FIRST_SET);

        assertThat(result.errors()).anyMatch(message -> message.contains("진영"));
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    void 캡처한_사람의_포지션이_없으면_오류다() {
        SetDraft draft = SetDraftFixtures.sample1();
        SetDraft noCapturer = new SetDraft(draft.topSide(), null,
                draft.topTeam(), draft.bottomTeam(), draft.bans());

        ValidationResult result = SetDraftValidator.validate(noCapturer, FIRST_SET);

        assertThat(result.errors()).anyMatch(message -> message.contains("캡처한 사람"));
    }

    @Test
    void 오류는_한꺼번에_모두_알려준다() {
        SetDraft draft = SetDraftFixtures.sample1();
        SetDraft broken = new SetDraft(null, null, draft.topTeam(), draft.bottomTeam(), List.of());

        ValidationResult result = SetDraftValidator.validate(broken, FIRST_SET);

        assertThat(result.errors()).hasSize(3);
    }

    // ----- 경고(저장은 막지 않음) -----

    @Test
    void 팀_합계가_행_합과_다르면_경고만_한다() {
        SetDraft draft = SetDraftFixtures.sample1();
        DraftTeam top = new DraftTeam(draft.topTeam().players(), 26, 8, 25, 39852);

        ValidationResult result = SetDraftValidator.validate(withTop(draft, top), FIRST_SET);

        assertThat(result.errors()).isEmpty();
        assertThat(result.canSave()).isTrue();
        assertThat(result.warnings()).hasSize(1);
        assertThat(result.warnings().get(0)).contains("위쪽 팀", "K/D/A");
    }

    @Test
    void 제한_챔피언을_픽했으면_경고한다() {
        // 샘플 1의 픽 top3, bottom2 가 이전 세트에서 이미 쓰였다고 가정
        ValidationContext context =
                new ValidationContext(1, DuelTeam.TEAM1, Set.of("top3", "bottom2", "unrelated"));

        ValidationResult result = SetDraftValidator.validate(SetDraftFixtures.sample1(), context);

        assertThat(result.errors()).isEmpty();
        assertThat(result.warnings()).hasSize(1);
        assertThat(result.warnings().get(0)).contains("피어리스", "top3", "bottom2");
    }

    @Test
    void 진영이_규칙과_다르면_경고한다() {
        // 2세트에서 1팀은 레드가 규칙인데 샘플 1 초안은 위쪽 팀 블루
        ValidationContext secondSet = new ValidationContext(2, DuelTeam.TEAM1, Set.of());

        ValidationResult result = SetDraftValidator.validate(SetDraftFixtures.sample1(), secondSet);

        assertThat(result.errors()).isEmpty();
        assertThat(result.warnings()).hasSize(1);
        assertThat(result.warnings().get(0)).contains("1팀", "레드");
    }

    @Test
    void 오류와_경고는_함께_나올_수_있다() {
        SetDraft draft = SetDraftFixtures.sample1();
        SetDraft noCapturer = new SetDraft(Side.BLUE, null, draft.topTeam(), draft.bottomTeam(), draft.bans());
        ValidationContext secondSet = new ValidationContext(2, DuelTeam.TEAM1, Set.of());

        ValidationResult result = SetDraftValidator.validate(noCapturer, secondSet);

        assertThat(result.errors()).hasSize(1);
        assertThat(result.warnings()).hasSize(1);
        assertThat(result.canSave()).isFalse();
    }

    // ----- Codex 리뷰 반영: 픽 챔피언 필수 -----

    @Test
    void 픽_챔피언이_null이면_오류다() {
        SetDraft draft = SetDraftFixtures.sample1();
        DraftTeam top = replaceChampion(draft.topTeam(), 0, null);

        ValidationResult result = SetDraftValidator.validate(withTop(draft, top), FIRST_SET);

        assertThat(result.errors()).anyMatch(message -> message.contains("픽 챔피언"));
        assertThat(result.canSave()).isFalse();
    }

    @Test
    void 픽_챔피언이_공백이면_오류다() {
        SetDraft draft = SetDraftFixtures.sample1();
        DraftTeam bottom = replaceChampion(draft.bottomTeam(), 4, "  ");

        ValidationResult result = SetDraftValidator.validate(withBottom(draft, bottom), FIRST_SET);

        assertThat(result.errors()).anyMatch(message -> message.contains("픽 챔피언"));
        assertThat(result.canSave()).isFalse();
    }

    // ----- Codex 리뷰 반영: 골드 합계 경고 -----

    @Test
    void 팀_골드_합계가_행_합과_다르면_경고만_한다() {
        SetDraft draft = SetDraftFixtures.sample1();
        DraftTeam top = new DraftTeam(draft.topTeam().players(), 26, 8, 24, 39853);

        ValidationResult result = SetDraftValidator.validate(withTop(draft, top), FIRST_SET);

        assertThat(result.errors()).isEmpty();
        assertThat(result.warnings()).hasSize(1);
        assertThat(result.warnings().get(0)).contains("위쪽 팀", "골드", "39852", "39853");
    }

    // ----- Codex 리뷰 반영: null 선수 행 -----

    @Test
    void 선수_행이_null이어도_예외_없이_오류로_알려준다() {
        SetDraft draft = SetDraftFixtures.sample1();
        List<DraftPlayer> withNullRow = new ArrayList<>(draft.topTeam().players());
        withNullRow.set(3, null);
        DraftTeam top = new DraftTeam(withNullRow, 26, 8, 24, 39852);
        ValidationContext context = new ValidationContext(1, DuelTeam.TEAM1, Set.of("top1"));

        ValidationResult result = SetDraftValidator.validate(withTop(draft, top), context);

        assertThat(result.errors()).anyMatch(message -> message.contains("비어 있는 선수 행"));
        assertThat(result.canSave()).isFalse();
        assertThat(result.warnings()).anyMatch(message -> message.contains("피어리스") && message.contains("top1"));
    }

    // ----- Codex 리뷰 반영: int 오버플로 -----

    @Test
    void 합계가_int_범위를_넘어도_표시값과_우연히_같다고_보지_않는다() {
        SetDraft draft = SetDraftFixtures.sample1();
        List<DraftPlayer> players = new ArrayList<>(draft.topTeam().players());
        DraftPlayer first = players.get(0);
        DraftPlayer second = players.get(1);
        players.set(0, new DraftPlayer(first.playerId(), first.position(), first.championKey(),
                Integer.MAX_VALUE, 0, 0, 0));
        players.set(1, new DraftPlayer(second.playerId(), second.position(), second.championKey(),
                2, 0, 0, 0));
        // 진짜 합 2147483649 가 int 로 넘치면 -2147483647 이 되어 표시값과 같아져 버린다
        int wrappedKills = Integer.MAX_VALUE + 2;
        DraftTeam top = new DraftTeam(players, wrappedKills, 0, 0, 0);

        ValidationResult result = SetDraftValidator.validate(withTop(draft, top), FIRST_SET);

        assertThat(result.warnings()).anyMatch(message -> message.contains("위쪽 팀") && message.contains("K/D/A"));
    }

    // ----- Codex 리뷰 반영: 불변성 -----

    @Test
    void 만든_뒤_입력_목록을_바꿔도_초안은_변하지_않는다() {
        List<String> bans = new ArrayList<>(SetDraftFixtures.bans());
        List<DraftPlayer> players = new ArrayList<>(SetDraftFixtures.sample1().topTeam().players());
        DraftTeam team = new DraftTeam(players, 26, 8, 24, 39852);
        SetDraft sample = SetDraftFixtures.sample1();
        SetDraft draft = new SetDraft(Side.BLUE, Position.ADC, team, sample.bottomTeam(), bans);

        bans.clear();
        players.clear();

        assertThat(draft.bans()).hasSize(10);
        assertThat(draft.topTeam().players()).hasSize(5);
    }

    @Test
    void 초안의_목록_접근자는_수정할_수_없다() {
        SetDraft draft = SetDraftFixtures.sample1();

        assertThatThrownBy(() -> draft.bans().add("x")).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> draft.topTeam().players().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void 초안은_null_원소를_가진_목록도_받아들인다() {
        List<DraftPlayer> withNull = Arrays.asList(null, null);
        List<String> bansWithNull = Arrays.asList("ban1", null);

        DraftTeam team = new DraftTeam(withNull, 0, 0, 0, 0);
        SetDraft draft = new SetDraft(Side.BLUE, Position.ADC, team, team, bansWithNull);

        assertThat(team.players()).containsExactly(null, null);
        assertThat(draft.bans()).containsExactly("ban1", null);
    }

    @Test
    void 검증_결과의_목록은_수정할_수_없고_입력_목록과_분리된다() {
        List<String> errors = new ArrayList<>(List.of("e"));
        ValidationResult result = new ValidationResult(errors, new ArrayList<>());

        errors.add("changed");

        assertThat(result.errors()).containsExactly("e");
        assertThatThrownBy(() -> result.errors().add("x")).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> result.warnings().add("x")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void 검증_문맥의_제한_챔피언은_입력_집합과_분리된다() {
        Set<String> restricted = new java.util.HashSet<>(Set.of("a"));
        ValidationContext context = new ValidationContext(1, DuelTeam.TEAM1, restricted);

        restricted.add("b");

        assertThat(context.restrictedChampions()).containsExactly("a");
    }

    // ----- 테스트 도우미 -----

    private static SetDraft withTop(SetDraft draft, DraftTeam top) {
        return new SetDraft(draft.topSide(), draft.capturerPosition(), top, draft.bottomTeam(), draft.bans());
    }

    private static SetDraft withBottom(SetDraft draft, DraftTeam bottom) {
        return new SetDraft(draft.topSide(), draft.capturerPosition(), draft.topTeam(), bottom, draft.bans());
    }

    private static DraftTeam replacePlayerId(DraftTeam team, int index, String playerId) {
        List<DraftPlayer> players = new ArrayList<>(team.players());
        DraftPlayer old = players.get(index);
        players.set(index, new DraftPlayer(playerId, old.position(), old.championKey(),
                old.kills(), old.deaths(), old.assists(), old.gold()));
        return new DraftTeam(players, team.displayedKills(), team.displayedDeaths(), team.displayedAssists(), team.displayedGold());
    }

    private static DraftTeam replacePosition(DraftTeam team, int index, Position position) {
        List<DraftPlayer> players = new ArrayList<>(team.players());
        DraftPlayer old = players.get(index);
        players.set(index, new DraftPlayer(old.playerId(), position, old.championKey(),
                old.kills(), old.deaths(), old.assists(), old.gold()));
        return new DraftTeam(players, team.displayedKills(), team.displayedDeaths(), team.displayedAssists(), team.displayedGold());
    }

    private static DraftTeam replaceChampion(DraftTeam team, int index, String championKey) {
        List<DraftPlayer> players = new ArrayList<>(team.players());
        DraftPlayer old = players.get(index);
        players.set(index, new DraftPlayer(old.playerId(), old.position(), championKey,
                old.kills(), old.deaths(), old.assists(), old.gold()));
        return new DraftTeam(players, team.displayedKills(), team.displayedDeaths(), team.displayedAssists(),
                team.displayedGold());
    }
}
