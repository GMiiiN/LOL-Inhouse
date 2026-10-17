package com.lolinhouse.player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.lolinhouse.common.web.ApiException;
import com.lolinhouse.domain.player.NicknameNormalizer;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 역할: 선수·별칭 관리 규칙(등록, 실명 변경, 별칭 추가·삭제, 충돌 → 409, 대표 닉네임 갱신, 마지막 별칭 보호).
 * OSIV가 꺼져 있어 엔티티 → DTO 변환도 여기서 한다. {@link #addAlias}는 M4(세트 저장)도 같은 규칙으로 호출한다.
 */
@Service
public class PlayerService {

    private final PlayerRepository playerRepository;
    private final PlayerAliasRepository aliasRepository;

    public PlayerService(PlayerRepository playerRepository, PlayerAliasRepository aliasRepository) {
        this.playerRepository = playerRepository;
        this.aliasRepository = aliasRepository;
    }

    @Transactional(readOnly = true)
    public List<PlayerResponse> list() {
        List<Player> players = playerRepository.findAll(Sort.by("id"));
        if (players.isEmpty()) {
            return List.of();
        }
        // 별칭은 선수마다 조회하지 않고 한 번에 가져와 묶는다(N+1 방지).
        Map<Long, List<PlayerAlias>> aliasesByPlayer = aliasRepository
                .findByPlayerIdInOrderByCreatedAtAscIdAsc(players.stream().map(Player::id).toList()).stream()
                .collect(Collectors.groupingBy(PlayerAlias::playerId));
        return players.stream()
                .map(p -> PlayerResponse.from(p, aliasesByPlayer.getOrDefault(p.id(), List.of())))
                .toList();
    }

    @Transactional(readOnly = true)
    public PlayerResponse get(Long id) {
        Player player = playerRepository.findById(id).orElseThrow(() -> playerNotFound(id));
        return toResponse(player);
    }

    @Transactional
    public PlayerResponse create(PlayerCreateRequest request) {
        // 1) 요청 안 중복: 비교 키(대소문자·전각 무시)가 같으면 같은 닉네임이다.
        Map<String, String> normalizedByKey = new LinkedHashMap<>();
        for (String raw : request.nicknames()) {
            String normalized = normalizeOrReject(raw);
            String key = NicknameNormalizer.comparisonKey(normalized);
            if (normalizedByKey.putIfAbsent(key, normalized) != null) {
                throw ApiException.badRequest("DUPLICATE_NICKNAME_IN_REQUEST",
                        "요청에 같은 닉네임이 두 번 들어 있습니다: " + normalized);
            }
        }
        // 2) 이미 다른 선수의 별칭인 닉네임: 친절한 409(전부 모아서 알려 준다)
        throwIfConflicts(normalizedByKey);

        Player player;
        try {
            player = playerRepository.save(Player.registered(normalizedByKey.values().iterator().next(),
                    request.realName(), request.mainPosition()));
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("INVALID_REAL_NAME", e.getMessage());
        }
        // 3) 동시 요청이 사전 조회를 빠져나가도 DB UNIQUE가 막는다 → saveAlias가 409로 바꾼다.
        // 입력 순서대로 넣는다(첫 닉네임 = 대표 = 가장 오래된 별칭). 두 요청이 [a, b]와 [b, a]를 동시에 넣어
        // 교착이 나면 MySQL이 한쪽을 통째로 롤백하고, ApiExceptionHandler가 409 CONCURRENT_REQUEST_CONFLICT로 알린다.
        for (String nickname : normalizedByKey.values()) {
            saveAlias(player.id(), nickname);
        }
        return toResponse(player);
    }

    @Transactional
    public PlayerResponse update(Long id, PlayerUpdateRequest request) {
        // 별칭 삭제와 같은 행 잠금으로 줄을 세운다. 잠그지 않으면 삭제가 바꾼 대표 닉네임을
        // 이 요청의 늦은 UPDATE(모든 컬럼을 쓴다)가 예전 값으로 되돌릴 수 있다(Codex M3 리뷰 1).
        Player player = playerRepository.findByIdForUpdate(id).orElseThrow(() -> playerNotFound(id));
        boolean hasRealName = request.realName() != null && !request.realName().isBlank();
        if (hasRealName) {
            try {
                player.changeRealName(request.realName());
            } catch (IllegalArgumentException e) {
                throw ApiException.badRequest("INVALID_REAL_NAME", e.getMessage());
            }
        } else if (player.isRegistered()) {
            throw ApiException.badRequest("REAL_NAME_CANNOT_BE_CLEARED", "등록된 선수의 실명은 비울 수 없습니다.");
        }
        player.changeMainPosition(request.mainPosition());
        playerRepository.flush();
        return toResponse(player);
    }

    /** API용: 별칭을 추가하고 갱신된 선수 전체를 돌려준다. */
    @Transactional
    public PlayerResponse addAliasAndGet(Long playerId, String rawNickname) {
        addAlias(playerId, rawNickname);
        return toResponse(playerRepository.findById(playerId).orElseThrow(() -> playerNotFound(playerId)));
    }

    /**
     * 별칭 하나를 만든다. M3 API와 M4(담당자가 명시적으로 연결·확인한 닉네임 저장)가 함께 쓴다.
     * 자동 연결된 읽은 글자는 호출하지 않는다(설계 §5·§9). 호출자의 트랜잭션에 참여한다.
     */
    @Transactional
    public PlayerAlias addAlias(Long playerId, String rawNickname) {
        if (!playerRepository.existsById(playerId)) {
            throw playerNotFound(playerId);
        }
        String normalized = normalizeOrReject(rawNickname);
        throwIfConflicts(Map.of(NicknameNormalizer.comparisonKey(normalized), normalized));
        return saveAlias(playerId, rawNickname);
    }

    @Transactional
    public void deleteAlias(Long playerId, Long aliasId) {
        // 선수 행을 먼저 잠근다: 같은 선수의 별칭 삭제 요청이 줄을 서므로 "개수 확인 → 삭제"가 원자적이 된다.
        // (잠금 읽기는 최신 커밋을 읽고, 그 뒤의 개수 조회가 스냅샷을 만든다 → 앞선 삭제가 보인다.)
        Player player = playerRepository.findByIdForUpdate(playerId).orElseThrow(() -> playerNotFound(playerId));
        PlayerAlias alias = aliasRepository.findByIdAndPlayerId(aliasId, playerId)
                .orElseThrow(() -> ApiException.notFound("ALIAS_NOT_FOUND", "해당 선수의 별칭이 없습니다: " + aliasId));
        if (aliasRepository.countByPlayerId(playerId) <= 1) {
            throw ApiException.conflict("LAST_ALIAS", "선수의 마지막 별칭은 삭제할 수 없습니다.");
        }
        aliasRepository.delete(alias);
        aliasRepository.flush();
        if (alias.nickname().equals(player.gameNickname())) {
            // 대표 닉네임이 사라지면 남은 별칭 중 가장 오래된 것이 대표가 된다(같은 트랜잭션).
            List<PlayerAlias> remaining = aliasRepository.findByPlayerIdOrderByCreatedAtAscIdAsc(playerId);
            player.changeGameNickname(remaining.get(0).nickname());
        }
    }

    private PlayerAlias saveAlias(Long playerId, String rawNickname) {
        PlayerAlias alias;
        try {
            alias = PlayerAlias.of(playerId, rawNickname);
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("INVALID_NICKNAME", e.getMessage());
        }
        try {
            return aliasRepository.saveAndFlush(alias);
        } catch (DataIntegrityViolationException e) {
            // 사전 조회와 저장 사이에 다른 요청이 같은 키를 먼저 넣은 경우: UNIQUE 위반을 같은 409로 바꾼다.
            throw ApiException.conflict("ALIAS_CONFLICT", "이미 다른 선수에게 연결된 닉네임입니다: " + alias.nickname());
        }
    }

    /** 비교 키 → 정규화 닉네임 맵을 받아, 이미 존재하는 별칭이 있으면 conflicts를 담아 409. */
    private void throwIfConflicts(Map<String, String> normalizedByKey) {
        Map<String, PlayerAlias> existing = aliasRepository.findByNicknameKeyIn(normalizedByKey.keySet()).stream()
                .collect(Collectors.toMap(PlayerAlias::nicknameKey, a -> a));
        if (existing.isEmpty()) {
            return;
        }
        Set<Long> ownerIds = existing.values().stream().map(PlayerAlias::playerId).collect(Collectors.toSet());
        Map<Long, String> displayNames = new HashMap<>();
        playerRepository.findAllById(ownerIds).forEach(p -> displayNames.put(p.id(), p.displayName()));
        List<AliasConflictResponse> conflicts = new ArrayList<>();
        normalizedByKey.forEach((key, nickname) -> {
            PlayerAlias owner = existing.get(key);
            if (owner != null) {
                conflicts.add(new AliasConflictResponse(nickname, owner.playerId(),
                        displayNames.get(owner.playerId())));
            }
        });
        throw ApiException.conflict("ALIAS_CONFLICT", "이미 다른 선수에게 연결된 닉네임이 있습니다.")
                .with("conflicts", conflicts);
    }

    private ApiException invalidNickname() {
        return ApiException.badRequest("INVALID_NICKNAME",
                "닉네임은 1~" + PlayerAlias.MAX_NICKNAME_LENGTH + "자여야 합니다.");
    }

    private String normalizeOrReject(String raw) {
        String normalized = raw == null ? "" : NicknameNormalizer.normalize(raw);
        if (normalized.isEmpty()
                || normalized.codePointCount(0, normalized.length()) > PlayerAlias.MAX_NICKNAME_LENGTH) {
            throw invalidNickname();
        }
        return normalized;
    }

    private PlayerResponse toResponse(Player player) {
        return PlayerResponse.from(player, aliasRepository.findByPlayerIdOrderByCreatedAtAscIdAsc(player.id()));
    }

    private ApiException playerNotFound(Long id) {
        return ApiException.notFound("PLAYER_NOT_FOUND", "선수를 찾을 수 없습니다: " + id);
    }
}
