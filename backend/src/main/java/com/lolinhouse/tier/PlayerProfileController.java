package com.lolinhouse.tier;

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
 * 역할: 선수 프로필 관리 HTTP 입구(담당자 전용, 인가는 SecurityConfig). 규칙은 PlayerProfileService.
 */
@RestController
@RequestMapping("/api/admin/player-profiles")
public class PlayerProfileController {

    private final PlayerProfileService profileService;

    public PlayerProfileController(PlayerProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping
    List<PlayerProfileResponse> list() {
        return profileService.list();
    }

    @PostMapping
    ResponseEntity<PlayerProfileResponse> create(@Valid @RequestBody PlayerProfileRequest request) {
        PlayerProfileResponse created = profileService.create(request);
        return ResponseEntity.created(URI.create("/api/admin/player-profiles/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    PlayerProfileResponse update(@PathVariable Long id, @Valid @RequestBody PlayerProfileRequest request) {
        return profileService.update(id, request);
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@PathVariable Long id) {
        profileService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
