package com.lolinhouse.ingest.job;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import com.lolinhouse.common.web.ApiException;
import com.lolinhouse.domain.Position;
import com.lolinhouse.domain.Side;
import com.lolinhouse.ingest.image.ImageInspector;
import com.lolinhouse.ingest.image.ImageStore;
import com.lolinhouse.ingest.image.InspectedImage;
import com.lolinhouse.session.DuelRepository;
import com.lolinhouse.session.GameSet;
import com.lolinhouse.session.GameSetRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 역할: 추출 작업의 시작(업로드 → 검사 → 저장 → 작업 INSERT → 비동기 실행 요청)과 상태 조회. 클래스 전체를 트랜잭션으로 묶지 않는다 —
 * 이미지 검사·저장(느림)은 잠금 밖에서, 중복 검사·재사용·INSERT는 대결 행을 잠근 짧은 트랜잭션 하나에서, 실행 요청은 커밋 뒤에 한다
 * (커밋 전에 넘기면 실행 스레드가 아직 없는 행을 읽는다).
 * 같은 이미지를 동시에 올려도 대결 행 잠금이 두 요청을 줄 세워 작업이 하나만 생긴다.
 * 설명: docs/design/m4-design.md D3·D8·§2.4·§11.2-3·§11.2-5, 상태 머신은 같은 문서 §2.4
 */
@Service
public class ExtractionService {

    private static final Logger log = LoggerFactory.getLogger(ExtractionService.class);

    /** start 결과. reused=true면 새 작업이 아니라 기존 작업이다. */
    public record StartResult(Long jobId, ExtractionJobStatus status, boolean reused) {
    }

    private final DuelRepository duels;
    private final GameSetRepository gameSets;
    private final ExtractionJobRepository jobs;
    private final ImageInspector inspector;
    private final ImageStore imageStore;
    private final ExtractionRunner runner;
    private final JobTransitions transitions;
    private final TransactionTemplate tx;
    private final String apiKey;
    private final Duration staleAfter;
    private final Duration queuedExpireAfter;
    private final Clock clock;

    @Autowired
    public ExtractionService(DuelRepository duels, GameSetRepository gameSets, ExtractionJobRepository jobs,
            ImageInspector inspector, ImageStore imageStore, ExtractionRunner runner, JobTransitions transitions,
            PlatformTransactionManager transactionManager, @Value("${GEMINI_API_KEY:}") String apiKey,
            @Value("${app.extraction.stale-after}") Duration staleAfter,
            @Value("${app.extraction.queued-expire-after}") Duration queuedExpireAfter) {
        this(duels, gameSets, jobs, inspector, imageStore, runner, transitions, transactionManager, apiKey,
                staleAfter, queuedExpireAfter, Clock.systemUTC());
    }

    /** 시계를 바꿔 끼우는 생성자(테스트에서 시간을 흘려 보내려는 용도). */
    public ExtractionService(DuelRepository duels, GameSetRepository gameSets, ExtractionJobRepository jobs,
            ImageInspector inspector, ImageStore imageStore, ExtractionRunner runner, JobTransitions transitions,
            PlatformTransactionManager transactionManager, String apiKey, Duration staleAfter,
            Duration queuedExpireAfter, Clock clock) {
        this.duels = duels;
        this.gameSets = gameSets;
        this.jobs = jobs;
        this.inspector = inspector;
        this.imageStore = imageStore;
        this.runner = runner;
        this.transitions = transitions;
        this.tx = new TransactionTemplate(transactionManager);
        this.apiKey = apiKey;
        this.staleAfter = staleAfter;
        this.queuedExpireAfter = queuedExpireAfter;
        this.clock = clock;
    }

    /**
     * 업로드된 이미지로 추출 작업을 시작한다(또는 같은 이미지의 기존 작업을 돌려준다).
     *
     * @param retry true면 재사용을 건너뛰고 같은 대결·같은 이미지의 미저장 QUEUED/RUNNING 작업을 SUPERSEDED로 밀어낸 뒤 새 작업을 만든다
     */
    public StartResult start(Long duelId, byte[] bytes, Side topSide, Position capturerPosition, boolean retry) {
        if (apiKey == null || apiKey.isBlank()) {
            throw ApiException.serviceUnavailable("EXTRACTION_NOT_CONFIGURED",
                    "이미지 추출이 설정되지 않았습니다. 담당자에게 문의하세요.");
        }
        if (!duels.existsById(duelId)) {
            throw duelNotFound(duelId);
        }
        InspectedImage image = inspector.inspect(bytes);
        // 해시가 키라서 같은 이미지는 같은 파일이다(멱등). 잠금 밖에서 먼저 저장한다.
        String imageKey = imageStore.put(image.hash(), image.mimeType(), image.bytes());

        StartResult result = tx.execute(status -> registerJob(duelId, image.hash(), imageKey, topSide,
                capturerPosition, retry));
        if (!result.reused()) {
            dispatch(result.jobId());
        }
        return result;
    }

    /** 작업 상태 조회. 오래 멈춘 RUNNING과 시작 못 한 QUEUED는 여기서 조건부 UPDATE로 정리한다. */
    public ExtractionJobResponse status(Long jobId) {
        ExtractionJob job = jobs.findById(jobId).orElseThrow(() -> notFound(jobId));
        Instant now = clock.instant();
        boolean changed = false;
        if (job.status() == ExtractionJobStatus.RUNNING && job.startedAt() != null
                && job.startedAt().isBefore(now.minus(staleAfter))) {
            changed = transitions.expireStaleRunning(jobId, now.minus(staleAfter), now) > 0;
        } else if (job.status() == ExtractionJobStatus.QUEUED && job.createdAt().isBefore(now.minus(queuedExpireAfter))) {
            changed = transitions.expireQueued(jobId, now.minus(queuedExpireAfter), now) > 0;
        }
        if (changed) {
            job = jobs.findById(jobId).orElseThrow(() -> notFound(jobId));
        }
        return ExtractionJobResponse.from(job);
    }

    /** 대결 행 잠금 아래에서: 유효 세트 해시 → (retry면 밀어내기) → 재사용 후보 → 새 작업 INSERT. */
    private StartResult registerJob(Long duelId, String hash, String imageKey, Side topSide,
            Position capturerPosition, boolean retry) {
        duels.findByIdForUpdate(duelId).orElseThrow(() -> duelNotFound(duelId));
        Optional<GameSet> existing = gameSets.findFirstByImageHashAndDeletedAtIsNull(hash);
        if (existing.isPresent()) {
            GameSet set = existing.get();
            throw ApiException.conflict("DUPLICATE_IMAGE", "이미 등록된 결과창 이미지입니다.")
                    .with("existingSetId", set.id())
                    .with("existingDuelId", set.duelId())
                    .with("existingSetNo", set.setNo());
        }
        Instant now = clock.instant();
        if (retry) {
            jobs.supersede(duelId, hash, now);
        } else {
            Optional<ExtractionJob> reusable = jobs.findReusableCandidates(duelId, hash,
                    now.minus(queuedExpireAfter), now.minus(staleAfter)).stream().findFirst();
            if (reusable.isPresent()) {
                return new StartResult(reusable.get().id(), reusable.get().status(), true);
            }
        }
        ExtractionJob job = jobs.saveAndFlush(ExtractionJob.queued(duelId, topSide, capturerPosition, hash, imageKey));
        return new StartResult(job.id(), job.status(), false);
    }

    /** 커밋된 작업을 실행기에 넘긴다. 대기열이 가득 차면 작업을 FAILED(BUSY)로 남기고 503. */
    private void dispatch(Long jobId) {
        try {
            runner.run(jobId);
        } catch (TaskRejectedException e) {
            try {
                transitions.fail(jobId, ExtractionErrorCode.BUSY, 0, clock.instant());
            } catch (RuntimeException failure) {
                // 상태 기록까지 실패한 QUEUED는 만료 규칙(조회 시 EXPIRED)이 정리한다.
                log.warn("BUSY 기록 실패 jobId={} 예외={}", jobId, failure.getClass().getSimpleName());
            }
            log.warn("추출 대기열 가득 jobId={}", jobId);
            throw ApiException.serviceUnavailable("EXTRACTION_BUSY",
                    "추출 대기열이 가득 찼습니다. 잠시 후 다시 시도하세요.");
        }
    }

    private static ApiException duelNotFound(Long duelId) {
        return ApiException.notFound("DUEL_NOT_FOUND", "대결을 찾을 수 없습니다: " + duelId);
    }

    private static ApiException notFound(Long jobId) {
        return ApiException.notFound("EXTRACTION_NOT_FOUND", "추출 작업을 찾을 수 없습니다: " + jobId);
    }
}
