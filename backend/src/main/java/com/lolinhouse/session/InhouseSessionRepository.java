package com.lolinhouse.session;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/** 역할: 내전 묶음 저장소 */
public interface InhouseSessionRepository extends JpaRepository<InhouseSession, Long> {

    /** 시작일로 조회(생성 전 사전 검사, 409의 existingSessionId). */
    Optional<InhouseSession> findByStartDate(LocalDate startDate);

    /** 목록 화면용: 시작일 내림차순(최신이 위). */
    List<InhouseSession> findAllByOrderByStartDateDesc();
}
