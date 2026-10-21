package com.lolinhouse.stats;

import java.util.List;

/**
 * 역할: GET /api/champion-stats 응답. 정렬은 프런트가 한다.
 * 밴픽률 = setsWithPickOrBan ÷ totalSets (같은 세트에서 픽과 밴이 겹치면 1로 센다).
 */
public record ChampionStatsResponse(int totalSets, List<Item> champions) {

    public record Item(String championKey, int picks, int bans, int wins, int losses, int setsWithPickOrBan) {
    }
}
