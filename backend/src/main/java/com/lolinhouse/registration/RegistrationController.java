package com.lolinhouse.registration;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.lolinhouse.common.web.ProblemResponses;

/**
 * 역할: 세트 등록 HTTP 입구(담당자 전용): registration-context, draft, sets/check, sets. 요청 파싱·상태 코드만 맡고 규칙은 서비스.
 * 규칙 위반 422 SET_RULES_VIOLATED(+violations, warnings)와 카탈로그 없음 503은 이 컨트롤러가 직접 응답으로 바꾼다.
 * 설명: docs/design/m4-design.md §5
 */
@RestController
@RequestMapping("/api/admin")
public class RegistrationController {

    private final RegistrationContextService contextService;
    private final SetRegistrationService registrationService;

    public RegistrationController(RegistrationContextService contextService,
            SetRegistrationService registrationService) {
        this.contextService = contextService;
        this.registrationService = registrationService;
    }

    @GetMapping("/duels/{duelId}/registration-context")
    RegistrationContextResponse context(@PathVariable Long duelId) {
        return contextService.context(duelId);
    }

    @GetMapping("/extractions/{jobId}/draft")
    DraftResponse draft(@PathVariable Long jobId) {
        return registrationService.draft(jobId);
    }

    @PostMapping("/duels/{duelId}/sets/check")
    Evaluation check(@PathVariable Long duelId, @Valid @RequestBody SetRegistrationRequest request) {
        return registrationService.check(duelId, request);
    }

    @PostMapping("/duels/{duelId}/sets")
    ResponseEntity<SetRegistrationResponse> register(@PathVariable Long duelId,
            @Valid @RequestBody SetRegistrationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(registrationService.register(duelId, request));
    }

    /** 결과창 없이 세트 가져오기(담당자 도구용). 경기 시간 null 허용. ADR 014 */
    @PostMapping("/duels/{duelId}/sets/import")
    ResponseEntity<SetRegistrationResponse> importSet(@PathVariable Long duelId,
            @Valid @RequestBody SetImportRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(registrationService.importSet(duelId, request));
    }

    @ExceptionHandler(SetRulesViolatedException.class)
    ResponseEntity<ProblemDetail> handleRulesViolated(SetRulesViolatedException ex) {
        ProblemDetail problem = ProblemResponses.of(HttpStatus.UNPROCESSABLE_CONTENT, "SET_RULES_VIOLATED",
                "세트가 규칙을 어겨 저장할 수 없습니다.");
        problem.setProperty("violations", ex.violations());
        problem.setProperty("warnings", ex.warnings());
        return problemResponse(HttpStatus.UNPROCESSABLE_CONTENT, problem);
    }

    @ExceptionHandler(ChampionCatalogUnavailableException.class)
    ResponseEntity<ProblemDetail> handleCatalogUnavailable(ChampionCatalogUnavailableException ex) {
        return problemResponse(HttpStatus.SERVICE_UNAVAILABLE, ProblemResponses.of(HttpStatus.SERVICE_UNAVAILABLE,
                "CHAMPION_CATALOG_UNAVAILABLE", "챔피언 목록을 불러올 수 없습니다. 잠시 후 다시 시도하세요."));
    }

    private static ResponseEntity<ProblemDetail> problemResponse(HttpStatus status, ProblemDetail problem) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problem);
    }
}
