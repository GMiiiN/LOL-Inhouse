package com.lolinhouse.domain.set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lolinhouse.domain.set.SetFingerprint.TeamTotals;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class SetFingerprintTest {

    // 샘플 1: 2026/10/11, 18:14, 위쪽 팀 킬 26 골드 39852, 아래쪽 팀 킬 8 골드 29846
    private static final LocalDate SAMPLE1_DATE = LocalDate.of(2026, 10, 11);
    private static final int SAMPLE1_SECONDS = 18 * 60 + 14;
    private static final TeamTotals SAMPLE1_TOP = new TeamTotals(26, 39852);
    private static final TeamTotals SAMPLE1_BOTTOM = new TeamTotals(8, 29846);

    @Test
    void 샘플1_지문은_날짜_시간_그리고_킬_골드_작은_쪽부터_이어_붙인_문자열이다() {
        SetFingerprint fingerprint =
                SetFingerprint.of(SAMPLE1_DATE, SAMPLE1_SECONDS, SAMPLE1_TOP, SAMPLE1_BOTTOM);

        assertThat(fingerprint.canonical()).isEqualTo("2026-10-11|1094|8:29846|26:39852");
    }

    @Test
    void 같은_경기를_상대_팀_클라이언트에서_캡처해도_지문이_같다() {
        SetFingerprint fromWinnerClient =
                SetFingerprint.of(SAMPLE1_DATE, SAMPLE1_SECONDS, SAMPLE1_TOP, SAMPLE1_BOTTOM);
        SetFingerprint fromLoserClient =
                SetFingerprint.of(SAMPLE1_DATE, SAMPLE1_SECONDS, SAMPLE1_BOTTOM, SAMPLE1_TOP);

        assertThat(fromLoserClient).isEqualTo(fromWinnerClient);
    }

    @Test
    void 킬이_같으면_골드로_순서를_정한다() {
        SetFingerprint a = SetFingerprint.of(SAMPLE1_DATE, 600,
                new TeamTotals(10, 30000), new TeamTotals(10, 25000));
        SetFingerprint b = SetFingerprint.of(SAMPLE1_DATE, 600,
                new TeamTotals(10, 25000), new TeamTotals(10, 30000));

        assertThat(a.canonical()).isEqualTo("2026-10-11|600|10:25000|10:30000");
        assertThat(a).isEqualTo(b);
    }

    @Test
    void 킬_수는_문자열_정렬이_아니라_숫자_순으로_비교한다() {
        // 문자열이면 "26" < "8" 이 되어 순서가 뒤집힌다
        SetFingerprint fingerprint =
                SetFingerprint.of(SAMPLE1_DATE, SAMPLE1_SECONDS, SAMPLE1_TOP, SAMPLE1_BOTTOM);

        assertThat(fingerprint.canonical()).contains("|8:29846|26:39852");
    }

    @Test
    void 샘플3은_샘플1과_다른_지문이다() {
        SetFingerprint sample3 = SetFingerprint.of(LocalDate.of(2026, 10, 12), 25 * 60 + 42,
                new TeamTotals(16, 44767), new TeamTotals(32, 60381));

        assertThat(sample3.canonical()).isEqualTo("2026-10-12|1542|16:44767|32:60381");
        assertThat(sample3).isNotEqualTo(
                SetFingerprint.of(SAMPLE1_DATE, SAMPLE1_SECONDS, SAMPLE1_TOP, SAMPLE1_BOTTOM));
    }

    @Test
    void 날짜가_다르면_다른_지문이다() {
        SetFingerprint other = SetFingerprint.of(LocalDate.of(2026, 10, 12), SAMPLE1_SECONDS,
                SAMPLE1_TOP, SAMPLE1_BOTTOM);

        assertThat(other.canonical()).startsWith("2026-10-12|");
    }

    @Test
    void 값이_잘못되면_예외() {
        assertThatThrownBy(() -> SetFingerprint.of(null, 600, SAMPLE1_TOP, SAMPLE1_BOTTOM))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("날짜");
        assertThatThrownBy(() -> SetFingerprint.of(SAMPLE1_DATE, 0, SAMPLE1_TOP, SAMPLE1_BOTTOM))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("경기 시간");
        assertThatThrownBy(() -> SetFingerprint.of(SAMPLE1_DATE, 600, null, SAMPLE1_BOTTOM))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("팀");
        assertThatThrownBy(() -> new TeamTotals(-1, 100))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("킬");
        assertThatThrownBy(() -> new TeamTotals(1, -100))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("골드");
    }

    @Test
    void 경기_시간_문자열을_초로_바꾼다() {
        assertThat(SetFingerprint.parseDuration("18:14")).isEqualTo(1094);
        assertThat(SetFingerprint.parseDuration("25:42")).isEqualTo(1542);
        assertThat(SetFingerprint.parseDuration("05:03")).isEqualTo(303);
        assertThat(SetFingerprint.parseDuration("100:00")).isEqualTo(6000);
    }

    @Test
    void 형식이_잘못된_경기_시간은_예외() {
        for (String invalid : new String[] {null, "", "1814", "18:6", "8:14", "18:60", "18:14:00",
                "ab:cd", "-1:30", "1000:00"}) {
            assertThatThrownBy(() -> SetFingerprint.parseDuration(invalid))
                    .as("입력: %s", invalid)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("mm:ss");
        }
    }

    @Test
    void 경기_시간을_모르면_시간_자리에_물음표를_넣고_팀_순서와_무관하다() {
        SetFingerprint a = SetFingerprint.ofUnknownDuration(SAMPLE1_DATE, SAMPLE1_TOP, SAMPLE1_BOTTOM);
        SetFingerprint b = SetFingerprint.ofUnknownDuration(SAMPLE1_DATE, SAMPLE1_BOTTOM, SAMPLE1_TOP);

        assertThat(a.canonical()).isEqualTo("2026-10-11|?|8:29846|26:39852");
        assertThat(b).isEqualTo(a);
    }

    @Test
    void 시간을_모르는_지문은_초가_있는_지문과_절대_같지_않다() {
        SetFingerprint known = SetFingerprint.of(SAMPLE1_DATE, SAMPLE1_SECONDS, SAMPLE1_TOP, SAMPLE1_BOTTOM);
        SetFingerprint unknown = SetFingerprint.ofUnknownDuration(SAMPLE1_DATE, SAMPLE1_TOP, SAMPLE1_BOTTOM);

        assertThat(unknown).isNotEqualTo(known);
    }
}
