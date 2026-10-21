package com.lolinhouse.tier;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.lolinhouse.common.web.ApiException;
import com.lolinhouse.common.web.TierTeamErrorCodes;
import com.lolinhouse.domain.Position;
import com.lolinhouse.domain.tier.LaneTier;
import com.lolinhouse.player.Player;
import com.lolinhouse.player.PlayerAlias;
import com.lolinhouse.player.PlayerAliasRepository;
import com.lolinhouse.player.PlayerCreateRequest;
import com.lolinhouse.player.PlayerRepository;
import com.lolinhouse.player.PlayerResponse.AliasResponse;
import com.lolinhouse.player.PlayerService;
import com.lolinhouse.player.PlayerUpdateRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 역할: 선수 프로필(실명·주력 포지션·닉네임·라인 티어)을 한 트랜잭션으로 저장·조회·삭제한다.
 * 잠금 순서는 score_setting → 선수 행(설계 §11)이고, 어느 단계든 실패하면 별칭·티어 모두 원상태로 롤백된다.
 */
@Service
public class PlayerProfileService {

    /** 새로 만들 때의 닉네임 최대 개수. 수정은 max(이 값, 기존 개수). */
    static final int MAX_NICKNAMES = 10;

    private final PlayerService playerService;
    private final PlayerRepository players;
    private final PlayerAliasRepository aliases;
    private final PlayerLaneTierRepository laneTiers;
    private final ScoreSettingRepository scoreSettings;
    private final JdbcTemplate jdbc;

    public PlayerProfileService(PlayerService playerService, PlayerRepository players, PlayerAliasRepository aliases,
            PlayerLaneTierRepository laneTiers, ScoreSettingRepository scoreSettings, JdbcTemplate jdbc) {
        this.playerService = playerService;
        this.players = players;
        this.aliases = aliases;
        this.laneTiers = laneTiers;
        this.scoreSettings = scoreSettings;
        this.jdbc = jdbc;
    }

    /** 선수 전체(미등록 포함), id 오름차순. */
    @Transactional(readOnly = true)
    public List<PlayerProfileResponse> list() {
        List<Player> all = players.findAll(Sort.by("id"));
        if (all.isEmpty()) {
            return List.of();
        }
        List<Long> ids = all.stream().map(Player::id).toList();
        Map<Long, List<PlayerAlias>> aliasesByPlayer = aliases.findByPlayerIdInOrderByCreatedAtAscIdAsc(ids).stream()
                .collect(Collectors.groupingBy(PlayerAlias::playerId));
        Map<Long, List<PlayerLaneTier>> lanesByPlayer = laneTiers.findByPlayerIdIn(ids).stream()
                .collect(Collectors.groupingBy(PlayerLaneTier::playerId));
        return all.stream().map(p -> toResponse(p, aliasesByPlayer.getOrDefault(p.id(), List.of()),
                lanesByPlayer.getOrDefault(p.id(), List.of()))).toList();
    }

    @Transactional
    public PlayerProfileResponse create(PlayerProfileRequest request) {
        scoreSettings.findForUpdate();
        Map<Position, LaneTier> wanted = parseLanes(request);
        if (wanted.isEmpty()) {
            throw ApiException.badRequest(TierTeamErrorCodes.LANE_TIER_REQUIRED, "최소 한 라인은 티어를 골라야 합니다.");
        }
        List<String> nicknames = requireNicknames(request);
        if (nicknames.size() > MAX_NICKNAMES) {
            throw ApiException.badRequest("INVALID_NICKNAME", "닉네임은 최대 " + MAX_NICKNAMES + "개까지 둘 수 있습니다.");
        }
        Long id = playerService.create(new PlayerCreateRequest(request.realName(), request.mainPosition(), nicknames))
                .id();
        if (Boolean.TRUE.equals(request.flagged())) {
            players.findById(id).orElseThrow().changeFlagged(true);
        }
        applyLanes(id, wanted, request.shouldRecordChange());
        return load(id);
    }

    @Transactional
    public PlayerProfileResponse update(Long id, PlayerProfileRequest request) {
        scoreSettings.findForUpdate();
        Map<Position, LaneTier> wanted = parseLanes(request);
        List<String> nicknames = requireNicknames(request);
        playerService.update(id, new PlayerUpdateRequest(request.realName(), request.mainPosition()));
        if (request.flagged() != null) {
            players.findById(id).orElseThrow().changeFlagged(request.flagged());
        }
        int maxCount = Math.max(MAX_NICKNAMES, (int) aliases.countByPlayerId(id));
        playerService.replaceAliases(id, nicknames, maxCount);
        applyLanes(id, wanted, request.shouldRecordChange());
        return load(id);
    }

    /** 세트 기록(game_set_player)이 하나라도 있으면 409. 없으면 별칭·라인 티어·선수를 지운다. */
    @Transactional
    public void delete(Long id) {
        scoreSettings.findForUpdate();
        Player player = players.findByIdForUpdate(id)
                .orElseThrow(() -> ApiException.notFound("PLAYER_NOT_FOUND", "선수를 찾을 수 없습니다: " + id));
        Integer records = jdbc.queryForObject("select count(*) from game_set_player where player_id = ?",
                Integer.class, id);
        if (records != null && records > 0) {
            throw ApiException.conflict(TierTeamErrorCodes.PLAYER_HAS_RECORDS, "세트 기록이 있는 선수는 삭제할 수 없습니다.");
        }
        aliases.deleteAll(aliases.findByPlayerIdOrderByCreatedAtAscIdAsc(id));
        aliases.flush();
        laneTiers.deleteAll(laneTiers.findByPlayerId(id));
        laneTiers.flush();
        players.delete(player);
        players.flush();
    }

    /** 요청의 라인을 검사해 티어가 있는 라인만 모은다. 키가 없거나 값이 null이면 티어 없음. */
    private Map<Position, LaneTier> parseLanes(PlayerProfileRequest request) {
        Map<Position, LaneTier> wanted = new EnumMap<>(Position.class);
        if (request.lanes() == null) {
            return wanted;
        }
        request.lanes().forEach((position, input) -> {
            if (position == null) {
                throw invalidLane("알 수 없는 라인입니다.");
            }
            if (input == null) {
                return;
            }
            if (input.tier() == null || input.sub() == null || input.tier() < LaneTier.MIN_TIER
                    || input.tier() > LaneTier.MAX_TIER) {
                throw invalidLane(position + " 티어는 " + LaneTier.MIN_TIER + "~" + LaneTier.MAX_TIER
                        + "와 상·중·하가 필요합니다.");
            }
            wanted.put(position, new LaneTier(input.tier(), input.sub()));
        });
        return wanted;
    }

    private List<String> requireNicknames(PlayerProfileRequest request) {
        if (request.nicknames() == null || request.nicknames().isEmpty()) {
            throw ApiException.badRequest(TierTeamErrorCodes.NICKNAME_REQUIRED, "닉네임은 1개 이상 필요합니다.");
        }
        return request.nicknames();
    }

    /** 라인마다 추가·수정·삭제. recordChange=false면 값만 바꾸고 ▲▼는 건드리지 않는다. */
    private void applyLanes(Long playerId, Map<Position, LaneTier> wanted, boolean recordChange) {
        Map<Position, PlayerLaneTier> current = laneTiers.findByPlayerId(playerId).stream()
                .collect(Collectors.toMap(PlayerLaneTier::position, l -> l));
        for (Position position : Position.values()) {
            PlayerLaneTier existing = current.get(position);
            LaneTier next = wanted.get(position);
            if (next == null) {
                if (existing != null) {
                    laneTiers.delete(existing);
                }
            } else if (existing == null) {
                laneTiers.save(PlayerLaneTier.create(playerId, position, next));
            } else if (recordChange) {
                existing.changeTo(next);
            } else {
                existing.overwrite(next);
            }
        }
        laneTiers.flush();
    }

    private PlayerProfileResponse load(Long id) {
        Player player = players.findById(id).orElseThrow();
        return toResponse(player, aliases.findByPlayerIdOrderByCreatedAtAscIdAsc(id), laneTiers.findByPlayerId(id));
    }

    private PlayerProfileResponse toResponse(Player player, List<PlayerAlias> playerAliases,
            List<PlayerLaneTier> lanes) {
        Map<Position, PlayerProfileResponse.LaneValue> byLane = new EnumMap<>(Position.class);
        for (Position position : Position.values()) {
            byLane.put(position, null);
        }
        for (PlayerLaneTier lane : lanes) {
            byLane.put(lane.position(),
                    new PlayerProfileResponse.LaneValue(lane.laneTier().tier(), lane.laneTier().sub()));
        }
        return new PlayerProfileResponse(player.id(), player.displayName(), player.realName(), player.isRegistered(),
                player.isFlagged(), player.mainPosition(),
                playerAliases.stream().map(a -> new AliasResponse(a.id(), a.nickname())).toList(), byLane);
    }

    private ApiException invalidLane(String detail) {
        return ApiException.badRequest(TierTeamErrorCodes.INVALID_LANE_TIER, detail);
    }
}
