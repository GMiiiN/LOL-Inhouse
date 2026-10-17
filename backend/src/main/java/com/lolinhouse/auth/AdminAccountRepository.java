package com.lolinhouse.auth;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/** 역할: 담당자 계정 저장소. 계정 존재 여부는 count()/existsBy로 확인한다 */
public interface AdminAccountRepository extends JpaRepository<AdminAccount, Long> {

    Optional<AdminAccount> findByUsername(String username);
}
