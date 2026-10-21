package com.lolinhouse.tier;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 역할: 점수표 수정·기본값 복원 HTTP 입구(담당자 전용, 인가는 SecurityConfig).
 */
@RestController
@RequestMapping("/api/admin/score-table")
public class ScoreSettingAdminController {

    private final ScoreSettingService scoreSettingService;

    public ScoreSettingAdminController(ScoreSettingService scoreSettingService) {
        this.scoreSettingService = scoreSettingService;
    }

    @PutMapping
    ScoreTableResponse update(@RequestBody ScoreTableRequest request) {
        return scoreSettingService.update(request);
    }

    @PostMapping("/reset")
    ScoreTableResponse reset() {
        return scoreSettingService.reset();
    }
}
