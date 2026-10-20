package com.lolinhouse.ingest.job;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import com.lolinhouse.domain.Position;
import com.lolinhouse.domain.Side;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 역할: 결과창 이미지 한 장의 추출 작업. 추출 사실(result_json = {@link ExtractionResult} v1 JSON)만 저장하고 초안은 조회 때 계산한다.
 * 아래 전이 메서드는 메모리 상의 상태 규칙(잘못된 전이는 IllegalStateException)이다. 실행기와 조회가 경쟁하는 전이(선점·성공·실패·만료·복구)는
 * 이 메서드가 아니라 {@link ExtractionJobRepository}의 조건부 UPDATE로 한다. 이 메서드는 저장 트랜잭션(linkGameSet, moveToDuel)과 테스트가 쓴다.
 * 상태 머신: docs/design/m4-design.md §2.4, 스키마: V3__extraction_job.sql
 */
@Entity
@Table(name = "extraction_job")
public class ExtractionJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "duel_id", nullable = false)
    private Long duelId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 10)
    private ExtractionJobStatus status;

    /** 업로드 전에 고른 위쪽 팀 진영. 확인 화면에서 바뀔 수 있어 저장 명령의 값이 최종이다. */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "top_side", nullable = false, length = 4)
    private Side topSide;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "capturer_position", nullable = false, length = 10)
    private Position capturerPosition;

    @Column(name = "image_hash", nullable = false, length = 64)
    private String imageHash;

    @Column(name = "image_key", nullable = false)
    private String imageKey;

    /** SUCCEEDED일 때만 있다. JSON 문자열을 그대로 보관하고 직렬화·역직렬화는 앱이 Jackson 3로 직접 한다. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result_json")
    private String resultJson;

    /** FAILED일 때만 있다. */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "error_code", length = 40)
    private ExtractionErrorCode errorCode;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    /** 저장된 세트. null이면 아직 저장 안 됨. */
    @Column(name = "game_set_id")
    private Long gameSetId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ExtractionJob() {
    }

    /** 대기 중(QUEUED) 작업. 이 행이 커밋된 뒤에 실행기에 넘긴다. */
    public static ExtractionJob queued(Long duelId, Side topSide, Position capturerPosition, String imageHash,
            String imageKey) {
        if (duelId == null || topSide == null || capturerPosition == null || imageHash == null || imageKey == null) {
            throw new IllegalArgumentException("추출 작업의 필수 값이 비어 있습니다.");
        }
        ExtractionJob job = new ExtractionJob();
        job.duelId = duelId;
        job.status = ExtractionJobStatus.QUEUED;
        job.topSide = topSide;
        job.capturerPosition = capturerPosition;
        job.imageHash = imageHash;
        job.imageKey = imageKey;
        return job;
    }

    /** QUEUED → RUNNING. */
    public void markRunning(Instant now) {
        requireStatus(ExtractionJobStatus.QUEUED);
        status = ExtractionJobStatus.RUNNING;
        startedAt = micros(now);
    }

    /** RUNNING → SUCCEEDED. resultJson은 {@link ExtractionResult}를 직렬화한 문자열. */
    public void succeed(String resultJson, int attemptCount, Instant now) {
        requireStatus(ExtractionJobStatus.RUNNING);
        if (resultJson == null || resultJson.isBlank()) {
            throw new IllegalArgumentException("성공한 작업에는 결과 JSON이 필요합니다.");
        }
        status = ExtractionJobStatus.SUCCEEDED;
        this.resultJson = resultJson;
        this.attemptCount = attemptCount;
        finishedAt = micros(now);
    }

    /** QUEUED 또는 RUNNING → FAILED. */
    public void fail(ExtractionErrorCode code, int attemptCount, Instant now) {
        if (status != ExtractionJobStatus.QUEUED && status != ExtractionJobStatus.RUNNING) {
            throw new IllegalStateException("이미 끝난 작업은 실패 처리할 수 없습니다: " + status);
        }
        if (code == null) {
            throw new IllegalArgumentException("실패 코드가 필요합니다.");
        }
        status = ExtractionJobStatus.FAILED;
        errorCode = code;
        this.attemptCount = attemptCount;
        finishedAt = micros(now);
    }

    /** 세트 저장 때 이 작업과 저장된 세트를 1:1로 잇는다. SUCCEEDED이고 아직 연결 안 된 작업만. */
    public void linkGameSet(Long gameSetId) {
        requireStatus(ExtractionJobStatus.SUCCEEDED);
        if (this.gameSetId != null) {
            throw new IllegalStateException("이미 세트에 연결된 작업입니다.");
        }
        if (gameSetId == null) {
            throw new IllegalArgumentException("세트 id가 필요합니다.");
        }
        this.gameSetId = gameSetId;
    }

    /** "새 대결로 등록"에서 작업을 새 대결로 옮긴다(저장 트랜잭션 안). SUCCEEDED이고 아직 저장 안 된 작업만. */
    public void moveToDuel(Long newDuelId) {
        requireStatus(ExtractionJobStatus.SUCCEEDED);
        if (gameSetId != null) {
            throw new IllegalStateException("이미 세트에 연결된 작업은 옮길 수 없습니다.");
        }
        if (newDuelId == null) {
            throw new IllegalArgumentException("대결 id가 필요합니다.");
        }
        this.duelId = newDuelId;
    }

    public Long id() {
        return id;
    }

    public Long duelId() {
        return duelId;
    }

    public ExtractionJobStatus status() {
        return status;
    }

    public Side topSide() {
        return topSide;
    }

    public Position capturerPosition() {
        return capturerPosition;
    }

    public String imageHash() {
        return imageHash;
    }

    public String imageKey() {
        return imageKey;
    }

    public String resultJson() {
        return resultJson;
    }

    public ExtractionErrorCode errorCode() {
        return errorCode;
    }

    public int attemptCount() {
        return attemptCount;
    }

    public Long gameSetId() {
        return gameSetId;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public Instant finishedAt() {
        return finishedAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    private void requireStatus(ExtractionJobStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("작업 상태가 " + expected + "이어야 하는데 " + status + "입니다.");
        }
    }

    private static Instant micros(Instant instant) {
        return instant.truncatedTo(ChronoUnit.MICROS);
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
