package com.lolinhouse.ingest;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import javax.imageio.ImageIO;

import com.google.genai.Client;
import com.google.genai.types.GenerateContentConfig;
import com.lolinhouse.champion.CatalogSnapshot;
import com.lolinhouse.champion.Champion;
import com.lolinhouse.ingest.extract.Box;
import com.lolinhouse.ingest.extract.Deadline;
import com.lolinhouse.ingest.extract.ExtractionException;
import com.lolinhouse.ingest.extract.ExtractionOutcome;
import com.lolinhouse.ingest.job.ExtractionErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 역할: Phase 0 계약 중 순수 부분(Deadline, Box, CatalogSnapshot)과 테스트 기반(가짜 추출기·가짜 Data Dragon·합성 결과창)이
 * 약속대로 동작하는지, 그리고 google-genai SDK(Jackson 2)가 Boot(Jackson 3)와 같은 클래스패스에서 쓰이는지 확인한다.
 * 작업자 A·B·C는 이 기반을 믿고 쓴다.
 */
class IngestFoundationTest {

    private final FakeScoreboardExtractor extractor = new FakeScoreboardExtractor();
    private final FakeDataDragonSource dataDragon = new FakeDataDragonSource();

    @AfterEach
    void resetFakes() {
        extractor.reset();
        dataDragon.reset();
    }

    // ---------- Deadline ----------

    private static final class MutableClock extends Clock {
        private final AtomicReference<Instant> now;

        MutableClock(Instant start) {
            this.now = new AtomicReference<>(start);
        }

        void advance(Duration duration) {
            now.updateAndGet(current -> current.plus(duration));
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    }

    @Test
    void deadlineShrinksRequestTimeoutAndStopsNewRequestsInTheLastFiveSeconds() {
        MutableClock clock = new MutableClock(Instant.parse("2026-10-17T00:00:00Z"));
        Deadline deadline = Deadline.of(clock, clock.instant(), Duration.ofMinutes(5));

        assertThat(deadline.remaining()).isEqualTo(Duration.ofMinutes(5));
        assertThat(deadline.requestTimeout(Duration.ofSeconds(90))).isEqualTo(Duration.ofSeconds(90));
        assertThat(deadline.canStartRequest()).isTrue();

        clock.advance(Duration.ofSeconds(250));
        assertThat(deadline.requestTimeout(Duration.ofSeconds(90))).isEqualTo(Duration.ofSeconds(50));

        clock.advance(Duration.ofSeconds(47));
        assertThat(deadline.remaining()).isEqualTo(Duration.ofSeconds(3));
        assertThat(deadline.canStartRequest()).isFalse();
        assertThat(deadline.isExpired()).isFalse();

        clock.advance(Duration.ofSeconds(10));
        assertThat(deadline.remaining()).isEqualTo(Duration.ZERO);
        assertThat(deadline.isExpired()).isTrue();
    }

    // ---------- Box / CatalogSnapshot ----------

    @Test
    void boxConvertsToAndFromTheFourNumberArrayInYFirstOrder() {
        Box box = new Box(80, 25, 144, 65);

        assertThat(box.toList()).containsExactly(80, 25, 144, 65);
        assertThat(box.toArray()).containsExactly(80, 25, 144, 65);
        assertThat(Box.fromList(box.toList())).isEqualTo(box);
        assertThatThrownBy(() -> Box.fromList(List.of(1, 2, 3))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void catalogSnapshotKnowsWhichChampionIdsExist() {
        CatalogSnapshot snapshot = new CatalogSnapshot("16.20.1",
                List.of(new Champion("Ahri", "103", "아리"), new Champion("MonkeyKing", "62", "오공")),
                (crop, ban) -> null);

        assertThat(snapshot.contains("MonkeyKing")).isTrue();
        assertThat(snapshot.contains("Wukong")).isFalse();
    }

    // ---------- FakeScoreboardExtractor ----------

    @Test
    void fakeExtractorPlaysTheScriptInOrderThenRepeatsTheLastStepAndCountsCalls() throws Exception {
        var scoreboard = SyntheticScoreboardImage.defaultImage().scoreboard();
        ExtractionException busy = new ExtractionException(ExtractionErrorCode.EXTRACTION_UPSTREAM_UNAVAILABLE, 503,
                true, "일시 오류");
        extractor.thenThrow(busy).thenReturn(scoreboard);

        assertThatThrownBy(() -> extractor.extract(new byte[0], "image/png", Duration.ofSeconds(90))).isSameAs(busy);
        assertThat(extractor.extract(new byte[0], "image/png", Duration.ofSeconds(90))).isEqualTo(scoreboard);
        assertThat(extractor.extract(new byte[0], "image/png", Duration.ofSeconds(30))).isEqualTo(scoreboard);
        assertThat(extractor.calls()).isEqualTo(3);
        assertThat(extractor.lastRequestTimeout()).isEqualTo(Duration.ofSeconds(30));

        extractor.reset();
        assertThat(extractor.calls()).isZero();
        assertThatThrownBy(() -> extractor.extract(new byte[0], "image/png", Duration.ofSeconds(1)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void fakeExtractorAsResilientExtractorReturnsOutcomesAndAttachesAttemptsToFailures() throws Exception {
        var scoreboard = SyntheticScoreboardImage.defaultImage().scoreboard();
        var deadline = Deadline.of(Clock.systemUTC(), Instant.now(), Duration.ofMinutes(5));
        extractor.thenReturn(scoreboard)
                .thenThrow(new ExtractionException(ExtractionErrorCode.EXTRACTION_REJECTED, 400, false, "거절"));

        ExtractionOutcome outcome = extractor.extract(new byte[0], "image/png", deadline);
        assertThat(outcome.scoreboard()).isEqualTo(scoreboard);
        assertThat(outcome.attempts()).hasSize(1);

        assertThatThrownBy(() -> extractor.extract(new byte[0], "image/png", deadline))
                .isInstanceOfSatisfying(ExtractionException.class, e -> {
                    assertThat(e.errorCode()).isEqualTo(ExtractionErrorCode.EXTRACTION_REJECTED);
                    assertThat(e.attempts()).hasSize(1);
                    assertThat(e.attempts().get(0).httpStatus()).isEqualTo(400);
                });
    }

    // ---------- FakeDataDragonSource ----------

    @Test
    void fakeDataDragonServesVersionsChampionsAndIconsAndCanGoOffline() throws Exception {
        String versions = new String(dataDragon.fetch("/api/versions.json"), StandardCharsets.UTF_8);
        String champions = new String(dataDragon.fetch("/cdn/16.20.1/data/ko_KR/champion.json"), StandardCharsets.UTF_8);
        BufferedImage icon = ImageIO.read(new ByteArrayInputStream(dataDragon.fetch("/cdn/16.20.1/img/champion/Champion03.png")));

        assertThat(versions).contains("16.20.1");
        assertThat(champions).contains("\"Champion00\"").contains("\"Champion19\"").contains("챔피언07");
        assertThat(icon.getWidth()).isEqualTo(FakeDataDragonSource.ICON_SIZE);
        assertThat(dataDragon.fetchCount()).isEqualTo(3);

        dataDragon.setOffline(true);
        assertThatThrownBy(() -> dataDragon.fetch("/api/versions.json")).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> {
            dataDragon.setOffline(false);
            dataDragon.fetch("/cdn/16.20.1/img/champion/Nope.png");
        }).isInstanceOf(IOException.class);
    }

    @Test
    void syntheticIconsAreDeterministicAndDistinctPerChampion() {
        BufferedImage first = FakeDataDragonSource.iconImage(2);
        BufferedImage again = FakeDataDragonSource.iconImage(2);
        BufferedImage other = FakeDataDragonSource.iconImage(3);

        assertThat(sameImage(first, again)).isTrue();
        assertThat(sameImage(first, other)).isFalse();
    }

    // ---------- SyntheticScoreboardImage ----------

    @Test
    void syntheticScoreboardIsAReadablePngWithBoxesAtTheDrawnPositions() throws Exception {
        SyntheticScoreboardImage image = SyntheticScoreboardImage.defaultImage();
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(image.png()));

        assertThat(decoded.getWidth()).isEqualTo(SyntheticScoreboardImage.WIDTH);
        assertThat(decoded.getHeight()).isEqualTo(SyntheticScoreboardImage.HEIGHT);
        // [ymin, xmin, ymax, xmax] 0~1000, y가 먼저
        assertThat(image.portraitBox(true, 0)).isEqualTo(new Box(80, 25, 144, 65));
        assertThat(image.portraitBox(false, 4)).isEqualTo(new Box(840, 25, 904, 65));
        assertThat(image.banBox(true, 0)).isEqualTo(new Box(80, 250, 128, 280));
        assertThat(image.banBox(true, 3)).isEqualTo(new Box(144, 250, 192, 280));

        var scoreboard = image.scoreboard();
        assertThat(scoreboard.top().rows()).hasSize(5);
        assertThat(scoreboard.bottom().banBoxes()).hasSize(5);
        assertThat(scoreboard.top().rows().get(2).portraitBox()).isEqualTo(image.portraitBox(true, 2));
        assertThat(scoreboard.top().kills()).isEqualTo(
                scoreboard.top().rows().stream().mapToInt(row -> row.kills()).sum());
    }

    @Test
    void banIsDrawnDarkerThanTheOriginalIcon() throws Exception {
        SyntheticScoreboardImage image = SyntheticScoreboardImage.defaultImage();
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(image.png()));

        // 위쪽 팀 밴 0번 칸(챔피언 10)의 (+8, +20)픽셀은 원본 아이콘의 (20, 50)에 해당한다(48px → 120px 확대 2.5배)
        int ban = brightness(decoded.getRGB(400 + 8, 80 + 20));
        int original = brightness(FakeDataDragonSource.iconImage(image.banIndex(true, 0)).getRGB(20, 50));
        assertThat(ban).isLessThan(original);
    }

    // ---------- SDK 공존 ----------

    @Test
    void googleGenaiSdkWorksNextToJackson3() {
        GenerateContentConfig config = GenerateContentConfig.builder()
                .responseMimeType("application/json").temperature(0.0f).build();

        // SDK는 내부적으로 Jackson 2로 직렬화한다. Boot는 Jackson 3를 쓰므로 둘이 한 클래스패스에 있다.
        assertThat(config.toJson()).contains("responseMimeType").contains("application/json");
        try (Client client = Client.builder().apiKey("not-a-real-key").build()) {
            assertThat(client).isNotNull();
        }
    }

    private static boolean sameImage(BufferedImage a, BufferedImage b) {
        for (int y = 0; y < a.getHeight(); y++) {
            for (int x = 0; x < a.getWidth(); x++) {
                if (a.getRGB(x, y) != b.getRGB(x, y)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static int brightness(int rgb) {
        return ((rgb >> 16) & 255) + ((rgb >> 8) & 255) + (rgb & 255);
    }
}
