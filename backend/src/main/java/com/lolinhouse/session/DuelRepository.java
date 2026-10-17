package com.lolinhouse.session;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 역할: 대결 저장소 */
public interface DuelRepository extends JpaRepository<Duel, Long> {

    /** 묶음 안 대결 목록, seq 오름차순. */
    List<Duel> findBySessionIdOrderBySeqAsc(Long sessionId);

    /** 다음 대결 번호 계산용: 대결이 하나도 없으면 0. 새 seq = 이 값 + 1. */
    @Query("select coalesce(max(d.seq), 0) from Duel d where d.sessionId = :sessionId")
    int findMaxSeq(@Param("sessionId") Long sessionId);

    /** 묶음 목록의 duelCount를 한 번의 쿼리로 구한다(대결이 없는 묶음은 결과에 없다 = 0). */
    @Query("select d.sessionId as sessionId, count(d) as duelCount from Duel d "
            + "where d.sessionId in :sessionIds group by d.sessionId")
    List<SessionDuelCount> countBySessionIds(@Param("sessionIds") Collection<Long> sessionIds);

    /** 묶음별 대결 수 조회 결과 한 줄. */
    interface SessionDuelCount {

        Long getSessionId();

        long getDuelCount();
    }
}
