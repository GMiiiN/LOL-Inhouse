package com.lolinhouse.record.export;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

import com.lolinhouse.common.web.ApiException;
import com.lolinhouse.common.web.PublicReadErrorCodes;
import com.lolinhouse.record.RecordFilter;
import com.lolinhouse.record.RecordFilter.ChampionScope;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 역할: 공개 CSV 내보내기(GET /api/records/export). 필터는 기록 화면과 같은 RecordFilter. */
@RestController
@RequestMapping("/api/records")
public class RecordExportController {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final String CONTENT_TYPE = "text/csv; charset=UTF-8";

    private final RecordExportService service;

    public RecordExportController(RecordExportService service) {
        this.service = service;
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) String from,
            @RequestParam(required = false) String to, @RequestParam(required = false) Long playerId,
            @RequestParam(required = false) String champion, @RequestParam(required = false) String championScope) {
        LocalDate fromDate = parseDate(from);
        LocalDate toDate = parseDate(to);
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw ApiException.badRequest(PublicReadErrorCodes.INVALID_QUERY, "from이 to보다 늦습니다.");
        }
        String championKey = champion == null || champion.isBlank() ? null : champion;
        byte[] body = service.export(new RecordFilter(fromDate, toDate, playerId, championKey, parseScope(championScope)));
        String filename = "lol-inhouse-records-"
                + LocalDate.now(SEOUL).format(DateTimeFormatter.BASIC_ISO_DATE) + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, CONTENT_TYPE)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename).build().toString())
                .body(body);
    }

    private static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw ApiException.badRequest(PublicReadErrorCodes.INVALID_QUERY, "날짜 형식이 올바르지 않습니다.");
        }
    }

    private static ChampionScope parseScope(String value) {
        if (value == null || value.isBlank()) {
            return ChampionScope.ANY;
        }
        try {
            return ChampionScope.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest(PublicReadErrorCodes.INVALID_QUERY, "championScope 값이 올바르지 않습니다.");
        }
    }
}
