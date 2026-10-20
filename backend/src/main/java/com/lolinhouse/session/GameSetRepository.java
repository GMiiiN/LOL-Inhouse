package com.lolinhouse.session;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 역할: 세트 저장소. 아래 "유효"는 모두 삭제되지 않은(deleted_at IS NULL) 세트를 뜻한다.
 * M4 등록 흐름(중복 이미지 검사, 지문 중복, 다음 세트 번호, 직전 세트, 피어리스 제한)이 쓰는 조회를 모았다.
 * 설명: docs/design/m4-design.md §2.6
 */
public interface GameSetRepository extends JpaRepository<GameSet, Long> {

    /** 대결의 유효 세트를 세트 번호 순으로. */
    List<GameSet> findByDuelIdAndDeletedAtIsNullOrderBySetNoAsc(Long duelId);

    /** 같은 이미지 해시의 유효 세트(업로드 1차 중복 검사). 해시는 UNIQUE가 아니라 여러 개일 수 있어 First로 하나만 본다. */
    Optional<GameSet> findFirstByImageHashAndDeletedAtIsNull(String imageHash);

    /** 같은 지문의 유효 세트(초안 평가·저장 전 중복 검사). 유효 세트끼리 지문이 UNIQUE라 최대 1개다. */
    Optional<GameSet> findByFingerprintAndDeletedAtIsNull(String fingerprint);

    /** 대결의 마지막 유효 세트(직전 세트). 1팀/2팀 판별과 팀 변경 감지의 기준이다. */
    Optional<GameSet> findFirstByDuelIdAndDeletedAtIsNullOrderBySetNoDesc(Long duelId);

    /** 대결의 유효 세트 중 가장 큰 세트 번호, 유효 세트가 없으면 0. 다음 세트 번호 = 이 값 + 1. */
    @Query("select coalesce(max(g.setNo), 0) from GameSet g where g.duelId = :duelId and g.deletedAt is null")
    int findMaxActiveSetNo(@Param("duelId") Long duelId);

    /** 한 대결의 유효 세트에서 양 팀이 픽한 챔피언 키 전부(중복 포함). 피어리스 제한 계산용. */
    @Query("select p.championKey from GameSetPlayer p where p.gameSet.duelId = :duelId and p.gameSet.deletedAt is null")
    List<String> findActivePickChampionKeys(@Param("duelId") Long duelId);

    /** 여러 대결의 유효 세트 픽 챔피언 키 전부. 이어가기(CONTINUE) 체인의 제한을 한 번에 구할 때 쓴다. */
    @Query("select p.championKey from GameSetPlayer p where p.gameSet.duelId in :duelIds and p.gameSet.deletedAt is null")
    List<String> findActivePickChampionKeysByDuelIds(@Param("duelIds") Collection<Long> duelIds);

    /** 세트 하나의 1팀(TEAM1) 선수 id. 다음 세트의 1팀/2팀 판별 기준이다. */
    @Query("select p.playerId from GameSetPlayer p where p.gameSet.id = :gameSetId "
            + "and p.team = com.lolinhouse.domain.DuelTeam.TEAM1")
    List<Long> findTeam1PlayerIds(@Param("gameSetId") Long gameSetId);
}
