package com.lolinhouse.player;

import java.net.URI;
import java.util.List;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 역할: 선수·별칭 관리 HTTP 입구(담당자 전용, /api/admin/** 인가는 SecurityConfig). 요청 파싱·상태 코드만 맡고 규칙은 PlayerService.
 */
@RestController
@RequestMapping("/api/admin/players")
public class PlayerController {

    private final PlayerService playerService;

    public PlayerController(PlayerService playerService) {
        this.playerService = playerService;
    }

    @GetMapping
    List<PlayerResponse> list() {
        return playerService.list();
    }

    @GetMapping("/{id}")
    PlayerResponse get(@PathVariable Long id) {
        return playerService.get(id);
    }

    @PostMapping
    ResponseEntity<PlayerResponse> create(@Valid @RequestBody PlayerCreateRequest request) {
        PlayerResponse created = playerService.create(request);
        return ResponseEntity.created(URI.create("/api/admin/players/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    PlayerResponse update(@PathVariable Long id, @Valid @RequestBody PlayerUpdateRequest request) {
        return playerService.update(id, request);
    }

    @PostMapping("/{id}/aliases")
    ResponseEntity<PlayerResponse> addAlias(@PathVariable Long id, @Valid @RequestBody AliasCreateRequest request) {
        PlayerResponse updated = playerService.addAliasAndGet(id, request.nickname());
        return ResponseEntity.created(URI.create("/api/admin/players/" + id)).body(updated);
    }

    @DeleteMapping("/{id}/aliases/{aliasId}")
    ResponseEntity<Void> deleteAlias(@PathVariable Long id, @PathVariable Long aliasId) {
        playerService.deleteAlias(id, aliasId);
        return ResponseEntity.noContent().build();
    }
}
