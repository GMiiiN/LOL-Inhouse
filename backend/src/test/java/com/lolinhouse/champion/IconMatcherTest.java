package com.lolinhouse.champion;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import com.lolinhouse.ingest.FakeDataDragonSource;
import com.lolinhouse.ingest.SyntheticScoreboardImage;
import com.lolinhouse.ingest.extract.Box;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 합성 결과창(Phase 0)으로 PICK·BAN 설정이 정답을 1위로 뽑는지, 상위 3개·margin·동점 정렬을 시험한다. */
class IconMatcherTest {

    private static IconMatcher matcher;
    private static SyntheticScoreboardImage scoreboard;
    private static BufferedImage decoded;

    @BeforeAll
    static void build() throws Exception {
        List<IconMatcher.Icon> icons = new ArrayList<>();
        for (int i = 0; i < FakeDataDragonSource.DEFAULT_CHAMPION_COUNT; i++) {
            icons.add(new IconMatcher.Icon(FakeDataDragonSource.championKey(i), FakeDataDragonSource.championId(i),
                    FakeDataDragonSource.iconImage(i)));
        }
        matcher = new IconMatcher(icons);
        scoreboard = SyntheticScoreboardImage.defaultImage();
        decoded = ImageIO.read(new ByteArrayInputStream(scoreboard.png()));
    }

    private static BufferedImage crop(Box box) {
        return Boxes.crop(decoded, box.toArray());
    }

    @Test
    void picksMatchTheDrawnChampionFirst() {
        for (boolean top : new boolean[] {true, false}) {
            for (int row = 0; row < 5; row++) {
                IconMatch match = matcher.top(crop(scoreboard.portraitBox(top, row)), false);
                assertThat(match.candidates().get(0).id())
                        .isEqualTo(FakeDataDragonSource.championId(scoreboard.pickIndex(top, row)));
                assertThat(match.cropFailed()).isFalse();
            }
        }
    }

    @Test
    void bansMatchTheDrawnChampionFirst() {
        for (boolean top : new boolean[] {true, false}) {
            for (int slot = 0; slot < 5; slot++) {
                IconMatch match = matcher.top(crop(scoreboard.banBox(top, slot)), true);
                assertThat(match.candidates().get(0).id())
                        .isEqualTo(FakeDataDragonSource.championId(scoreboard.banIndex(top, slot)));
            }
        }
    }

    @Test
    void returnsTopThreeDescendingWithMarginBetweenFirstAndSecond() {
        IconMatch match = matcher.top(crop(scoreboard.portraitBox(true, 0)), false);

        assertThat(match.candidates()).hasSize(3);
        assertThat(match.candidates().get(0).score()).isGreaterThanOrEqualTo(match.candidates().get(1).score());
        assertThat(match.candidates().get(1).score()).isGreaterThanOrEqualTo(match.candidates().get(2).score());
        assertThat(match.margin()).isEqualTo(match.candidates().get(0).score() - match.candidates().get(1).score());
        assertThat(match.margin()).isGreaterThan(0.05);
    }

    @Test
    void tiesAreOrderedById() {
        BufferedImage same = FakeDataDragonSource.iconImage(1);
        IconMatcher tied = new IconMatcher(List.of(new IconMatcher.Icon("2", "Zed", same),
                new IconMatcher.Icon("1", "Aatrox", same), new IconMatcher.Icon("3", "Mid", same)));

        IconMatch match = tied.top(FakeDataDragonSource.iconImage(1), false);

        assertThat(match.candidates()).extracting(IconMatch.Candidate::id).containsExactly("Aatrox", "Mid", "Zed");
        assertThat(match.margin()).isZero();
    }

    @Test
    void needsAtLeastTwoIcons() {
        List<IconMatcher.Icon> one = List.of(new IconMatcher.Icon("1", "Ahri", FakeDataDragonSource.iconImage(0)));
        assertThatThrownBy(() -> new IconMatcher(one)).isInstanceOf(IllegalArgumentException.class);
    }
}
