package com.lolinhouse.player;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 역할: 선수 저장소 */
public interface PlayerRepository extends JpaRepository<Player, Long> {

    /**
     * 선수 행을 SELECT ... FOR UPDATE로 잠그고 읽는다. 별칭 삭제처럼 "개수 확인 후 삭제"를
     * 동시 요청에서 원자적으로 만들기 위한 것이다(설계 §9-3). 반드시 트랜잭션 안에서 호출한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Player p where p.id = :id")
    Optional<Player> findByIdForUpdate(@Param("id") Long id);

    /** 실명이 같은 선수 후보. DB 콜레이션은 대소문자·악센트를 무시하므로 정확히 같은지는 호출자가 Java로 다시 거른다. */
    List<Player> findByRealName(String realName);
}
