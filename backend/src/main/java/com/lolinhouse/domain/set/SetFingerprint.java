package com.lolinhouse.domain.set;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.regex.Pattern;

/**
 * 세트 중복 방지용 지문. 결과창의 날짜 + 경기 시간 + 양 팀 킬·골드 합계로 만든다.
 * 같은 경기를 어느 팀 클라이언트에서 캡처해도 같도록 두 팀 값을 정해진 순서로 놓는다.
 * 중복 검사 자체(DB UNIQUE)는 M3 저장 계층이 맡는다.
 * 설명: docs/study/06-domain-set-rules.md, 결정: docs/adr/004-set-fingerprint.md
 */
public record SetFingerprint(String canonical) {

    private static final Pattern DURATION_PATTERN = Pattern.compile("([0-9]{2,3}):([0-9]{2})");
    private static final Comparator<TeamTotals> CANONICAL_ORDER =
            Comparator.comparingInt(TeamTotals::kills).thenComparingInt(TeamTotals::gold);

    /** 한 팀의 킬 합계와 골드 합계(결과창 팀 이름 옆 숫자). */
    public record TeamTotals(int kills, int gold) {

        public TeamTotals {
            if (kills < 0) {
                throw new IllegalArgumentException("팀 킬 합계는 0 이상이어야 합니다.");
            }
            if (gold < 0) {
                throw new IllegalArgumentException("팀 골드 합계는 0 이상이어야 합니다.");
            }
        }
    }

    /** 예: {@code 2026-10-11|1094|8:29846|26:39852} (날짜|초|작은 팀 킬:골드|큰 팀 킬:골드). */
    public static SetFingerprint of(LocalDate date, int durationSeconds, TeamTotals teamA, TeamTotals teamB) {
        if (date == null) {
            throw new IllegalArgumentException("결과창 날짜가 필요합니다.");
        }
        if (durationSeconds <= 0) {
            throw new IllegalArgumentException("경기 시간은 0보다 커야 합니다.");
        }
        if (teamA == null || teamB == null) {
            throw new IllegalArgumentException("양 팀의 킬·골드 합계가 필요합니다.");
        }
        // 위쪽/아래쪽 팀 순서를 지우기 위해 킬 → 골드 순(숫자 비교)으로 작은 쪽을 앞에 둔다
        TeamTotals first = CANONICAL_ORDER.compare(teamA, teamB) <= 0 ? teamA : teamB;
        TeamTotals second = first == teamA ? teamB : teamA;
        return new SetFingerprint(date + "|" + durationSeconds + "|" + format(first) + "|" + format(second));
    }

    /** "18:14" → 1094초. 분은 2~3자리, 초는 00~59. */
    public static int parseDuration(String text) {
        var matcher = DURATION_PATTERN.matcher(text == null ? "" : text);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("경기 시간은 mm:ss 형식이어야 합니다: " + text);
        }
        int minutes = Integer.parseInt(matcher.group(1));
        int seconds = Integer.parseInt(matcher.group(2));
        if (seconds >= 60) {
            throw new IllegalArgumentException("경기 시간의 초는 59 이하여야 합니다(mm:ss): " + text);
        }
        return minutes * 60 + seconds;
    }

    private static String format(TeamTotals totals) {
        return totals.kills() + ":" + totals.gold();
    }
}
