package com.lolinhouse.auth;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * 역할: 담당자(관리자) 계정 한 줄. 비밀번호는 BCrypt 해시만 저장한다(평문 저장·로그 금지). 해시 생성과 로그인 로직은 auth 패키지의 보안 코드가 맡는다.
 */
@Entity
@Table(name = "admin_account")
public class AdminAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "username", nullable = false, length = 50)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AdminAccount() {
    }

    private AdminAccount(String username, String passwordHash) {
        this.username = username;
        this.passwordHash = passwordHash;
    }

    /** passwordHash는 이미 BCrypt로 해시된 값이어야 한다. */
    public static AdminAccount of(String username, String passwordHash) {
        if (username == null || username.isBlank() || passwordHash == null || passwordHash.isBlank()) {
            throw new IllegalArgumentException("사용자 이름과 비밀번호 해시가 필요합니다.");
        }
        return new AdminAccount(username, passwordHash);
    }

    public Long id() {
        return id;
    }

    public String username() {
        return username;
    }

    public String passwordHash() {
        return passwordHash;
    }

    public Instant createdAt() {
        return createdAt;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
