package com.lolinhouse.session;

import java.net.URI;
import java.util.List;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 역할: 내전 묶음·대결 HTTP 입구(담당자 전용). 요청 파싱·상태 코드·Location만 맡고 규칙은 InhouseSessionService.
 */
@RestController
@RequestMapping("/api/admin")
public class InhouseSessionController {

    private final InhouseSessionService sessionService;

    public InhouseSessionController(InhouseSessionService sessionService) {
        this.sessionService = sessionService;
    }

    @PostMapping("/inhouse-sessions")
    ResponseEntity<InhouseSessionSummaryResponse> create(@Valid @RequestBody InhouseSessionCreateRequest request) {
        InhouseSessionSummaryResponse created = sessionService.create(request.startDate());
        return ResponseEntity.created(URI.create("/api/admin/inhouse-sessions/" + created.id())).body(created);
    }

    @GetMapping("/inhouse-sessions")
    List<InhouseSessionSummaryResponse> list() {
        return sessionService.list();
    }

    @GetMapping("/inhouse-sessions/{id}")
    InhouseSessionDetailResponse get(@PathVariable Long id) {
        return sessionService.get(id);
    }

    @PostMapping("/inhouse-sessions/{sessionId}/duels")
    ResponseEntity<DuelResponse> createDuel(@PathVariable Long sessionId) {
        DuelResponse created = sessionService.createDuel(sessionId);
        return ResponseEntity.created(URI.create("/api/admin/duels/" + created.id())).body(created);
    }

    @GetMapping("/duels/{id}")
    DuelResponse getDuel(@PathVariable Long id) {
        return sessionService.getDuel(id);
    }
}
