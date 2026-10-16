package com.lolinhouse.domain.player;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NicknameMatcherTest {

    private final NicknameMatcher matcher = new NicknameMatcher();

    private static KnownAlias alias(String playerId, String nickname) {
        return new KnownAlias(playerId, nickname, true);
    }

    // ---------- 1. EXACT ----------

    @Test
    void exactMatchLinksPlayer() {
        var result = matcher.match("Moonlight_Fox", List.of(alias("p1", "Sky_Walker"), alias("p2", "Moonlight_Fox")));

        assertThat(result.type()).isEqualTo(MatchType.EXACT);
        assertThat(result.playerId()).isEqualTo("p2");
        assertThat(result.similarity()).isEqualTo(1.0);
    }

    @Test
    void exactMatchIgnoresFullWidthAndExtraSpaces() {
        var result = matcher.match("  Ｍoonlight   Ｆox ", List.of(alias("p1", "Moonlight Fox")));

        assertThat(result.type()).isEqualTo(MatchType.EXACT);
        assertThat(result.playerId()).isEqualTo("p1");
    }

    @Test
    void exactMatchWorksForJapaneseHalfWidthKatakana() {
        var result = matcher.match("ｶﾞｲ月光", List.of(alias("p1", "ガイ月光")));

        assertThat(result.type()).isEqualTo(MatchType.EXACT);
    }

    @Test
    void exactMatchAlsoWorksForUnregisteredPlayersAlias() {
        var result = matcher.match("Sky_Walker", List.of(new KnownAlias("p7", "Sky_Walker", false)));

        assertThat(result.type()).isEqualTo(MatchType.EXACT);
        assertThat(result.playerId()).isEqualTo("p7");
    }

    // ---------- 2. TRUNCATED_AUTO ----------

    @Test
    void truncatedNicknameWithNinetyTwoPercentPrefixAutoLinks() {
        // 보이는 앞부분 "Moonlight_Fax"(13자)는 "Moonlight_Fox"와 한 글자 차이 -> 12/13 = 0.923
        var result = matcher.match("Moonlight_Fax…", List.of(alias("p1", "Moonlight_Fox_Kim"), alias("p2", "Sky_Walker99")));

        assertThat(result.type()).isEqualTo(MatchType.TRUNCATED_AUTO);
        assertThat(result.playerId()).isEqualTo("p1");
        assertThat(result.similarity()).isBetween(0.92, 0.93);
    }

    @Test
    void truncatedExactPrefixIsPerfectSimilarity() {
        var result = matcher.match("Moonlight…", List.of(alias("p1", "Moonlight_Fox_Kim")));

        assertThat(result.type()).isEqualTo(MatchType.TRUNCATED_AUTO);
        assertThat(result.similarity()).isEqualTo(1.0);
    }

    @Test
    void truncatedSimilarityExactlySeventyPercentAutoLinks() {
        // 앞 10글자 중 3글자 다름 -> 7/10 = 0.70 (경계값 포함)
        var result = matcher.match("ABCDEFGxxx…", List.of(alias("p1", "ABCDEFGHIJKLMN")));

        assertThat(result.type()).isEqualTo(MatchType.TRUNCATED_AUTO);
        assertThat(result.similarity()).isEqualTo(0.7);
    }

    @Test
    void truncatedBelowSeventyPercentIsOnlyCandidate() {
        // 13글자 중 4글자 다름 -> 9/13 = 0.692
        var result = matcher.match("ABCDEFGHIxxxx…", List.of(alias("p1", "ABCDEFGHIJKLMNOP")));

        assertThat(result.type()).isEqualTo(MatchType.CANDIDATES);
        assertThat(result.candidates()).extracting(NicknameMatchResult.Candidate::playerId).containsExactly("p1");
    }

    @Test
    void truncatedTieBetweenTwoPlayersIsNotAutoLinked() {
        var result = matcher.match("Moonlight…", List.of(alias("p1", "Moonlight_Fox"), alias("p2", "Moonlight_Cat")));

        assertThat(result.type()).isEqualTo(MatchType.CANDIDATES);
        assertThat(result.candidates()).extracting(NicknameMatchResult.Candidate::playerId)
                .containsExactlyInAnyOrder("p1", "p2");
    }

    @Test
    void truncatedTwoAliasesOfSamePlayerStillAutoLinks() {
        var result = matcher.match("Moonlight…", List.of(alias("p1", "Moonlight_Fox"), alias("p1", "Moonlight_Main")));

        assertThat(result.type()).isEqualTo(MatchType.TRUNCATED_AUTO);
        assertThat(result.playerId()).isEqualTo("p1");
    }

    @Test
    void truncatedNicknameWithNoSimilarAliasAsksForFullNickname() {
        var result = matcher.match("Zzzzzzzz…", List.of(alias("p1", "Moonlight_Fox")));

        assertThat(result.type()).isEqualTo(MatchType.NEW_UNREGISTERED);
        assertThat(result.fullNicknameRequired()).isTrue();
        assertThat(result.newPlayerName()).isNull();
    }

    @Test
    void truncatedAliasShorterThanVisiblePrefixUsesWholeAlias() {
        // 별칭이 보이는 앞부분보다 짧으면 별칭 전체와 비교: "Fox" vs "Fox_Kim" -> 1 - 4/7 = 0.43
        var result = matcher.match("Fox_Kim…", List.of(alias("p1", "Fox")));

        assertThat(result.type()).isEqualTo(MatchType.NEW_UNREGISTERED);
    }

    // ---------- 3. CANDIDATES ----------

    @Test
    void oneCharacterOcrErrorInFullNicknameIsCandidateNotAutoLink() {
        var result = matcher.match("Moonlight_Fax", List.of(alias("p1", "Moonlight_Fox")));

        assertThat(result.type()).isEqualTo(MatchType.CANDIDATES);
        assertThat(result.playerId()).isNull();
        assertThat(result.candidates()).hasSize(1);
        assertThat(result.candidates().get(0).playerId()).isEqualTo("p1");
        assertThat(result.candidates().get(0).similarity()).isBetween(0.92, 0.93);
    }

    @Test
    void oneCharacterErrorInHangulNicknameIsCandidate() {
        var result = matcher.match("달빛여오", List.of(alias("p1", "달빛여우")));

        assertThat(result.type()).isEqualTo(MatchType.CANDIDATES);
        assertThat(result.candidates().get(0).similarity()).isEqualTo(0.75);
    }

    @Test
    void candidatesAreLimitedToThreeBestFirst() {
        var result = matcher.match("ABCDEFGH", List.of(
                alias("p1", "ABCDEFGX"),   // 7/8 = 0.875
                alias("p2", "ABCDXXXX"),   // 4/8 = 0.5
                alias("p3", "ABCDEFXX"),   // 6/8 = 0.75
                alias("p4", "ABCDEXXX"),   // 5/8 = 0.625
                alias("p5", "ZZZZZZZZ")));  // 0

        assertThat(result.type()).isEqualTo(MatchType.CANDIDATES);
        assertThat(result.candidates()).extracting(NicknameMatchResult.Candidate::playerId)
                .containsExactly("p1", "p3", "p4");
    }

    @Test
    void candidateThresholdIsInclusiveAtHalf() {
        var result = matcher.match("ABCDEFGH", List.of(alias("p2", "ABCDXXXX")));

        assertThat(result.type()).isEqualTo(MatchType.CANDIDATES);
    }

    @Test
    void candidatesHaveOneEntryPerPlayerWithBestAlias() {
        var result = matcher.match("ABCDEFGH", List.of(
                alias("p1", "ABCDXXXX"), alias("p1", "ABCDEFGX"), alias("p2", "ABCDEFXX")));

        assertThat(result.candidates()).extracting(NicknameMatchResult.Candidate::playerId).containsExactly("p1", "p2");
        assertThat(result.candidates().get(0).nickname()).isEqualTo("ABCDEFGX");
    }

    // ---------- 4. NEW_UNREGISTERED ----------

    @Test
    void unknownNicknameCreatesUnregisteredPlayerWithNicknameAsDisplayName() {
        var result = matcher.match("  Brand   New ", List.of(alias("p1", "Moonlight_Fox")));

        assertThat(result.type()).isEqualTo(MatchType.NEW_UNREGISTERED);
        assertThat(result.fullNicknameRequired()).isFalse();
        assertThat(result.newPlayerName()).isEqualTo("Brand New");
        assertThat(result.candidates()).isEmpty();
    }

    @Test
    void emptyAliasListCreatesNewPlayer() {
        var result = matcher.match("Brand New", List.of());

        assertThat(result.type()).isEqualTo(MatchType.NEW_UNREGISTERED);
    }

    @Test
    void blankNicknameIsRejected() {
        assertThatThrownBy(() -> matcher.match("   ", List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ellipsisOnlyIsRejected() {
        assertThatThrownBy(() -> matcher.match("…", List.of(alias("p1", "Moonlight_Fox"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---------- Codex 리뷰 회귀 테스트 ----------

    @Test
    void truncatedIsNotAutoLinkedWhenSecondPlayerIsAlsoAboveThreshold() {
        // p1 = 1.0, p2 = 0.8 (둘 다 0.70 이상) -> 1등이 더 높아도 유일하지 않으므로 사람이 고른다.
        var result = matcher.match("ABCDEFGHIJ…", List.of(alias("p1", "ABCDEFGHIJ_K"), alias("p2", "ABCDEFGHXX_K")));

        assertThat(result.type()).isEqualTo(MatchType.CANDIDATES);
        assertThat(result.candidates()).extracting(NicknameMatchResult.Candidate::playerId).containsExactly("p1", "p2");
    }

    @Test
    void truncatedIsAutoLinkedWhenOnlyOnePlayerIsAboveThreshold() {
        // p2 = 0.6 (0.70 미만, 후보 수준) -> p1만 0.70 이상이라 자동 연결
        var result = matcher.match("ABCDEFGHIJ…", List.of(alias("p1", "ABCDEFGHIJ_K"), alias("p2", "ABCDEFXXXX_K")));

        assertThat(result.type()).isEqualTo(MatchType.TRUNCATED_AUTO);
        assertThat(result.playerId()).isEqualTo("p1");
    }

    @Test
    void resultDoesNotChangeWhenInputListIsMutatedLater() {
        List<NicknameMatchResult.Candidate> source = new ArrayList<>(
                List.of(new NicknameMatchResult.Candidate("p1", "Moonlight_Fox", 0.9)));
        var result = new NicknameMatchResult(MatchType.CANDIDATES, null, 0.0, source, false, null);

        source.clear();

        assertThat(result.candidates()).hasSize(1);
    }

    @Test
    void candidatesListOfResultCannotBeModified() {
        var result = matcher.match("Moonlight_Fax", List.of(alias("p1", "Moonlight_Fox")));

        assertThatThrownBy(() -> result.candidates().add(new NicknameMatchResult.Candidate("p9", "x", 1.0)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void nonBreakingSpaceAfterEllipsisStillDetectsTruncation() {
        var result = matcher.match("ABCDEFGHIJ… ", List.of(alias("p1", "ABCDEFGHIJ_K")));

        assertThat(result.type()).isEqualTo(MatchType.TRUNCATED_AUTO);
    }

    @Test
    void narrowNonBreakingSpaceAndIdeographicSpaceAroundEllipsisStillDetectTruncation() {
        assertThat(matcher.match("ABCDEFGHIJ… ", List.of(alias("p1", "ABCDEFGHIJ_K"))).type())
                .isEqualTo(MatchType.TRUNCATED_AUTO);
        assertThat(matcher.match("　ABCDEFGHIJ…　", List.of(alias("p1", "ABCDEFGHIJ_K"))).type())
                .isEqualTo(MatchType.TRUNCATED_AUTO);
    }

    @Test
    void truncatedInputKeepsFullNicknameRequiredOnCandidatesResult() {
        // 9/13 = 0.692: 자동 연결은 아니지만 후보를 모두 거부하고 새 선수를 만들면 전체 닉네임이 필요하다.
        var result = matcher.match("ABCDEFGHIxxxx…", List.of(alias("p1", "ABCDEFGHIJKLMNOP")));

        assertThat(result.type()).isEqualTo(MatchType.CANDIDATES);
        assertThat(result.fullNicknameRequired()).isTrue();
    }

    @Test
    void fullNicknameRequiredFollowsTruncationOnEveryResultType() {
        assertThat(matcher.match("Moonlight…", List.of(alias("p1", "Moonlight_Fox"))).fullNicknameRequired()).isTrue();
        assertThat(matcher.match("Moonlight_Fax", List.of(alias("p1", "Moonlight_Fox"))).fullNicknameRequired()).isFalse();
        assertThat(matcher.match("Moonlight_Fox", List.of(alias("p1", "Moonlight_Fox"))).fullNicknameRequired()).isFalse();
        assertThat(matcher.match("Brand New", List.of()).fullNicknameRequired()).isFalse();
    }

    @Test
    void sameNormalizedAliasOnTwoPlayersReturnsAllAsCandidates() {
        var result = matcher.match("ABC", List.of(alias("p1", "ＡBC"), alias("p2", "ABC"), alias("p3", "XYZ")));

        assertThat(result.type()).isEqualTo(MatchType.CANDIDATES);
        assertThat(result.playerId()).isNull();
        assertThat(result.candidates()).extracting(NicknameMatchResult.Candidate::playerId).containsExactly("p1", "p2");
        assertThat(result.candidates()).extracting(NicknameMatchResult.Candidate::similarity).containsOnly(1.0);
    }

    // ---------- 대소문자 무시 (사용자 결정 2026-10-16) ----------

    @Test
    void exactMatchIgnoresCase() {
        var result = matcher.match("Faker", List.of(alias("p1", "faker")));

        assertThat(result.type()).isEqualTo(MatchType.EXACT);
        assertThat(result.playerId()).isEqualTo("p1");
    }

    @Test
    void truncatedPrefixComparisonIgnoresCase() {
        var result = matcher.match("MOONLIGHT…", List.of(alias("p1", "moonlight_fox")));

        assertThat(result.type()).isEqualTo(MatchType.TRUNCATED_AUTO);
        assertThat(result.similarity()).isEqualTo(1.0);
    }

    @Test
    void caseOnlyDifferenceDoesNotCountAsEditInSimilarity() {
        // 대소문자를 무시하므로 "Moonlight_FAX" vs "moonlight_fox"는 한 글자 차이(12/13)
        var result = matcher.match("Moonlight_FAX", List.of(alias("p1", "moonlight_fox")));

        assertThat(result.type()).isEqualTo(MatchType.CANDIDATES);
        assertThat(result.candidates().get(0).similarity()).isBetween(0.92, 0.93);
    }

    @Test
    void newPlayerKeepsOriginalCaseForDisplayName() {
        var result = matcher.match("  Brand   NEW ", List.of(alias("p1", "Moonlight_Fox")));

        assertThat(result.newPlayerName()).isEqualTo("Brand NEW");
    }

    @Test
    void candidateKeepsOriginalAliasSpelling() {
        var result = matcher.match("moonlight_fax", List.of(alias("p1", "Moonlight_Fox")));

        assertThat(result.candidates().get(0).nickname()).isEqualTo("Moonlight_Fox");
    }

    @Test
    void aliasesDifferingOnlyByCaseOnTwoPlayersAreAmbiguous() {
        var result = matcher.match("faker", List.of(alias("p1", "Faker"), alias("p2", "faker")));

        assertThat(result.type()).isEqualTo(MatchType.CANDIDATES);
        assertThat(result.candidates()).extracting(NicknameMatchResult.Candidate::playerId).containsExactly("p1", "p2");
    }

    @Test
    void sameNormalizedAliasTwiceOnOnePlayerIsStillExact() {
        var result = matcher.match("ABC", List.of(alias("p1", "ＡBC"), alias("p1", "ABC")));

        assertThat(result.type()).isEqualTo(MatchType.EXACT);
        assertThat(result.playerId()).isEqualTo("p1");
    }
}
