package com.lolinhouse.domain.player;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LevenshteinTest {

    @Test
    void identicalStringsHaveZeroDistance() {
        assertThat(Levenshtein.distance("abc", "abc")).isZero();
    }

    @Test
    void emptyAgainstTextIsTextLength() {
        assertThat(Levenshtein.distance("", "abcd")).isEqualTo(4);
        assertThat(Levenshtein.distance("abcd", "")).isEqualTo(4);
    }

    @Test
    void classicKittenSittingIsThree() {
        assertThat(Levenshtein.distance("kitten", "sitting")).isEqualTo(3);
    }

    @Test
    void singleSubstitutionInHangulIsOne() {
        assertThat(Levenshtein.distance("달빛여우", "달빛여오")).isEqualTo(1);
    }

    @Test
    void supplementaryCharacterCountsAsOneCharacter() {
        // 𠮷(U+20BB7)는 char 2개지만 글자 1개다. 한 글자 오인식은 거리 1이어야 한다.
        assertThat(Levenshtein.distance("𠮷野", "吉野")).isEqualTo(1);
    }

    @Test
    void similarityIsOneMinusDistanceOverLongerLength() {
        assertThat(Levenshtein.similarity("abcd", "abce")).isEqualTo(0.75);
        assertThat(Levenshtein.similarity("abc", "abcdef")).isEqualTo(0.5);
    }

    @Test
    void similarityOfTwoEmptyStringsIsOne() {
        assertThat(Levenshtein.similarity("", "")).isEqualTo(1.0);
    }

    @Test
    void similarityOfCompletelyDifferentStringsIsZero() {
        assertThat(Levenshtein.similarity("abc", "xyz")).isZero();
    }
}
