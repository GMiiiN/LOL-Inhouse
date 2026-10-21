package com.lolinhouse.domain.team;

import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

import com.lolinhouse.domain.Position;
import com.lolinhouse.domain.tier.LaneTier;
import com.lolinhouse.domain.tier.ScoreTable;

/**
 * 역할: 참가자 x 5라인 점수표. 프런트가 교체 후 점수를 다시 계산할 때 찾아 쓴다. 티어 없는 칸은 대체 점수(6티어 하)이고 hasTier = false다.
 */
public record ScoreMatrix(Map<Long, Map<Position, Cell>> cells) {

    /** 한 칸. tier가 null이면 티어 없음. */
    public record Cell(int score, LaneTier tier) {

        public boolean hasTier() {
            return tier != null;
        }
    }

    public static ScoreMatrix of(Collection<TeamMakerPlayer> players, ScoreTable table) {
        Map<Long, Map<Position, Cell>> cells = new LinkedHashMap<>();
        for (TeamMakerPlayer player : players) {
            Map<Position, Cell> row = new EnumMap<>(Position.class);
            for (Position position : Position.values()) {
                LaneTier tier = player.tierOf(position);
                row.put(position, tier == null ? new Cell(table.fallbackPoints(position), null)
                        : new Cell(table.points(tier, position), tier));
            }
            cells.put(player.id(), row);
        }
        return new ScoreMatrix(cells);
    }
}
