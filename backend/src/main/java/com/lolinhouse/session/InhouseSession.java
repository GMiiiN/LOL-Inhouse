package com.lolinhouse.session;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * 역할: 내전 묶음 = 하루(시작일 기준)의 내전. 시작일은 한국 날짜 그대로 DATE로 저장하고 UNIQUE라 하루에 하나다.
 */
@Entity
@Table(name = "inhouse_session")
public class InhouseSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected InhouseSession() {
    }

    private InhouseSession(LocalDate startDate) {
        this.startDate = startDate;
    }

    public static InhouseSession of(LocalDate startDate) {
        if (startDate == null) {
            throw new IllegalArgumentException("시작일이 필요합니다.");
        }
        return new InhouseSession(startDate);
    }

    public Long id() {
        return id;
    }

    public LocalDate startDate() {
        return startDate;
    }

    public Instant createdAt() {
        return createdAt;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
