package com.lolinhouse.ingest.job;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

import javax.imageio.ImageIO;

import com.lolinhouse.session.Duel;
import com.lolinhouse.session.DuelRepository;
import com.lolinhouse.session.FearlessMode;
import com.lolinhouse.session.InhouseSession;
import com.lolinhouse.session.InhouseSessionRepository;

/**
 * 역할: 추출 통합 테스트들이 같이 쓰는 도우미(대결 만들기, 서로 다른 이미지 만들기, 비동기 완료 대기). Awaitility 대신 폴링을 쓴다.
 */
final class ExtractionTestSupport {

    private ExtractionTestSupport() {
    }

    /** 테스트마다 먼 미래의 임의 날짜로 묶음과 대결을 새로 만든다(커밋됨). */
    static Duel newDuel(InhouseSessionRepository sessions, DuelRepository duels) {
        LocalDate date = LocalDate.of(4000, 1, 1).plusDays(ThreadLocalRandom.current().nextInt(2_000_000));
        InhouseSession session = sessions.save(InhouseSession.of(date));
        return duels.save(Duel.of(session.id(), 1, FearlessMode.RESET));
    }

    /** seed마다 바이트가 다른 1600x1000 PNG(해시가 달라야 같은 대결에 여러 작업을 만들 수 있다). */
    static byte[] seededPng(int seed) {
        BufferedImage image = new BufferedImage(1600, 1000, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, 0x010101 * (seed % 200 + 20));
        image.setRGB(1, 0, seed * 7919);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "png", out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    /** 조건을 만족하는 상태가 될 때까지 폴링한다. 시간 안에 안 되면 마지막 상태로 실패한다. */
    static ExtractionJob await(ExtractionJobRepository jobs, long jobId, Predicate<ExtractionJob> condition,
            Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        ExtractionJob job;
        do {
            job = jobs.findById(jobId).orElseThrow();
            if (condition.test(job)) {
                return job;
            }
            sleep(50);
        } while (System.nanoTime() < deadline);
        throw new AssertionError("작업 " + jobId + " 이(가) 시간 안에 조건을 만족하지 못함: " + job.status() + "/" + job.errorCode());
    }

    static ExtractionJob awaitFinished(ExtractionJobRepository jobs, long jobId) {
        return await(jobs, jobId, j -> j.status() == ExtractionJobStatus.SUCCEEDED
                || j.status() == ExtractionJobStatus.FAILED, Duration.ofSeconds(30));
    }

    static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
