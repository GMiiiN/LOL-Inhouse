package com.lolinhouse.teammaker;

import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;

import com.lolinhouse.common.web.ApiException;
import com.lolinhouse.common.web.TierTeamErrorCodes;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 역할: 공개 팀 짜기 계산의 남용 방지(설계 §11 P2-7). 접속 주소별 분당 30회(메모리 고정 창)와 동시 계산 2개 한도.
 * 터널 뒤 실제 주소 헤더 처리는 M5.5에서 한다. 여기서는 받은 주소 문자열을 그대로 키로 쓴다.
 */
@Component
public class TeamMakerGuard {

    public static final int MAX_PER_MINUTE = 30;
    public static final int MAX_CONCURRENT = 2;
    private static final long WINDOW_MILLIS = 60_000L;
    static final int MAX_ADDRESSES = 10_000;
    private static final long PURGE_INTERVAL_MILLIS = 10_000L;

    private record Window(long minute, int count) {
    }

    private final Clock clock;
    private final Semaphore permits = new Semaphore(MAX_CONCURRENT);
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final AtomicLong lastPurgeMillis = new AtomicLong(Long.MIN_VALUE / 2);

    @Autowired
    public TeamMakerGuard() {
        this(Clock.systemUTC());
    }

    TeamMakerGuard(Clock clock) {
        this.clock = clock;
    }

    /** 이 주소의 이번 분 요청 수를 1 올린다. 30회를 넘으면 429 RATE_LIMITED. */
    public void checkRate(String address) {
        checkRate(address, clock.millis() / WINDOW_MILLIS);
    }

    /**
     * minute은 요청이 들어온 분. 늦게 도착한 요청이 이미 저장된 더 새로운 창을 되돌리지 못하게, 저장된 창이 같거나 더 새로우면 그 창에 더한다.
     * 추적 주소 수는 MAX_ADDRESSES로 막는다: 가득 차면 처음 보는 주소를 429로 거절한다(기존 항목을 쫓아내면 공격자가 한도를 비울 수 있고,
     * 오래된 항목은 정리 주기마다 지워져 다음 분이면 자리가 난다).
     */
    void checkRate(String address, long minute) {
        purgeIfDue();
        if (windows.size() >= MAX_ADDRESSES && !windows.containsKey(address)) {
            throw rateLimited();
        }
        Window window = windows.compute(address, (key, old) ->
                old == null || old.minute() < minute ? new Window(minute, 1) : new Window(old.minute(), old.count() + 1));
        if (window.count() > MAX_PER_MINUTE) {
            throw rateLimited();
        }
    }

    /** 정리는 PURGE_INTERVAL_MILLIS에 한 번만, 현재 분보다 오래된 항목만 지운다. */
    private void purgeIfDue() {
        long now = clock.millis();
        long last = lastPurgeMillis.get();
        if (now - last < PURGE_INTERVAL_MILLIS || !lastPurgeMillis.compareAndSet(last, now)) {
            return;
        }
        long currentMinute = now / WINDOW_MILLIS;
        windows.entrySet().removeIf(entry -> entry.getValue().minute() < currentMinute);
    }

    int trackedAddresses() {
        return windows.size();
    }

    private static ApiException rateLimited() {
        return ApiException.tooManyRequests(TierTeamErrorCodes.RATE_LIMITED,
                "요청이 너무 많아요. 잠시 뒤에 다시 시도해 주세요.");
    }

    /** 계산 자리를 즉시 얻는다. 자리가 없으면 기다리지 않고 503 TEAM_MAKER_BUSY. 얻었으면 반드시 {@link #release()}. */
    public void acquire() {
        if (!permits.tryAcquire()) {
            throw ApiException.serviceUnavailable(TierTeamErrorCodes.TEAM_MAKER_BUSY,
                    "지금 팀 짜기 계산이 몰려 있어요. 잠시 뒤에 다시 시도해 주세요.");
        }
    }

    public void release() {
        permits.release();
    }
}
