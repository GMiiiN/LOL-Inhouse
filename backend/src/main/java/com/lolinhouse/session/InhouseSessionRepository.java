package com.lolinhouse.session;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 역할: 내전 묶음 저장소 */
public interface InhouseSessionRepository extends JpaRepository<InhouseSession, Long> {

    /** 시작일로 조회(생성 전 사전 검사, 409의 existingSessionId). */
    Optional<InhouseSession> findByStartDate(LocalDate startDate);

    /** 목록 화면용: 시작일 내림차순(최신이 위). */
    List<InhouseSession> findAllByOrderByStartDateDesc();

    /**
     * 묶음 행을 FOR UPDATE로 잠그고 읽는다. "새 대결로 등록"이 seq = max+1을 계산해 대결을 만드는 동안
     * 같은 묶음의 다른 대결 생성을 줄 세운다(docs/design/m4-design.md §11.1). 트랜잭션 안에서 호출한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from InhouseSession s where s.id = :id")
    Optional<InhouseSession> findByIdForUpdate(@Param("id") Long id);
}
