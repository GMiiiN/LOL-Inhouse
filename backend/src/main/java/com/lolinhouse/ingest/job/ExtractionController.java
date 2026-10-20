package com.lolinhouse.ingest.job;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.lolinhouse.common.web.ApiException;
import com.lolinhouse.domain.Position;
import com.lolinhouse.domain.Side;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 역할: 추출 작업 HTTP 입구(담당자 전용). multipart 파싱·진영/포지션 값 검사·상태 코드(새 작업 202 + Location, 재사용 200)만 맡고 규칙은 ExtractionService.
 * 오류 응답(400 IMAGE_REQUIRED, 413 IMAGE_TOO_LARGE 등)은 ApiExceptionHandler가 만든다.
 * 설명: docs/design/m4-design.md §5
 */
@RestController
@RequestMapping("/api/admin")
class ExtractionController {

    private final ExtractionService service;

    ExtractionController(ExtractionService service) {
        this.service = service;
    }

    @PostMapping(path = "/duels/{duelId}/extractions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<ExtractionStartResponse> start(@PathVariable Long duelId,
            @RequestPart("image") MultipartFile image, @RequestParam(required = false) String topSide,
            @RequestParam(required = false) String capturerPosition,
            @RequestParam(defaultValue = "false") boolean retry) {
        List<Map<String, String>> errors = new ArrayList<>();
        Side side = parse(Side.class, "topSide", topSide, errors);
        Position position = parse(Position.class, "capturerPosition", capturerPosition, errors);
        if (!errors.isEmpty()) {
            throw ApiException.badRequest("VALIDATION_FAILED", "요청 값이 올바르지 않습니다.").with("errors", errors);
        }
        byte[] bytes;
        try {
            bytes = image.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("업로드 파일을 읽지 못했습니다.", e);
        }
        ExtractionService.StartResult result = service.start(duelId, bytes, side, position, retry);
        ExtractionStartResponse body = new ExtractionStartResponse(result.jobId(), result.status(), result.reused());
        URI location = URI.create("/api/admin/extractions/" + result.jobId());
        return ResponseEntity.status(result.reused() ? HttpStatus.OK : HttpStatus.ACCEPTED).location(location)
                .body(body);
    }

    @GetMapping("/extractions/{jobId}")
    ExtractionJobResponse status(@PathVariable Long jobId) {
        return service.status(jobId);
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String field, String value,
            List<Map<String, String>> errors) {
        if (value != null) {
            try {
                return Enum.valueOf(type, value);
            } catch (IllegalArgumentException e) {
                // 아래에서 오류로 기록한다.
            }
        }
        errors.add(Map.of("field", field, "message", "허용된 값이어야 합니다."));
        return null;
    }
}
