package com.lolinhouse.tier;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 역할: 미등록 선수를 기존 선수에게 합치는 HTTP 입구(담당자 전용). 규칙은 PlayerProfileService.merge. */
@RestController
@RequestMapping("/api/admin/players")
public class PlayerMergeController {

    private final PlayerProfileService profileService;

    public PlayerMergeController(PlayerProfileService profileService) {
        this.profileService = profileService;
    }

    @PostMapping("/{sourceId}/merge")
    PlayerProfileResponse merge(@PathVariable Long sourceId, @Valid @RequestBody PlayerMergeRequest request) {
        return profileService.merge(sourceId, request.targetId());
    }
}
