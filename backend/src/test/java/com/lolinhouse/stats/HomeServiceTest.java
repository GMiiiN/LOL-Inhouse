package com.lolinhouse.stats;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.lolinhouse.domain.DuelTeam;
import com.lolinhouse.domain.Position;
import com.lolinhouse.domain.stats.PlayerSetRecord;
import com.lolinhouse.stats.HomeResponse.TopPick;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 역할: 홈의 라인별 최다 픽 선택 규칙(픽 수 → 승률 → 한국어 이름)을 DB 없이 확인한다. */
class HomeServiceTest {

    private static final AtomicInteger SET_SEQ = new AtomicInteger();

    private static PlayerSetRecord pick(Position position, String champion, boolean win) {
        String setId = String.valueOf(SET_SEQ.incrementAndGet());
        return new PlayerSetRecord("1", "1", setId, DuelTeam.TEAM1, position, champion, win, 1, 1, 1, 1, 1);
    }

    @Test
    void 기록이_없어도_다섯_라인_키가_모두_있고_값은_null이다() {
        Map<Position, TopPick> result = HomeService.topPicks(List.of(), Map::of);

        assertThat(result).containsOnlyKeys(Position.values());
        assertThat(result.values()).containsOnlyNulls();
    }

    @Test
    void 픽_횟수가_가장_많은_챔피언이_이긴다() {
        List<PlayerSetRecord> records = List.of(pick(Position.TOP, "Ahri", true), pick(Position.TOP, "Zed", false),
                pick(Position.TOP, "Zed", false), pick(Position.MID, "Ahri", true));

        Map<Position, TopPick> result = HomeService.topPicks(records, Map::of);

        assertThat(result.get(Position.TOP)).isEqualTo(new TopPick("Zed", 2, 0));
        assertThat(result.get(Position.MID)).isEqualTo(new TopPick("Ahri", 1, 1));
        assertThat(result.get(Position.JUNGLE)).isNull();
    }

    @Test
    void 픽_횟수가_같으면_승률이_높은_쪽이다() {
        List<PlayerSetRecord> records = List.of(pick(Position.ADC, "Ahri", false), pick(Position.ADC, "Ahri", true),
                pick(Position.ADC, "Zed", true), pick(Position.ADC, "Zed", true));

        assertThat(HomeService.topPicks(records, Map::of).get(Position.ADC)).isEqualTo(new TopPick("Zed", 2, 2));
    }

    @Test
    void 픽_횟수와_승률이_같으면_한국어_이름순이고_키_순서와_다를_수_있다() {
        List<PlayerSetRecord> records = List.of(pick(Position.SUPPORT, "Aatrox", true),
                pick(Position.SUPPORT, "Zed", true));
        Map<String, String> names = Map.of("Aatrox", "아트록스", "Zed", "가나다");

        assertThat(HomeService.topPicks(records, () -> names).get(Position.SUPPORT).championKey()).isEqualTo("Zed");
    }

    @Test
    void 동률이_아니면_챔피언_카탈로그를_읽지_않는다() {
        AtomicInteger calls = new AtomicInteger();
        List<PlayerSetRecord> records = List.of(pick(Position.TOP, "Ahri", true), pick(Position.TOP, "Ahri", true),
                pick(Position.TOP, "Zed", true));

        HomeService.topPicks(records, () -> {
            calls.incrementAndGet();
            return Map.of();
        });

        assertThat(calls).hasValue(0);
    }

    @Test
    void 카탈로그에_없는_키는_키_글자로_비교한다() {
        List<PlayerSetRecord> records = List.of(pick(Position.TOP, "Zed", true), pick(Position.TOP, "Ahri", true));

        assertThat(HomeService.topPicks(records, Map::of).get(Position.TOP).championKey()).isEqualTo("Ahri");
    }
}
