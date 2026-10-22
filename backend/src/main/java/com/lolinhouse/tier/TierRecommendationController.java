package com.lolinhouse.tier;

import java.util.List;

import com.lolinhouse.domain.tier.TierRecommender.Recommendation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 역할: 티어 조정 추천 HTTP 입구(담당자 전용, 인가는 SecurityConfig의 /api/admin/**). */
@RestController
@RequestMapping("/api/admin/tier-recommendations")
public class TierRecommendationController {

    private final TierRecommendationService service;

    public TierRecommendationController(TierRecommendationService service) {
        this.service = service;
    }

    @GetMapping
    List<Recommendation> list() {
        return service.recommend();
    }
}
