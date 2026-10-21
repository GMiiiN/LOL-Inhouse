package com.lolinhouse.teammaker;

import java.time.Duration;
import java.time.Instant;

import com.lolinhouse.common.web.ApiException;
import com.lolinhouse.common.web.TierTeamErrorCodes;
import com.lolinhouse.ingest.extract.MutableClock;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 역할: 남용 방지 규칙(분당 30회 고정 창, 동시 계산 2개)을 시계 주입으로 검증한다. */
class TeamMakerGuardTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-20T00:00:10Z"));
    private final TeamMakerGuard guard = new TeamMakerGuard(clock);

    @Test
    void 같은_주소의_31번째_요청은_429_RATE_LIMITED다() {
        for (int i = 0; i < 30; i++) {
            guard.checkRate("1.1.1.1");
        }
        assertThatThrownBy(() -> guard.checkRate("1.1.1.1"))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.status()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(e.code()).isEqualTo(TierTeamErrorCodes.RATE_LIMITED);
                });
    }

    @Test
    void 다른_주소는_따로_센다() {
        for (int i = 0; i < 30; i++) {
            guard.checkRate("1.1.1.1");
        }
        assertThatCode(() -> guard.checkRate("2.2.2.2")).doesNotThrowAnyException();
    }

    @Test
    void 다음_분이_되면_다시_30회를_쓸_수_있다() {
        for (int i = 0; i < 30; i++) {
            guard.checkRate("1.1.1.1");
        }
        clock.advance(Duration.ofSeconds(60));
        assertThatCode(() -> guard.checkRate("1.1.1.1")).doesNotThrowAnyException();
    }

    @Test
    void 동시_계산_자리는_2개이고_세_번째는_즉시_503이다() {
        guard.acquire();
        guard.acquire();
        assertThatThrownBy(guard::acquire).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(e.code()).isEqualTo(TierTeamErrorCodes.TEAM_MAKER_BUSY);
        });
    }

    @Test
    void 반납하면_다시_자리를_얻는다() {
        guard.acquire();
        guard.acquire();
        guard.release();
        assertThatCode(guard::acquire).doesNotThrowAnyException();
    }

    @Test
    void 분_경계에서_늦게_온_이전_분_요청이_다음_분_카운터를_되돌리지_않는다() {
        long nextMinute = clock.millis() / 60_000L + 1;
        long previousMinute = nextMinute - 1;
        for (int i = 0; i < 30; i++) {
            guard.checkRate("1.1.1.1", nextMinute);
        }
        // 이전 분 번호를 들고 늦게 도착한 요청도 새 창에 더해져 31번째로 센다
        assertThatThrownBy(() -> guard.checkRate("1.1.1.1", previousMinute))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo(TierTeamErrorCodes.RATE_LIMITED));
    }

    @Test
    void 정리는_오래된_항목만_지우고_10초_간격으로만_돈다() {
        guard.checkRate("old");
        clock.advance(Duration.ofSeconds(61));
        guard.checkRate("fresh"); // 정리 시점: old(이전 분) 삭제
        assertThat(guard.trackedAddresses()).isEqualTo(1);
        guard.checkRate("fresh2");
        clock.advance(Duration.ofSeconds(5));
        guard.checkRate("fresh3"); // 10초 안이라 정리하지 않지만 현재 분 항목은 어차피 남는다
        assertThat(guard.trackedAddresses()).isEqualTo(3);
    }

    @Test
    void 추적_주소가_상한이면_새_주소는_429이고_기존_주소는_계속_센다() {
        for (int i = 0; i < TeamMakerGuard.MAX_ADDRESSES; i++) {
            guard.checkRate("a" + i);
        }
        assertThatThrownBy(() -> guard.checkRate("newcomer")).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.code()).isEqualTo(TierTeamErrorCodes.RATE_LIMITED));
        assertThatCode(() -> guard.checkRate("a0")).doesNotThrowAnyException();
        assertThat(guard.trackedAddresses()).isEqualTo(TeamMakerGuard.MAX_ADDRESSES);
        clock.advance(Duration.ofSeconds(61));
        assertThatCode(() -> guard.checkRate("newcomer")).doesNotThrowAnyException();
    }
}
