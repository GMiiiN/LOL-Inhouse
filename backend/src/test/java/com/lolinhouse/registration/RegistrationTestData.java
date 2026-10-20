package com.lolinhouse.registration;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import com.lolinhouse.domain.Position;
import com.lolinhouse.domain.Side;
import com.lolinhouse.ingest.ExtractionResultFixture;
import com.lolinhouse.ingest.job.ExtractionJob;
import com.lolinhouse.ingest.job.ExtractionJobRepository;
import com.lolinhouse.player.PlayerCreateRequest;
import com.lolinhouse.player.PlayerService;
import com.lolinhouse.registration.RegistrationFixtures.Req;
import com.lolinhouse.session.FearlessMode;
import com.lolinhouse.session.InhouseSessionService;
import tools.jackson.databind.json.JsonMapper;

/**
 * 역할: 통합 테스트용 데이터 준비 — 묶음·대결, 선수, 성공한 추출 작업(ExtractionResult JSON을 직접 INSERT), 그 선수들로 채운 저장 요청.
 * DB는 테스트끼리 공유하므로 날짜·닉네임·이미지 해시는 매번 무작위로 만들어 서로 겹치지 않게 한다.
 */
final class RegistrationTestData {

    private final InhouseSessionService sessions;
    private final PlayerService players;
    private final ExtractionJobRepository jobs;
    private final JsonMapper json;

    RegistrationTestData(InhouseSessionService sessions, PlayerService players, ExtractionJobRepository jobs,
            JsonMapper json) {
        this.sessions = sessions;
        this.players = players;
        this.jobs = jobs;
        this.json = json;
    }

    long newSession() {
        LocalDate date = LocalDate.of(3000, 1, 1).plusDays(ThreadLocalRandom.current().nextInt(2_000_000));
        return sessions.create(date).id();
    }

    long newDuel(long sessionId, FearlessMode mode) {
        return sessions.createDuel(sessionId, mode).id();
    }

    /** 새 묶음의 첫 대결(RESET) id. */
    long newFirstDuel() {
        return newDuel(newSession(), FearlessMode.RESET);
    }

    /** 실명이 있는 선수 n명을 만들고 id를 돌려준다(별칭은 고유한 무작위 닉네임 하나). */
    long[] newPlayers(int count) {
        long[] ids = new long[count];
        for (int i = 0; i < count; i++) {
            String suffix = UUID.randomUUID().toString().substring(0, 8);
            ids[i] = players.create(new PlayerCreateRequest("실명" + suffix, null, List.of("선수" + suffix))).id();
        }
        return ids;
    }

    long succeededJob(long duelId) {
        String hash = (UUID.randomUUID().toString() + UUID.randomUUID()).replace("-", "").substring(0, 64);
        ExtractionJob job = ExtractionJob.queued(duelId, Side.BLUE, Position.ADC, hash, "tt/" + hash + ".png");
        Instant now = Instant.now();
        job.markRunning(now);
        job.succeed(json.writeValueAsString(ExtractionResultFixture.sample()), 1, now);
        return jobs.save(job).id();
    }

    long queuedJob(long duelId) {
        String hash = (UUID.randomUUID().toString() + UUID.randomUUID()).replace("-", "").substring(0, 64);
        return jobs.save(ExtractionJob.queued(duelId, Side.BLUE, Position.ADC, hash, "tt/" + hash + ".png")).id();
    }

    /** 무작위 날짜(경기 날짜)와 경기 시간. 같은 요청끼리 같은 지문을 쓰려면 이 값을 재사용한다. */
    record Game(String shotDate, String duration) {

        static Game random() {
            LocalDate date = LocalDate.of(3000, 1, 1).plusDays(ThreadLocalRandom.current().nextInt(2_000_000));
            int seconds = 600 + ThreadLocalRandom.current().nextInt(3000);
            return new Game(date.toString(), String.format("%02d:%02d", seconds / 60, seconds % 60));
        }
    }

    /** 위쪽 5명·아래쪽 5명을 기존 선수로 채운 요청(1세트용 기본 챔피언: 위쪽 00~04, 아래쪽 05~09, 밴 10~19). */
    Req request(long jobId, long[] topIds, long[] bottomIds, Game game) {
        Req request = new Req();
        request.jobId = jobId;
        request.shotDate = game.shotDate();
        request.duration = game.duration();
        for (int i = 0; i < 5; i++) {
            long topId = topIds[i];
            long bottomId = bottomIds[i];
            request.topRow(i, row -> RegistrationFixtures.existing(row, topId));
            request.bottomRow(i, row -> RegistrationFixtures.existing(row, bottomId));
        }
        return request;
    }

    /** 이전 세트와 겹치지 않게 픽을 Champion10~19로, 밴을 Champion00~09로 바꾼다(피어리스 통과용). */
    static Req withOtherChampions(Req request) {
        for (int i = 0; i < 5; i++) {
            int index = i;
            request.topRow(i, row -> RegistrationFixtures.withChampion(row, RegistrationFixtures.champion(10 + index)));
            request.bottomRow(i,
                    row -> RegistrationFixtures.withChampion(row, RegistrationFixtures.champion(15 + index)));
            request.top.bans.set(i, RegistrationFixtures.champion(i));
            request.bottom.bans.set(i, RegistrationFixtures.champion(5 + i));
        }
        return request;
    }
}
