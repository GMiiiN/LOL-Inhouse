package com.lolinhouse.record;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

import com.lolinhouse.common.web.ApiException;
import com.lolinhouse.common.web.PublicReadErrorCodes;
import com.lolinhouse.record.RecordFilter.ChampionScope;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 역할: 공개 기록 조회 HTTP 입구(로그인 불필요, 인가는 SecurityConfig). 쿼리 값은 문자열로 받아 직접 해석해
 * 형식 오류(날짜·숫자·championScope)와 from > to, limit 범위 밖을 모두 400 INVALID_QUERY로 통일한다.
 */
@RestController
public class RecordController {

    static final int DEFAULT_LIMIT = 10;
    static final int MAX_LIMIT = 20;

    private final RecordService service;

    public RecordController(RecordService service) {
        this.service = service;
    }

    @GetMapping("/api/records")
    RecordPageResponse records(@RequestParam(required = false) String from, @RequestParam(required = false) String to,
            @RequestParam(required = false) String playerId, @RequestParam(required = false) String champion,
            @RequestParam(required = false) String championScope, @RequestParam(required = false) String before,
            @RequestParam(required = false) String limit) {
        LocalDate fromDate = date("from", from);
        LocalDate toDate = date("to", to);
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw invalid("from은 to보다 늦을 수 없습니다.");
        }
        int limitValue = blank(limit) ? DEFAULT_LIMIT : integer("limit", limit);
        if (limitValue < 1 || limitValue > MAX_LIMIT) {
            throw invalid("limit은 1~" + MAX_LIMIT + " 범위여야 합니다.");
        }
        RecordFilter filter = new RecordFilter(fromDate, toDate, blank(playerId) ? null : longValue("playerId", playerId),
                blank(champion) ? null : champion, scope(championScope));
        return service.page(filter, date("before", before), limitValue);
    }

    @GetMapping("/api/duels/{id}")
    DuelDetailResponse duel(@PathVariable long id) {
        return service.duel(id);
    }

    @GetMapping("/api/sets/{id}")
    SetView set(@PathVariable long id) {
        return service.set(id);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static LocalDate date(String name, String value) {
        if (blank(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw invalid(name + "은 YYYY-MM-DD 형식이어야 합니다.");
        }
    }

    private static int integer(String name, String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw invalid(name + "은 정수여야 합니다.");
        }
    }

    private static Long longValue(String name, String value) {
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            throw invalid(name + "은 정수여야 합니다.");
        }
    }

    private static ChampionScope scope(String value) {
        if (blank(value)) {
            return ChampionScope.ANY;
        }
        try {
            return ChampionScope.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw invalid("championScope는 ANY 또는 PLAYER_PICK이어야 합니다.");
        }
    }

    private static ApiException invalid(String detail) {
        return ApiException.badRequest(PublicReadErrorCodes.INVALID_QUERY, detail);
    }
}
