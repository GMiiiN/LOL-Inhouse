package com.lolinhouse.stats;

import com.lolinhouse.domain.stats.RankingEntry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 역할: 도메인의 순위 없음(UNRANKED = 0)이 API에서 null이 되고 실제 순위는 그대로 나가는지 확인한다. */
class RankingServiceTest {

    @Test
    void 도메인의_UNRANKED_0은_API에서_null이다() {
        assertThat(RankingService.rankOrNull(RankingEntry.UNRANKED)).isNull();
        assertThat(RankingService.rankOrNull(0)).isNull();
    }

    @Test
    void 실제_순위는_그대로_나간다() {
        assertThat(RankingService.rankOrNull(1)).isEqualTo(1);
        assertThat(RankingService.rankOrNull(3)).isEqualTo(3);
    }
}
