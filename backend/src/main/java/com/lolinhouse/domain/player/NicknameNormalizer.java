package com.lolinhouse.domain.player;

import java.text.Normalizer;
import java.util.Locale;

/**
 * 역할: 닉네임 비교 전에 표기 차이를 없앤다.
 * normalize = 화면·저장용 표기(NFKC, 가장자리 공백 제거, 내부 공백 하나로, 대소문자 유지).
 * comparisonKey = 비교용 키(normalize + 소문자). 대소문자는 구분하지 않기로 했다(사용자 결정 2026-10-16).
 * M3의 "정규화 닉네임 UNIQUE 키"도 comparisonKey와 같은 규칙으로 만들어야 한다.
 */
public final class NicknameNormalizer {

    private NicknameNormalizer() {
    }

    public static String normalize(String nickname) {
        String composed = Normalizer.normalize(nickname, Normalizer.Form.NFKC);
        // (?U): \s가 전각 공백(U+3000) 같은 유니코드 공백도 잡도록 한다.
        return stripUnicodeSpaces(composed.replaceAll("(?U)\\s+", " "));
    }

    /** 비교·중복 검사용 키. Locale.ROOT: 실행 PC의 언어(예: 터키어)에 따라 결과가 달라지지 않게 한다. */
    public static String comparisonKey(String nickname) {
        return normalize(nickname).toLowerCase(Locale.ROOT);
    }

    /**
     * 양 끝의 유니코드 공백(NBSP U+00A0, 좁은 NBSP U+202F, 전각 공백 U+3000 포함)만 제거한다.
     * String.strip()은 NBSP·U+202F 같은 "줄바꿈 없는 공백"을 지우지 않는다.
     */
    public static String stripUnicodeSpaces(String text) {
        return text.replaceAll("(?U)^[\\s\\p{Z}]+|[\\s\\p{Z}]+$", "");
    }
}
