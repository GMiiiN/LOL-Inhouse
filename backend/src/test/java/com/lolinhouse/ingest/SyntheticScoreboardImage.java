package com.lolinhouse.ingest;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.lolinhouse.ingest.extract.Box;
import com.lolinhouse.ingest.extract.ExtractedRow;
import com.lolinhouse.ingest.extract.ExtractedScoreboard;
import com.lolinhouse.ingest.extract.ExtractedScoreboard.TopResult;
import com.lolinhouse.ingest.extract.ExtractedTeam;

/**
 * 역할: 합성 챔피언 아이콘({@link FakeDataDragonSource#iconImage})을 알려진 위치에 그려 넣은 가짜 결과창 PNG(1600x1000)와,
 * 같은 위치를 가리키는 박스를 가진 {@link ExtractedScoreboard}를 함께 만든다. 가짜 추출기가 이 박스를 돌려주면
 * 박스 자르기 → 아이콘 매칭까지 외부 서비스 없이 결정적으로 검증할 수 있다.
 * 그림 규칙: 초상화는 아이콘 중앙 부분을 원형으로 잘라 금색 원 테두리를 두르고, 밴은 아이콘 전체를 어둡게 하고
 * 회색 테두리와 오른쪽 위→왼쪽 아래 사선을 덧그린다(실제 결과창을 흉내 낸 것).
 * 박스는 [ymin, xmin, ymax, xmax] 0~1000 정규화이고, 픽셀 좌표가 8의 배수라 정수로 딱 떨어진다.
 * 설명: docs/design/m4-design.md §7
 */
public final class SyntheticScoreboardImage {

    public static final int WIDTH = 1600;
    public static final int HEIGHT = 1000;

    private static final int PORTRAIT_SIZE = 64;
    private static final int BAN_SIZE = 48;

    private final int[] topPicks;
    private final int[] bottomPicks;
    private final int[] topBans;
    private final int[] bottomBans;
    private final byte[] png;

    private SyntheticScoreboardImage(int[] topPicks, int[] bottomPicks, int[] topBans, int[] bottomBans) {
        this.topPicks = topPicks.clone();
        this.bottomPicks = bottomPicks.clone();
        this.topBans = topBans.clone();
        this.bottomBans = bottomBans.clone();
        this.png = draw();
    }

    /** 위쪽 팀 픽 0~4, 아래쪽 팀 픽 5~9, 위쪽 밴 10~14, 아래쪽 밴 15~19번 챔피언. */
    public static SyntheticScoreboardImage defaultImage() {
        return of(new int[] {0, 1, 2, 3, 4}, new int[] {5, 6, 7, 8, 9}, new int[] {10, 11, 12, 13, 14},
                new int[] {15, 16, 17, 18, 19});
    }

    /** 각 배열은 길이 5, 값은 FakeDataDragonSource의 챔피언 index. */
    public static SyntheticScoreboardImage of(int[] topPicks, int[] bottomPicks, int[] topBans, int[] bottomBans) {
        return new SyntheticScoreboardImage(topPicks, bottomPicks, topBans, bottomBans);
    }

    public byte[] png() {
        return png.clone();
    }

    public int width() {
        return WIDTH;
    }

    public int height() {
        return HEIGHT;
    }

    /** 이 칸에 그려 넣은 챔피언 index (top=위쪽 팀, row 0~4). */
    public int pickIndex(boolean top, int row) {
        return (top ? topPicks : bottomPicks)[row];
    }

    /** 이 밴 칸에 그려 넣은 챔피언 index (top=위쪽 팀, slot 0~4, 위 줄 3개 → 아래 줄 2개 순서). */
    public int banIndex(boolean top, int slot) {
        return (top ? topBans : bottomBans)[slot];
    }

    public Box portraitBox(boolean top, int row) {
        return box(portraitX(), portraitY(top, row), PORTRAIT_SIZE);
    }

    public Box banBox(boolean top, int slot) {
        return box(banX(slot), banY(top, slot), BAN_SIZE);
    }

    /**
     * 이 그림과 맞는 검증된 결과창: 위쪽 팀 승리, 2026-10-12, 25:42. 행의 숫자는 칸마다 다르고 팀 합계는 행 합과 같다.
     * 닉네임은 "합성선수T0".."합성선수T4"(위쪽), "합성선수B0".."합성선수B4"(아래쪽)이다.
     */
    public ExtractedScoreboard scoreboard() {
        return new ExtractedScoreboard(TopResult.WIN, LocalDate.of(2026, 10, 12), "25:42", team(true), team(false));
    }

    private ExtractedTeam team(boolean top) {
        List<ExtractedRow> rows = new ArrayList<>();
        int kills = 0;
        int deaths = 0;
        int assists = 0;
        int gold = 0;
        for (int row = 0; row < 5; row++) {
            int base = top ? 0 : 3;
            ExtractedRow extracted = new ExtractedRow((top ? "합성선수T" : "합성선수B") + row, row + 2 + base,
                    row + 1 + base, row + 3 + base, 100 + 10 * row, 10000 + 500 * row + (top ? 0 : 1000),
                    portraitBox(top, row));
            rows.add(extracted);
            kills += extracted.kills();
            deaths += extracted.deaths();
            assists += extracted.assists();
            gold += extracted.gold();
        }
        List<Box> bans = new ArrayList<>();
        for (int slot = 0; slot < 5; slot++) {
            bans.add(banBox(top, slot));
        }
        return new ExtractedTeam(kills, deaths, assists, gold, rows, bans);
    }

    // ---------- 그리기 ----------

    private byte[] draw() {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setColor(new Color(0x10151c));
        g.fillRect(0, 0, WIDTH, HEIGHT);
        for (int row = 0; row < 5; row++) {
            drawPortrait(g, topPicks[row], portraitX(), portraitY(true, row));
            drawPortrait(g, bottomPicks[row], portraitX(), portraitY(false, row));
        }
        for (int slot = 0; slot < 5; slot++) {
            drawBan(g, topBans[slot], banX(slot), banY(true, slot));
            drawBan(g, bottomBans[slot], banX(slot), banY(false, slot));
        }
        g.dispose();
        try {
            return FakeDataDragonSource.png(image);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** 아이콘 중앙 약 84%를 원형으로 잘라 그리고 금색 원 테두리를 두른다. */
    private static void drawPortrait(Graphics2D g, int championIndex, int x, int y) {
        BufferedImage icon = FakeDataDragonSource.iconImage(championIndex);
        g.setColor(new Color(0x1a1f27));
        g.fillRect(x, y, PORTRAIT_SIZE, PORTRAIT_SIZE);
        g.setClip(new Ellipse2D.Double(x, y, PORTRAIT_SIZE, PORTRAIT_SIZE));
        g.drawImage(icon, x, y, x + PORTRAIT_SIZE, y + PORTRAIT_SIZE, 10, 10, 110, 110, null);
        g.setClip(null);
        g.setColor(new Color(0xc8aa6e));
        g.setStroke(new BasicStroke(3));
        g.drawOval(x + 1, y + 1, PORTRAIT_SIZE - 3, PORTRAIT_SIZE - 3);
    }

    /** 아이콘 전체를 어둡게 하고 회색 테두리와 사선을 덧그린다. */
    private static void drawBan(Graphics2D g, int championIndex, int x, int y) {
        BufferedImage icon = FakeDataDragonSource.iconImage(championIndex);
        g.drawImage(icon, x, y, BAN_SIZE, BAN_SIZE, null);
        g.setColor(new Color(0, 0, 0, 150));
        g.fillRect(x, y, BAN_SIZE, BAN_SIZE);
        g.setStroke(new BasicStroke(3));
        g.setColor(new Color(0x5b5a56));
        g.drawRect(x + 1, y + 1, BAN_SIZE - 3, BAN_SIZE - 3);
        g.setStroke(new BasicStroke(2));
        g.setColor(new Color(0xc8c8c8));
        g.drawLine(x + BAN_SIZE - 1, y, x, y + BAN_SIZE - 1);
    }

    // ---------- 좌표 ----------

    private static int portraitX() {
        return 40;
    }

    private static int portraitY(boolean top, int row) {
        return (top ? 80 : 520) + 80 * row;
    }

    private static int banX(int slot) {
        return 400 + (slot % 3) * 64;
    }

    private static int banY(boolean top, int slot) {
        return (top ? 80 : 520) + (slot / 3) * 64;
    }

    private static Box box(int x, int y, int size) {
        return new Box(normalize(y, HEIGHT), normalize(x, WIDTH), normalize(y + size, HEIGHT),
                normalize(x + size, WIDTH));
    }

    private static int normalize(int pixel, int total) {
        return (int) Math.round(pixel * 1000.0 / total);
    }
}
