package com.lolinhouse.registration;

import java.util.List;
import java.util.Map;

import com.lolinhouse.domain.player.KnownAlias;

/**
 * 역할: 선수·별칭 조회 스냅샷. displayNames(선수 id → 표시 이름), aliasOwners(닉네임 비교 키 → 선수 id), knownAliases(닉네임 매처 입력).
 */
public record PlayerDirectory(Map<Long, String> displayNames, Map<String, Long> aliasOwners,
        List<KnownAlias> knownAliases) {
}
