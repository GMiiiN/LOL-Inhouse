package com.lolinhouse.domain.player;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NicknameNormalizerTest {

    @Test
    void fullWidthLettersBecomeHalfWidth() {
        assertThat(NicknameNormalizer.normalize("ＡＢＣ１２３")).isEqualTo("ABC123");
    }

    @Test
    void halfWidthKatakanaIsComposed() {
        assertThat(NicknameNormalizer.normalize("ｶﾞｲ")).isEqualTo("ガイ");
    }

    @Test
    void trimsAndCollapsesInnerWhitespace() {
        assertThat(NicknameNormalizer.normalize("  Moon   light \t Fox ")).isEqualTo("Moon light Fox");
    }

    @Test
    void ideographicSpaceCountsAsWhitespace() {
        assertThat(NicknameNormalizer.normalize("月光　の狐")).isEqualTo("月光 の狐");
    }

    @Test
    void displayNormalizationKeepsCaseAndHangulAsIs() {
        assertThat(NicknameNormalizer.normalize("달빛Fox")).isEqualTo("달빛Fox");
    }

    @Test
    void comparisonKeyIsNormalizedAndLowercased() {
        assertThat(NicknameNormalizer.comparisonKey("  Ｆaker  PRO ")).isEqualTo("faker pro");
        assertThat(NicknameNormalizer.comparisonKey("달빛Fox")).isEqualTo("달빛fox");
    }

    @Test
    void comparisonKeyLowercasingIsLocaleIndependent() {
        java.util.Locale previous = java.util.Locale.getDefault();
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("tr-TR"));
            // 터키어 로케일에서 "I".toLowerCase()는 "ı"(점 없는 i)가 된다. Locale.ROOT를 써야 항상 "i".
            assertThat(NicknameNormalizer.comparisonKey("TITAN")).isEqualTo("titan");
        } finally {
            java.util.Locale.setDefault(previous);
        }
    }

    @Test
    void comparisonKeyIgnoresNonBreakingSpaceAtEdges() {
        assertThat(NicknameNormalizer.comparisonKey(" Faker ")).isEqualTo("faker");
    }

    @Test
    void stripUnicodeSpacesRemovesNbspNarrowNbspAndIdeographicSpaceFromBothEdgesOnly() {
        assertThat(NicknameNormalizer.stripUnicodeSpaces(" 　AB C…  ")).isEqualTo("AB C…");
    }
}
