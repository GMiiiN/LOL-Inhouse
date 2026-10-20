package com.lolinhouse.session;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 역할: 대결 = 같은 두 팀이 이어서 치르는 여러 세트의 묶음("대결 N"). 같은 묶음에서 seq는 1부터 하나씩 늘고 (session_id, seq)는 UNIQUE.
 * 초기화는 새 대결을 만드는 것이라 기존 대결은 바뀌지 않는다(설계 D5). 소속 묶음은 연관관계 없이 id로만 안다.
 * fearlessMode는 만들 때 고른 피어리스 방식(RESET/CONTINUE, docs/design/m4-design.md §11.1).
 */
@Entity
@Table(name = "duel")
public class Duel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "seq", nullable = false)
    private int seq;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "fearless_mode", nullable = false, length = 8)
    private FearlessMode fearlessMode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Duel() {
    }

    private Duel(Long sessionId, int seq, FearlessMode fearlessMode) {
        this.sessionId = sessionId;
        this.seq = seq;
        this.fearlessMode = fearlessMode;
    }

    public static Duel of(Long sessionId, int seq, FearlessMode fearlessMode) {
        if (sessionId == null) {
            throw new IllegalArgumentException("내전 묶음 id가 필요합니다.");
        }
        if (seq < 1) {
            throw new IllegalArgumentException("대결 번호는 1 이상이어야 합니다.");
        }
        if (fearlessMode == null) {
            throw new IllegalArgumentException("피어리스 방식이 필요합니다.");
        }
        return new Duel(sessionId, seq, fearlessMode);
    }

    public Long id() {
        return id;
    }

    public Long sessionId() {
        return sessionId;
    }

    public int seq() {
        return seq;
    }

    public FearlessMode fearlessMode() {
        return fearlessMode;
    }

    public Instant createdAt() {
        return createdAt;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
