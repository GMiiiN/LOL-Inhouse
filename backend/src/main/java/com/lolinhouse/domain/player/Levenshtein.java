package com.lolinhouse.domain.player;

/**
 * 역할: 두 문자열의 편집 거리(Levenshtein)와 0~1 유사도를 계산한다.
 * 닉네임 OCR 오인식(한 글자 치환)을 흡수하는 매칭의 바탕이다.
 * 글자는 char가 아니라 코드포인트 단위로 센다(보조 평면 한자도 1글자).
 */
public final class Levenshtein {

    private Levenshtein() {
    }

    /** a를 b로 바꾸는 데 필요한 최소 삽입·삭제·치환 횟수. */
    public static int distance(String a, String b) {
        int[] left = a.codePoints().toArray();
        int[] right = b.codePoints().toArray();

        // previous[j] = left의 앞 i-1글자를 right의 앞 j글자로 바꾸는 비용 (표의 윗줄)
        int[] previous = new int[right.length + 1];
        int[] current = new int[right.length + 1];
        for (int j = 0; j <= right.length; j++) {
            previous[j] = j;
        }

        for (int i = 1; i <= left.length; i++) {
            current[0] = i;
            for (int j = 1; j <= right.length; j++) {
                int substitution = previous[j - 1] + (left[i - 1] == right[j - 1] ? 0 : 1);
                int deletion = previous[j] + 1;
                int insertion = current[j - 1] + 1;
                current[j] = Math.min(substitution, Math.min(deletion, insertion));
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[right.length];
    }

    /** (긴 길이 - 거리) / 긴 길이. 둘 다 빈 문자열이면 1.0. */
    public static double similarity(String a, String b) {
        int longer = Math.max(a.codePointCount(0, a.length()), b.codePointCount(0, b.length()));
        if (longer == 0) {
            return 1.0;
        }
        // 1 - d/n 대신 (n - d)/n: 0.70 경계에서 부동소수점 오차가 끼어들지 않게 한 번만 나눈다.
        return (longer - distance(a, b)) / (double) longer;
    }
}
