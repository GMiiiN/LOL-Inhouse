package com.lolinhouse.record;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RecordViewsTest {

    private static PickRow pick(long duelId, int setNo, String champion) {
        return new PickRow(duelId * 10 + setNo, duelId, setNo, champion);
    }

    @Test
    void 첫_세트는_제한이_없다() {
        List<PickRow> picks = List.of(pick(1, 1, "Ahri"), pick(1, 2, "Zed"));

        assertThat(RecordViews.fearlessRestrictedAt(List.of(1L), 1L, 1, picks)).isEmpty();
    }

    @Test
    void 같은_대결은_더_작은_세트_번호의_픽만_제한한다() {
        List<PickRow> picks = List.of(pick(1, 1, "Ahri"), pick(1, 2, "Zed"), pick(1, 3, "Lux"));

        assertThat(RecordViews.fearlessRestrictedAt(List.of(1L), 1L, 3, picks)).containsExactly("Ahri", "Zed");
    }

    @Test
    void 체인의_앞_대결은_세트_번호와_상관없이_전부_제한한다_중복은_하나로_키_오름차순() {
        List<PickRow> picks = List.of(pick(1, 1, "Zed"), pick(1, 2, "Ahri"), pick(1, 1, "Zed"), pick(2, 1, "Lux"));

        assertThat(RecordViews.fearlessRestrictedAt(List.of(1L, 2L), 2L, 1, picks)).containsExactly("Ahri", "Zed");
    }

    @Test
    void 체인_밖_대결의_픽은_무시한다() {
        List<PickRow> picks = List.of(pick(1, 1, "Ahri"), pick(2, 1, "Lux"), pick(3, 1, "Zed"));

        // 체인은 [2, 3]: 대결 1은 RESET 이전이라 제한이 아니다
        assertThat(RecordViews.fearlessRestrictedAt(List.of(2L, 3L), 3L, 1, picks)).containsExactly("Lux");
    }
}
