package com.lolinhouse.player;

import com.lolinhouse.domain.player.KnownAlias;
import com.lolinhouse.domain.player.NicknameNormalizer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlayerAliasTest {

    @Test
    void keyIsComparisonKeyAndNicknameKeepsCase() {
        PlayerAlias alias = PlayerAlias.of(1L, "Hide On Bush");

        assertThat(alias.nickname()).isEqualTo("Hide On Bush");
        assertThat(alias.nicknameKey()).isEqualTo("hide on bush");
        assertThat(alias.nicknameKey()).isEqualTo(NicknameNormalizer.comparisonKey("Hide On Bush"));
    }

    @Test
    void differentCaseGivesSameKey() {
        assertThat(PlayerAlias.of(1L, "Faker").nicknameKey()).isEqualTo(PlayerAlias.of(2L, "fAKER").nicknameKey());
    }

    @Test
    void fullWidthLettersGiveSameKeyAsHalfWidth() {
        PlayerAlias fullWidth = PlayerAlias.of(1L, "Ｆａｋｅｒ"); // Ｆａｋｅｒ

        assertThat(fullWidth.nicknameKey()).isEqualTo("faker");
        assertThat(fullWidth.nickname()).isEqualTo("Faker");
    }

    @Test
    void nonBreakingSpaceAtEdgesAndInsideIsNormalized() {
        PlayerAlias alias = PlayerAlias.of(1L, " Hide On  Bush ");

        assertThat(alias.nickname()).isEqualTo("Hide On Bush");
        assertThat(alias.nicknameKey()).isEqualTo("hide on bush");
    }

    @Test
    void koreanNicknameIsKeptAsIs() {
        PlayerAlias alias = PlayerAlias.of(1L, "달빛여우");

        assertThat(alias.nicknameKey()).isEqualTo("달빛여우");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", " 　"})
    void blankAfterNormalizationIsRejected(String raw) {
        assertThatThrownBy(() -> PlayerAlias.of(1L, raw)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nullNicknameOrPlayerIdIsRejected() {
        assertThatThrownBy(() -> PlayerAlias.of(1L, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PlayerAlias.of(null, "x")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fiftyCharactersAreAcceptedAndFiftyOneAreRejected() {
        assertThat(PlayerAlias.of(1L, "a".repeat(50)).nickname()).hasSize(50);
        assertThatThrownBy(() -> PlayerAlias.of(1L, "a".repeat(51))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void lengthIsCountedAfterNormalizationNotOnRawInput() {
        // 원본은 52자(앞뒤 공백 포함)지만 정규화하면 50자
        assertThat(PlayerAlias.of(1L, " " + "a".repeat(50) + " ").nickname()).hasSize(50);
    }

    @Test
    void lengthIsCountedInCodePointsNotUtf16Units() {
        // 𠮷(U+20BB7)는 UTF-16으로 2칸이지만 글자 1개
        assertThat(PlayerAlias.of(1L, "𠮷".repeat(50)).nickname()).isNotEmpty();
        assertThatThrownBy(() -> PlayerAlias.of(1L, "𠮷".repeat(51))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void toKnownAliasUsesStringPlayerIdAndNormalizedNickname() {
        KnownAlias known = PlayerAlias.of(7L, "Faker").toKnownAlias(true);

        assertThat(known).isEqualTo(new KnownAlias("7", "Faker", true));
    }
}
