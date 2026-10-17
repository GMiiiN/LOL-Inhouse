package com.lolinhouse.player;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/** 역할: 별칭 저장소 */
public interface PlayerAliasRepository extends JpaRepository<PlayerAlias, Long> {

    /** 비교 키(comparisonKey)로 기존 별칭 조회. 충돌 사전 검사용. */
    List<PlayerAlias> findByNicknameKeyIn(Collection<String> nicknameKeys);

    /** 선수 한 명의 별칭, 만든 순서대로(같은 시각이면 id 순). 가장 오래된 별칭 = 첫 원소. */
    List<PlayerAlias> findByPlayerIdOrderByCreatedAtAscIdAsc(Long playerId);

    /** 선수 여러 명의 별칭을 한 번에(목록 화면의 N+1 방지). */
    List<PlayerAlias> findByPlayerIdInOrderByCreatedAtAscIdAsc(Collection<Long> playerIds);

    /** 다른 선수의 별칭 id로 삭제하는 실수를 막기 위해 선수 id와 함께 조회한다. */
    Optional<PlayerAlias> findByIdAndPlayerId(Long id, Long playerId);

    long countByPlayerId(Long playerId);
}
