package com.lolinhouse.stats;

import java.util.List;

/**
 * 역할: GET /api/duos 응답. 같은 세트 같은 팀이었던 두 선수 중 함께 minSets세트 이상인 쌍의 최고·최악(각각 최대 3쌍).
 * players는 playerId 오름차순이다.
 */
public record DuoResponse(int minSets, List<DuoEntry> best, List<DuoEntry> worst) {

    public record DuoEntry(List<DuoPlayer> players, int sets, int wins, int losses, double winRate) {
    }

    public record DuoPlayer(long playerId, String displayName, boolean registered) {
    }
}
