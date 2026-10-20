package com.lolinhouse.session;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 역할: 대결 저장소 */
public interface DuelRepository extends JpaRepository<Duel, Long> {

    /** 묶음 안 대결 목록, seq 오름차순. */
    List<Duel> findBySessionIdOrderBySeqAsc(Long sessionId);

    /** 다음 대결 번호 계산용: 대결이 하나도 없으면 0. 새 seq = 이 값 + 1. */
    @Query("select coalesce(max(d.seq), 0) from Duel d where d.sessionId = :sessionId")
    int findMaxSeq(@Param("sessionId") Long sessionId);

    /**
     * 대결 행을 SELECT ... FOR UPDATE로 잠그고 읽는다. 같은 대결의 세트 저장·작업 생성을 줄 세워
     * setNo·피어리스 계산과 "같은 이미지 동시 업로드"를 결정적으로 만든다(docs/design/m4-design.md §4.2·§11.2-5).
     * 반드시 트랜잭션 안에서 호출한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Duel d where d.id = :id")
    Optional<Duel> findByIdForUpdate(@Param("id") Long id);

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
