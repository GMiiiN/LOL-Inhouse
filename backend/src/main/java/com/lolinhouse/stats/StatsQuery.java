package com.lolinhouse.stats;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

import com.lolinhouse.common.web.ApiException;
import com.lolinhouse.common.web.PublicReadErrorCodes;
import com.lolinhouse.domain.Position;
import com.lolinhouse.domain.stats.RankingKey;

/** 역할: 통계 엔드포인트의 쿼리 문자열 해석. 형식이 틀리면 모두 400 INVALID_QUERY로 통일한다. */
final class StatsQuery {

    static final String ALL = "ALL";

    private StatsQuery() {
    }

    static LocalDate date(String name, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw invalid(name + "은(는) YYYY-MM-DD 형식이어야 합니다.");
        }
    }

    static void requireOrdered(LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw invalid("from은 to보다 늦을 수 없습니다.");
        }
    }

    /** null = ALL(전체 탭). */
    static Position position(String value) {
        if (value == null || value.isBlank() || ALL.equals(value)) {
            return null;
        }
        try {
            return Position.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw invalid("position은 ALL, TOP, JUNGLE, MID, ADC, SUPPORT 중 하나여야 합니다.");
        }
    }

    static RankingKey sort(String value) {
        if (value == null || value.isBlank()) {
            return RankingKey.WIN_RATE;
        }
        try {
            return RankingKey.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw invalid("sort는 WIN_RATE, KDA, SETS 중 하나여야 합니다.");
        }
    }

    private static ApiException invalid(String detail) {
        return ApiException.badRequest(PublicReadErrorCodes.INVALID_QUERY, detail);
    }
}
