package com.lolinhouse.tier;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/** 역할: 라인 티어 저장소 */
public interface PlayerLaneTierRepository extends JpaRepository<PlayerLaneTier, Long> {

    /** 선수 한 명의 라인 티어 전부. */
    List<PlayerLaneTier> findByPlayerId(Long playerId);

    /** 선수 여러 명의 라인 티어를 한 번에(목록·계산 입력의 N+1 방지). */
    List<PlayerLaneTier> findByPlayerIdIn(Collection<Long> playerIds);
}
