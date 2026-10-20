package com.lolinhouse.registration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.lolinhouse.champion.IconMatch;
import com.lolinhouse.domain.Position;
import com.lolinhouse.domain.Side;
import com.lolinhouse.domain.player.KnownAlias;
import com.lolinhouse.domain.player.MatchType;
import com.lolinhouse.ingest.ExtractionResultFixture;
import com.lolinhouse.ingest.job.ExtractionJob;
import com.lolinhouse.ingest.job.ExtractionResult;
import org.junit.jupiter.api.Test;

import static com.lolinhouse.registration.RegistrationFixtures.catalog;
import static com.lolinhouse.registration.RegistrationFixtures.champion;
import static com.lolinhouse.registration.RegistrationFixtures.firstSet;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 역할: DraftService(추출 사실 + 대결 맥락 → 초안) 검증 — 캡처한 사람 포지션별 배정, 닉네임 매칭 결과별 기본값
 * (EXACT/AUTO는 EXISTING·addAlias=false, 후보·잘린 새 닉네임은 UNRESOLVED, 잘리지 않은 새 닉네임은 NEW), 빈 닉네임 예외 처리,
 * 챔피언·밴 1순위 미리 채움, uncertain 표시. DB 없이 돈다.
 */
class DraftServiceTest {

    private final DraftService draftService = new DraftService(new SetEvaluator(), 1.0);

    private static ExtractionJob job(Position capturer) {
        return ExtractionJob.queued(7L, Side.BLUE, capturer, "a".repeat(64), "aa/key.png");
    }

    /** 합성 결과창 기준 결과에서 위쪽 팀 닉네임 5개와 아래쪽 첫 줄 닉네임만 바꾼다. */
    private static ExtractionResult withNicknames(List<String> topNicknames, String bottomFirstNickname) {
        ExtractionResult base = ExtractionResultFixture.sample();
        List<ExtractionResult.Row> top = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            top.add(withNickname(base.top().rows().get(i), topNicknames.get(i)));
        }
        List<ExtractionResult.Row> bottom = new ArrayList<>(base.bottom().rows());
        bottom.set(0, withNickname(bottom.get(0), bottomFirstNickname));
        return new ExtractionResult(base.schemaVersion(), base.model(), base.dataDragonVersion(), base.attempts(),
                base.topResult(), base.shotDate(), base.duration(),
                new ExtractionResult.Team(base.top().kills(), base.top().deaths(), base.top().assists(),
                        base.top().gold(), top, base.top().bans()),
                new ExtractionResult.Team(base.bottom().kills(), base.bottom().deaths(), base.bottom().assists(),
                        base.bottom().gold(), bottom, base.bottom().bans()));
    }

    private static ExtractionResult.Row withNickname(ExtractionResult.Row row, String nickname) {
        return new ExtractionResult.Row(nickname, row.kills(), row.deaths(), row.assists(), row.cs(), row.gold(),
                row.portraitBox(), row.champion());
    }

    private static PlayerDirectory directory() {
        Map<Long, String> names = new HashMap<>();
        for (long id = 1; id <= 5; id++) {
            names.put(id, "실명" + id);
        }
        List<KnownAlias> aliases = List.of(
                new KnownAlias("1", "Alpha01", true),
                new KnownAlias("2", "BetaBeta1", true),
                new KnownAlias("3", "Cand_abd", true),
                new KnownAlias("4", "Cand_abe", true));
        Map<String, Long> owners = new HashMap<>();
        aliases.forEach(alias -> owners.put(alias.nickname().toLowerCase(), Long.valueOf(alias.playerId())));
        return new PlayerDirectory(names, owners, aliases);
    }

    private DraftResponse build(Position capturer, ExtractionResult result, double margin) {
        return new DraftService(new SetEvaluator(), margin).build(job(capturer), result, firstSet(), directory(),
                catalog(), fingerprint -> Optional.empty());
    }

    private DraftResponse mixedDraft() {
        ExtractionResult result = withNicknames(
                List.of("Alpha01", "BetaBeta2", "Cand_abc", "Brandnew", "긴닉네임…"), "…");
        return draftService.build(job(Position.ADC), result, firstSet(), directory(), catalog(),
                fingerprint -> Optional.empty());
    }

    // ----- 포지션 배정 -----

    @Test
    void 캡처한_사람이_원딜이면_위쪽은_원딜_탑_정글_미드_서폿이고_아래쪽은_탑부터다() {
        DraftResponse draft = build(Position.ADC, ExtractionResultFixture.sample(), 1.0);

        assertThat(draft.top().rows()).extracting(DraftResponse.Row::position)
                .containsExactly(Position.ADC, Position.TOP, Position.JUNGLE, Position.MID, Position.SUPPORT);
        assertThat(draft.bottom().rows()).extracting(DraftResponse.Row::position)
                .containsExactly(Position.TOP, Position.JUNGLE, Position.MID, Position.ADC, Position.SUPPORT);
        assertThat(draft.top().rows()).extracting(DraftResponse.Row::screenRow).containsExactly(1, 2, 3, 4, 5);
    }

    @Test
    void 캡처한_사람이_미드이면_위쪽_첫_줄이_미드다() {
        DraftResponse draft = build(Position.MID, ExtractionResultFixture.sample(), 1.0);

        assertThat(draft.top().rows()).extracting(DraftResponse.Row::position)
                .containsExactly(Position.MID, Position.TOP, Position.JUNGLE, Position.ADC, Position.SUPPORT);
        assertThat(draft.defaults().capturerPosition()).isEqualTo(Position.MID);
    }

    // ----- 닉네임 매칭 기본값 -----

    @Test
    void 매칭_결과별_기본값은_EXACT와_자동연결은_EXISTING_후보와_잘림은_UNRESOLVED_새_닉네임은_NEW다() {
        DraftResponse draft = mixedDraft();
        List<RowInput> rows = draft.defaults().top().rows();

        assertThat(draft.top().rows()).extracting(row -> row.match().type()).containsExactly(MatchType.EXACT,
                MatchType.ONE_CHAR_AUTO, MatchType.CANDIDATES, MatchType.NEW_UNREGISTERED,
                MatchType.NEW_UNREGISTERED);

        assertThat(rows.get(0).resolution()).isEqualTo(RowResolution.EXISTING);
        assertThat(rows.get(0).playerId()).isEqualTo(1L);
        assertThat(rows.get(1).resolution()).isEqualTo(RowResolution.EXISTING);
        assertThat(rows.get(1).playerId()).isEqualTo(2L);
        assertThat(rows.get(2).resolution()).isEqualTo(RowResolution.UNRESOLVED);
        assertThat(rows.get(2).playerId()).isNull();
        assertThat(rows.get(3).resolution()).isEqualTo(RowResolution.NEW);
        assertThat(rows.get(3).nickname()).isEqualTo("Brandnew");
        assertThat(rows.get(4).resolution()).isEqualTo(RowResolution.UNRESOLVED);
        assertThat(rows).allMatch(row -> !row.addAlias());
    }

    @Test
    void 후보와_표시_이름과_잘림_여부를_응답에_담는다() {
        DraftResponse draft = mixedDraft();

        DraftResponse.Match exact = draft.top().rows().get(0).match();
        assertThat(exact.playerId()).isEqualTo(1L);
        assertThat(exact.playerDisplayName()).isEqualTo("실명1");

        DraftResponse.Match candidates = draft.top().rows().get(2).match();
        assertThat(candidates.candidates()).extracting(DraftResponse.Candidate::playerId)
                .containsExactlyInAnyOrder(3L, 4L);
        assertThat(candidates.candidates()).allMatch(candidate -> candidate.displayName() == null
                || candidate.displayName().startsWith("실명"));

        assertThat(draft.top().rows().get(3).truncated()).isFalse();
        assertThat(draft.top().rows().get(4).truncated()).isTrue();
        assertThat(draft.top().rows().get(4).match().fullNicknameRequired()).isTrue();
        assertThat(draft.top().rows().get(4).readNickname()).isEqualTo("긴닉네임…");
    }

    @Test
    void 줄임표만_있는_닉네임은_예외_없이_전체_닉네임_입력이_필요한_미확정이다() {
        DraftResponse draft = mixedDraft();

        DraftResponse.Row row = draft.bottom().rows().get(0);
        assertThat(row.truncated()).isTrue();
        assertThat(row.match().type()).isEqualTo(MatchType.NEW_UNREGISTERED);
        assertThat(row.match().fullNicknameRequired()).isTrue();
        assertThat(draft.defaults().bottom().rows().get(0).resolution()).isEqualTo(RowResolution.UNRESOLVED);
        assertThat(draft.defaults().bottom().rows().get(0).nickname()).isEqualTo("…");
    }

    @Test
    void 미확정_행이_있으면_초안_평가가_저장_오류를_낸다() {
        DraftResponse draft = mixedDraft();

        assertThat(draft.evaluation().errors()).anyMatch(error -> error.contains("골라야"));
    }

    // ----- 챔피언·밴·uncertain -----

    @Test
    void 챔피언과_밴은_1순위로_미리_채우고_화면_순서를_유지한다() {
        DraftResponse draft = build(Position.ADC, ExtractionResultFixture.sample(), 1.0);

        assertThat(draft.defaults().top().rows()).extracting(RowInput::championKey)
                .containsExactly(champion(0), champion(1), champion(2), champion(3), champion(4));
        assertThat(draft.defaults().bottom().rows()).extracting(RowInput::championKey)
                .containsExactly(champion(5), champion(6), champion(7), champion(8), champion(9));
        assertThat(draft.defaults().top().bans())
                .containsExactly(champion(10), champion(11), champion(12), champion(13), champion(14));
        assertThat(draft.defaults().bottom().bans())
                .containsExactly(champion(15), champion(16), champion(17), champion(18), champion(19));
        assertThat(draft.top().bans()).extracting(DraftResponse.Ban::slot).containsExactly(1, 2, 3, 4, 5);
        assertThat(draft.top().rows().get(0).champion().candidates()).hasSize(3);
    }

    @Test
    void uncertain은_margin이_기준보다_작을_때_켜진다() {
        ExtractionResult sample = ExtractionResultFixture.sample(); // margin 0.4

        assertThat(build(Position.ADC, sample, 1.0).top().rows().get(0).champion().uncertain()).isTrue();
        assertThat(build(Position.ADC, sample, 0.1).top().rows().get(0).champion().uncertain()).isFalse();
    }

    @Test
    void 잘라내기_실패_칸은_후보가_비고_null로_채워지며_uncertain이다() {
        ExtractionResult base = ExtractionResultFixture.sample();
        List<ExtractionResult.Ban> bans = new ArrayList<>(base.top().bans());
        bans.set(0, new ExtractionResult.Ban(bans.get(0).box(), IconMatch.failedCrop()));
        ExtractionResult result = new ExtractionResult(base.schemaVersion(), base.model(), base.dataDragonVersion(),
                base.attempts(), base.topResult(), base.shotDate(), base.duration(),
                new ExtractionResult.Team(base.top().kills(), base.top().deaths(), base.top().assists(),
                        base.top().gold(), base.top().rows(), bans),
                base.bottom());

        DraftResponse draft = build(Position.ADC, result, 0.1);

        assertThat(draft.defaults().top().bans().get(0)).isNull();
        assertThat(draft.top().bans().get(0).champion().cropFailed()).isTrue();
        assertThat(draft.top().bans().get(0).champion().uncertain()).isTrue();
        assertThat(draft.evaluation().errors()).anyMatch(error -> error.contains("밴 10개"));
    }

    @Test
    void 경기_정보와_작업_선택값을_그대로_돌려준다() {
        DraftResponse draft = build(Position.ADC, ExtractionResultFixture.sample(), 1.0);

        assertThat(draft.topSide()).isEqualTo(Side.BLUE);
        assertThat(draft.duelId()).isEqualTo(7L);
        assertThat(draft.shotDate()).isEqualTo("2026-10-12");
        assertThat(draft.duration()).isEqualTo("25:42");
        assertThat(draft.defaults().shotDate()).isEqualTo("2026-10-12");
        assertThat(draft.top().displayedKills()).isEqualTo(draft.defaults().top().displayedKills());
    }
}
