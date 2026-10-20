package com.lolinhouse.ingest.job;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import com.lolinhouse.IntegrationTest;
import com.lolinhouse.session.Duel;
import com.lolinhouse.session.DuelRepository;
import com.lolinhouse.session.InhouseSessionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 역할: 앱 시작 시 복구가 QUEUED·RUNNING만 INTERRUPTED로 바꾸고 SUCCEEDED·FAILED는 건드리지 않는지,
 * 그리고 ApplicationReadyEvent에 실제로 연결돼 있는지 확인한다.
 */
@IntegrationTest
class ExtractionJobRecoveryTest {

    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    ExtractionJobRepository jobs;
    @Autowired
    DuelRepository duels;
    @Autowired
    InhouseSessionRepository sessions;
    @Autowired
    ExtractionJobRecovery recovery;
    @Autowired
    ConfigurableApplicationContext context;

    private long insert(long duelId, String status, String hash) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        boolean succeeded = status.equals("SUCCEEDED");
        boolean failed = status.equals("FAILED");
        jdbc.update("insert into extraction_job (duel_id, status, top_side, capturer_position, image_hash, image_key, "
                + "result_json, error_code, attempt_count, created_at, started_at, updated_at) "
                + "values (?, ?, 'BLUE', 'ADC', ?, ?, ?, ?, 0, ?, ?, ?)", duelId, status, hash, "ab/" + hash + ".png",
                succeeded ? "{}" : null, failed ? "BUSY" : null, now, status.equals("QUEUED") ? null : now, now);
        return jdbc.queryForObject("select max(id) from extraction_job where duel_id = ?", Long.class, duelId);
    }

    @Test
    void recoverMarksOnlyUnfinishedJobsAsInterrupted() {
        Duel duel = ExtractionTestSupport.newDuel(sessions, duels);
        long queued = insert(duel.id(), "QUEUED", "1".repeat(64));
        long running = insert(duel.id(), "RUNNING", "2".repeat(64));
        long succeeded = insert(duel.id(), "SUCCEEDED", "3".repeat(64));
        long failed = insert(duel.id(), "FAILED", "4".repeat(64));

        int changed = recovery.recover();

        assertThat(changed).isGreaterThanOrEqualTo(2);
        for (long id : new long[] {queued, running}) {
            ExtractionJob job = jobs.findById(id).orElseThrow();
            assertThat(job.status()).isEqualTo(ExtractionJobStatus.FAILED);
            assertThat(job.errorCode()).isEqualTo(ExtractionErrorCode.INTERRUPTED);
        }
        assertThat(jobs.findById(succeeded).orElseThrow().status()).isEqualTo(ExtractionJobStatus.SUCCEEDED);
        assertThat(jobs.findById(failed).orElseThrow().errorCode()).isEqualTo(ExtractionErrorCode.BUSY);
    }

    @Test
    void applicationReadyEventTriggersRecovery() {
        Duel duel = ExtractionTestSupport.newDuel(sessions, duels);
        long running = insert(duel.id(), "RUNNING", "5".repeat(64));

        context.publishEvent(new ApplicationReadyEvent(new SpringApplication(), new String[0], context,
                Duration.ZERO));

        assertThat(jobs.findById(running).orElseThrow().errorCode()).isEqualTo(ExtractionErrorCode.INTERRUPTED);
    }
}
