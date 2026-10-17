package com.lolinhouse.session;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/** 역할: 세트 저장소(M4~M6에서 본격 사용) */
public interface GameSetRepository extends JpaRepository<GameSet, Long> {

    /** 대결의 유효(삭제되지 않은) 세트를 세트 번호 순으로. */
    List<GameSet> findByDuelIdAndDeletedAtIsNullOrderBySetNoAsc(Long duelId);
}
