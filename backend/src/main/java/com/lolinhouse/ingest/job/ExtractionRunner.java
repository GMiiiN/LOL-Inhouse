package com.lolinhouse.ingest.job;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.imageio.ImageIO;

import com.lolinhouse.champion.CatalogSnapshot;
import com.lolinhouse.champion.ChampionCatalog;
import com.lolinhouse.champion.ChampionIconMatcher;
import com.lolinhouse.champion.IconMatch;
import com.lolinhouse.common.config.AsyncConfig;
import com.lolinhouse.common.web.SafeLog;
import com.lolinhouse.ingest.extract.Box;
import com.lolinhouse.ingest.extract.Deadline;
import com.lolinhouse.ingest.extract.ExtractedRow;
import com.lolinhouse.ingest.extract.ExtractedTeam;
import com.lolinhouse.ingest.extract.ExtractionException;
import com.lolinhouse.ingest.extract.ExtractionOutcome;
import com.lolinhouse.ingest.extract.ResilientExtractor;
import com.lolinhouse.ingest.image.ImageStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * 역할: 추출 작업 하나를 별도 스레드(extractionExecutor)에서 끝까지 처리한다 — 선점 → 카탈로그 확인 → 이미지 읽기 → Gemini 추출(재시도 포함)
 * → 박스 20개 잘라 챔피언 상위 3개 매칭 → 결과 저장. 예외를 밖으로 던지지 않는다(비동기 반환값이 없어 던져도 받을 곳이 없고, 기본 처리기는 메시지를 로그에 남긴다).
 * 상태 전이는 전부 짧은 트랜잭션의 조건부 UPDATE({@link JobTransitions})다. 갱신 행 수가 0이면 그 사이에 TIMEOUT·INTERRUPTED·SUPERSEDED로 바뀐 것이므로
 * 내 결과는 버리고 로그만 남긴다. 작업 전체의 마감은 선점 시각 + app.gemini.job-deadline 하나({@link Deadline})로, 카탈로그·추출·매칭이 함께 쓴다.
 * 로그에는 jobId·상태·오류 코드·시간만 남기고 닉네임·이미지·응답 원문은 남기지 않는다.
 * 이 빈은 @Async 프록시를 거쳐야 하므로 ExtractionService가 이 빈의 {@link #run}을 직접 부른다(같은 클래스 안 호출 금지).
 * 설명: docs/design/m4-design.md D3·§2.4·§11.2-2·§11.2-4
 */
@Component
public class ExtractionRunner {

    private static final Logger log = LoggerFactory.getLogger(ExtractionRunner.class);

    private final JobTransitions transitions;
    private final ExtractionJobRepository jobs;
    private final ImageStore imageStore;
    private final ResilientExtractor extractor;
    private final ChampionCatalog catalog;
    private final JsonMapper json;
    private final String model;
    private final Duration jobDeadline;
    private final Clock clock;

    @Autowired
    public ExtractionRunner(JobTransitions transitions, ExtractionJobRepository jobs, ImageStore imageStore,
            ResilientExtractor extractor, ChampionCatalog catalog, JsonMapper json,
            @Value("${app.gemini.model}") String model, @Value("${app.gemini.job-deadline}") Duration jobDeadline) {
        this(transitions, jobs, imageStore, extractor, catalog, json, model, jobDeadline, Clock.systemUTC());
    }

    /** 시계를 바꿔 끼우는 생성자(테스트에서 마감 초과를 재현하려는 용도). */
    public ExtractionRunner(JobTransitions transitions, ExtractionJobRepository jobs, ImageStore imageStore,
            ResilientExtractor extractor, ChampionCatalog catalog, JsonMapper json, String model,
            Duration jobDeadline, Clock clock) {
        this.transitions = transitions;
        this.jobs = jobs;
        this.imageStore = imageStore;
        this.extractor = extractor;
        this.catalog = catalog;
        this.json = json;
        this.model = model;
        this.jobDeadline = jobDeadline;
        this.clock = clock;
    }

    /** 작업 행이 커밋된 뒤에 호출한다. 대기열이 가득 차면 호출자 쪽에서 TaskRejectedException이 난다(여기까지 오지 않는다). */
    @Async(AsyncConfig.EXTRACTION_EXECUTOR)
    public void run(Long jobId) {
        try {
            execute(jobId);
        } catch (Throwable t) {
            log.error("추출 작업 처리 중 예외 jobId={} 예외={}", jobId, SafeLog.describe(t));
            fail(jobId, ExtractionErrorCode.INTERNAL_ERROR, 0);
        }
    }

    private void execute(Long jobId) {
        Instant startedAt = clock.instant();
        if (transitions.claimRunning(jobId, startedAt) == 0) {
            log.info("추출 작업을 선점하지 못함(이미 처리됐거나 대기 중이 아님) jobId={}", jobId);
            return;
        }
        Deadline deadline = Deadline.of(clock, startedAt, jobDeadline);
        ExtractionJob job = jobs.findById(jobId).orElse(null);
        if (job == null) {
            return;
        }

        // 카탈로그가 없으면 Gemini를 부르기 전에 실패해 무료 한도를 아낀다.
        Optional<CatalogSnapshot> snapshot = catalog.current(deadline);
        if (snapshot.isEmpty()) {
            fail(jobId, ExtractionErrorCode.CHAMPION_CATALOG_UNAVAILABLE, 0);
            return;
        }
        if (deadline.isExpired()) {
            fail(jobId, ExtractionErrorCode.EXTRACTION_TIMEOUT, 0);
            return;
        }

        byte[] bytes;
        BufferedImage image;
        try {
            bytes = imageStore.get(job.imageKey());
            image = ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (IOException | RuntimeException e) {
            log.warn("저장된 이미지를 읽지 못함 jobId={} 예외={}", jobId, e.getClass().getSimpleName());
            fail(jobId, ExtractionErrorCode.IMAGE_UNREADABLE, 0);
            return;
        }
        if (image == null) {
            fail(jobId, ExtractionErrorCode.IMAGE_UNREADABLE, 0);
            return;
        }

        ExtractionOutcome outcome;
        try {
            outcome = extractor.extract(bytes, mimeTypeOf(job.imageKey()), deadline);
        } catch (ExtractionException e) {
            fail(jobId, e.errorCode(), e.attempts().size());
            return;
        }
        int attempts = outcome.attempts().size();
        if (deadline.isExpired()) {
            fail(jobId, ExtractionErrorCode.EXTRACTION_TIMEOUT, attempts);
            return;
        }

        ChampionIconMatcher matcher = snapshot.get().matcher();
        var scoreboard = outcome.scoreboard();
        ExtractionResult result = new ExtractionResult(ExtractionResult.CURRENT_SCHEMA_VERSION, model,
                snapshot.get().version(), outcome.attempts(), scoreboard.topResult(),
                scoreboard.shotDate().toString(), scoreboard.duration(), team(scoreboard.top(), image, matcher),
                team(scoreboard.bottom(), image, matcher));
        if (deadline.isExpired()) {
            fail(jobId, ExtractionErrorCode.EXTRACTION_TIMEOUT, attempts);
            return;
        }

        Instant finishedAt = clock.instant();
        int updated = transitions.succeed(jobId, json.writeValueAsString(result), attempts, finishedAt);
        if (updated == 0) {
            log.warn("추출 결과를 버림(작업이 이미 다른 상태로 바뀜) jobId={}", jobId);
            return;
        }
        log.info("추출 성공 jobId={} 시도={} 소요ms={}", jobId, attempts, Duration.between(startedAt, finishedAt).toMillis());
    }

    private void fail(Long jobId, ExtractionErrorCode code, int attempts) {
        try {
            int updated = transitions.fail(jobId, code, attempts, clock.instant());
            if (updated == 0) {
                log.warn("추출 실패를 기록하지 못함(작업이 이미 다른 상태로 바뀜) jobId={} 코드={}", jobId, code);
            } else {
                log.info("추출 실패 jobId={} 코드={} 시도={}", jobId, code, attempts);
            }
        } catch (RuntimeException e) {
            log.error("추출 실패 기록 중 예외 jobId={} 코드={} 예외={}", jobId, code, SafeLog.describe(e));
        }
    }

    private static ExtractionResult.Team team(ExtractedTeam team, BufferedImage image, ChampionIconMatcher matcher) {
        List<ExtractionResult.Row> rows = new ArrayList<>();
        for (ExtractedRow row : team.rows()) {
            rows.add(new ExtractionResult.Row(row.nickname(), row.kills(), row.deaths(), row.assists(), row.cs(),
                    row.gold(), row.portraitBox().toList(), match(matcher, image, row.portraitBox(), false)));
        }
        List<ExtractionResult.Ban> bans = new ArrayList<>();
        for (Box box : team.banBoxes()) {
            bans.add(new ExtractionResult.Ban(box.toList(), match(matcher, image, box, true)));
        }
        return new ExtractionResult.Team(team.kills(), team.deaths(), team.assists(), team.gold(), rows, bans);
    }

    /** 칸 하나를 잘라 매칭한다. 박스가 이미지 밖이거나 면적이 없으면 그 칸만 cropFailed. */
    private static IconMatch match(ChampionIconMatcher matcher, BufferedImage image, Box box, boolean ban) {
        BufferedImage crop;
        try {
            crop = crop(image, box);
        } catch (RuntimeException e) {
            return IconMatch.failedCrop();
        }
        return matcher.top(crop, ban);
    }

    /** Gemini의 [ymin, xmin, ymax, xmax] 0~1000 정규화 좌표를 원본 픽셀로 바꿔 자른다(경계는 자르고 면적 없는 박스는 거부). */
    static BufferedImage crop(BufferedImage image, Box box) {
        int width = image.getWidth();
        int height = image.getHeight();
        if (box.ymax() <= box.ymin() || box.xmax() <= box.xmin()) {
            throw new IllegalArgumentException("유효하지 않은 박스");
        }
        int left = (int) Math.floor(Math.clamp(box.xmin(), 0, 1000) * width / 1000.0);
        int top = (int) Math.floor(Math.clamp(box.ymin(), 0, 1000) * height / 1000.0);
        int right = (int) Math.ceil(Math.clamp(box.xmax(), 0, 1000) * width / 1000.0);
        int bottom = (int) Math.ceil(Math.clamp(box.ymax(), 0, 1000) * height / 1000.0);
        if (right <= left || bottom <= top) {
            throw new IllegalArgumentException("화면 밖 박스");
        }
        Rectangle rect = new Rectangle(left, top, right - left, bottom - top);
        return image.getSubimage(rect.x, rect.y, rect.width, rect.height);
    }

    private static String mimeTypeOf(String imageKey) {
        return imageKey.endsWith(".png") ? "image/png" : "image/jpeg";
    }
}
