package com.lolinhouse.tier;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.lolinhouse.domain.Position;
import com.lolinhouse.domain.tier.LastChange;
import com.lolinhouse.domain.tier.ScoreTable;
import com.lolinhouse.player.Player;
import com.lolinhouse.player.PlayerRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 역할: 공개 티어리스트 조립. 점수표와 라인 티어를 한 읽기 트랜잭션(같은 스냅샷)에서 읽고 점수를 현재 점수표로 계산한다.
 */
@Service
public class TierListService {

    private final PlayerRepository players;
    private final PlayerLaneTierRepository laneTiers;
    private final ScoreSettingRepository scoreSettings;

    public TierListService(PlayerRepository players, PlayerLaneTierRepository laneTiers,
            ScoreSettingRepository scoreSettings) {
        this.players = players;
        this.laneTiers = laneTiers;
        this.scoreSettings = scoreSettings;
    }

    @Transactional(readOnly = true)
    public TierListResponse get() {
        ScoreTable table = scoreSettings.findById(ScoreSetting.SINGLE_ID).orElseThrow().toTable();
        List<Player> registered = players.findAll(Sort.by("id")).stream().filter(Player::isRegistered).toList();
        Map<Long, List<PlayerLaneTier>> lanesByPlayer = registered.isEmpty() ? Map.of()
                : laneTiers.findByPlayerIdIn(registered.stream().map(Player::id).toList()).stream()
                        .collect(Collectors.groupingBy(PlayerLaneTier::playerId));
        List<TierListResponse.TierPlayer> result = registered.stream().map(player -> {
            Map<Position, TierListResponse.LaneView> lanes = new EnumMap<>(Position.class);
            for (Position position : Position.values()) {
                lanes.put(position, null);
            }
            for (PlayerLaneTier lane : lanesByPlayer.getOrDefault(player.id(), List.of())) {
                lanes.put(lane.position(), view(lane, table));
            }
            return new TierListResponse.TierPlayer(player.id(), player.displayName(), player.isFlagged(), lanes);
        }).toList();
        return new TierListResponse(ScoreTableResponse.from(table), result);
    }

    private TierListResponse.LaneView view(PlayerLaneTier lane, ScoreTable table) {
        LastChange change = lane.lastChange();
        TierListResponse.Trend trend = change == null ? null
                : new TierListResponse.Trend(change.direction(), change.from().tier(), change.from().sub());
        return new TierListResponse.LaneView(lane.laneTier().tier(), lane.laneTier().sub(),
                table.points(lane.laneTier(), lane.position()), trend);
    }
}
