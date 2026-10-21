package com.lolinhouse.tier;

import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

/** 역할: 점수표 저장소(id = 1 한 행) */
public interface ScoreSettingRepository extends JpaRepository<ScoreSetting, Integer> {

    /**
     * id = 1 행을 SELECT ... FOR UPDATE로 잠그고 읽는다. 점수표 수정과 프로필·선수 생성/수정의 전역 직렬화에 쓴다.
     * 잠금 순서는 score_setting, 그다음 선수 행이다. 반드시 트랜잭션 안에서 호출한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ScoreSetting s where s.id = 1")
    Optional<ScoreSetting> findForUpdate();
}
