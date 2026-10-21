package com.lolinhouse.tier;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 역할: 공개 조회 HTTP 입구(티어리스트·점수표). 로그인 없이 열리는 경로는 SecurityConfig가 정한다.
 */
@RestController
@RequestMapping("/api")
public class TierListController {

    private final TierListService tierListService;
    private final ScoreSettingService scoreSettingService;

    public TierListController(TierListService tierListService, ScoreSettingService scoreSettingService) {
        this.tierListService = tierListService;
        this.scoreSettingService = scoreSettingService;
    }

    @GetMapping("/tier-list")
    TierListResponse tierList() {
        return tierListService.get();
    }

    @GetMapping("/score-table")
    ScoreTableResponse scoreTable() {
        return scoreSettingService.get();
    }
}
