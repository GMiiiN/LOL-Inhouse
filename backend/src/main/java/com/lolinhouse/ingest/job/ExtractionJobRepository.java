package com.lolinhouse.ingest.job;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 역할: 추출 작업 저장소. 실행기·조회·복구가 경쟁하는 상태 전이는 모두 "WHERE status = 기대 상태" 조건부 UPDATE이고,
 * 갱신된 행 수(1이면 내가 전이시킴, 0이면 이미 다른 쪽이 바꿈)를 돌려준다. 0이면 호출자는 결과를 버리고 로그만 남긴다.
 * 각 UPDATE는 호출자의 트랜잭션에 참여하며, 호출자가 트랜잭션이 없으면 메서드마다 짧은 트랜잭션이 된다(runner 전체를 트랜잭션으로 감싸지 않는다).
 * 설명: docs/design/m4-design.md §2.4, §11.2-4
 */
public interface ExtractionJobRepository extends JpaRepository<ExtractionJob, Long> {

    /** 저장 트랜잭션용: 작업 행을 FOR UPDATE로 잠그고 읽는다. 대결 행을 먼저 잠근 뒤 호출한다(잠금 순서: 대결 → 작업). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from ExtractionJob j where j.id = :id")
    Optional<ExtractionJob> findByIdForUpdate(@Param("id") Long id);

    /**
     * 같은 대결·같은 이미지의 재사용 후보, 최신(id 큰 것)이 먼저. 호출자는 첫 번째를 쓴다.
     * 조건: 저장 안 됨(game_set_id IS NULL)이고 "저장 안 된 SUCCEEDED", "만료 전 QUEUED", "오래되지 않은 RUNNING" 중 하나.
     *
     * @param queuedCreatedNotBefore  이 시각 이후에 만들어진 QUEUED만 후보(now - queued-expire-after)
     * @param runningStartedNotBefore 이 시각 이후에 시작한 RUNNING만 후보(now - stale-after)
     */
    @Query("select j from ExtractionJob j where j.duelId = :duelId and j.imageHash = :imageHash and j.gameSetId is null "
            + "and (j.status = com.lolinhouse.ingest.job.ExtractionJobStatus.SUCCEEDED "
            + "or (j.status = com.lolinhouse.ingest.job.ExtractionJobStatus.QUEUED and j.createdAt >= :queuedCreatedNotBefore) "
            + "or (j.status = com.lolinhouse.ingest.job.ExtractionJobStatus.RUNNING and j.startedAt >= :runningStartedNotBefore)) "
            + "order by j.id desc")
    List<ExtractionJob> findReusableCandidates(@Param("duelId") Long duelId, @Param("imageHash") String imageHash,
            @Param("queuedCreatedNotBefore") Instant queuedCreatedNotBefore,
            @Param("runningStartedNotBefore") Instant runningStartedNotBefore);

    /** QUEUED → RUNNING 선점. 1이면 이 실행기가 작업을 집었다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update ExtractionJob j set j.status = com.lolinhouse.ingest.job.ExtractionJobStatus.RUNNING, "
            + "j.startedAt = :now, j.updatedAt = :now "
            + "where j.id = :id and j.status = com.lolinhouse.ingest.job.ExtractionJobStatus.QUEUED")
    int claimRunning(@Param("id") Long id, @Param("now") Instant now);

    /** RUNNING → SUCCEEDED + 결과 JSON. 0이면 그 사이 TIMEOUT·INTERRUPTED로 끝난 작업이므로 늦은 성공을 버린다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update ExtractionJob j set j.status = com.lolinhouse.ingest.job.ExtractionJobStatus.SUCCEEDED, "
            + "j.resultJson = :resultJson, j.attemptCount = :attemptCount, j.finishedAt = :now, j.updatedAt = :now "
            + "where j.id = :id and j.status = com.lolinhouse.ingest.job.ExtractionJobStatus.RUNNING")
    int succeed(@Param("id") Long id, @Param("resultJson") String resultJson,
            @Param("attemptCount") int attemptCount, @Param("now") Instant now);

    /** QUEUED 또는 RUNNING → FAILED(code). 실행기 실패와 BUSY(QUEUED에서 거절) 모두 이 메서드를 쓴다. 이미 끝난 작업은 건드리지 않는다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update ExtractionJob j set j.status = com.lolinhouse.ingest.job.ExtractionJobStatus.FAILED, "
            + "j.errorCode = :code, j.attemptCount = :attemptCount, j.finishedAt = :now, j.updatedAt = :now "
            + "where j.id = :id and j.status in (com.lolinhouse.ingest.job.ExtractionJobStatus.QUEUED, "
            + "com.lolinhouse.ingest.job.ExtractionJobStatus.RUNNING)")
    int fail(@Param("id") Long id, @Param("code") ExtractionErrorCode code,
            @Param("attemptCount") int attemptCount, @Param("now") Instant now);

    /** 조회 시 정리: started_at이 startedBefore보다 오래된 RUNNING → FAILED(EXTRACTION_TIMEOUT). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update ExtractionJob j set j.status = com.lolinhouse.ingest.job.ExtractionJobStatus.FAILED, "
            + "j.errorCode = com.lolinhouse.ingest.job.ExtractionErrorCode.EXTRACTION_TIMEOUT, "
            + "j.finishedAt = :now, j.updatedAt = :now "
            + "where j.id = :id and j.status = com.lolinhouse.ingest.job.ExtractionJobStatus.RUNNING "
            + "and j.startedAt < :startedBefore")
    int expireStaleRunning(@Param("id") Long id, @Param("startedBefore") Instant startedBefore,
            @Param("now") Instant now);

    /** 조회 시 정리: created_at이 createdBefore보다 오래된 QUEUED → FAILED(EXPIRED). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update ExtractionJob j set j.status = com.lolinhouse.ingest.job.ExtractionJobStatus.FAILED, "
            + "j.errorCode = com.lolinhouse.ingest.job.ExtractionErrorCode.EXPIRED, "
            + "j.finishedAt = :now, j.updatedAt = :now "
            + "where j.id = :id and j.status = com.lolinhouse.ingest.job.ExtractionJobStatus.QUEUED "
            + "and j.createdAt < :createdBefore")
    int expireQueued(@Param("id") Long id, @Param("createdBefore") Instant createdBefore, @Param("now") Instant now);

    /** "다시 추출"(retry=true): 같은 대결·같은 이미지의 저장 안 된 QUEUED·RUNNING 작업을 FAILED(SUPERSEDED)로 바꾼다. 바꾼 행 수를 돌려준다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update ExtractionJob j set j.status = com.lolinhouse.ingest.job.ExtractionJobStatus.FAILED, "
            + "j.errorCode = com.lolinhouse.ingest.job.ExtractionErrorCode.SUPERSEDED, "
            + "j.finishedAt = :now, j.updatedAt = :now "
            + "where j.duelId = :duelId and j.imageHash = :imageHash and j.gameSetId is null "
            + "and j.status in (com.lolinhouse.ingest.job.ExtractionJobStatus.QUEUED, "
            + "com.lolinhouse.ingest.job.ExtractionJobStatus.RUNNING)")
    int supersede(@Param("duelId") Long duelId, @Param("imageHash") String imageHash, @Param("now") Instant now);

    /** 앱 시작 복구: 남아 있는 QUEUED·RUNNING 전부 → FAILED(INTERRUPTED). 바꾼 행 수를 돌려준다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update ExtractionJob j set j.status = com.lolinhouse.ingest.job.ExtractionJobStatus.FAILED, "
            + "j.errorCode = com.lolinhouse.ingest.job.ExtractionErrorCode.INTERRUPTED, "
            + "j.finishedAt = :now, j.updatedAt = :now "
            + "where j.status in (com.lolinhouse.ingest.job.ExtractionJobStatus.QUEUED, "
            + "com.lolinhouse.ingest.job.ExtractionJobStatus.RUNNING)")
    int failAllUnfinishedAsInterrupted(@Param("now") Instant now);
}
