package com.lolinhouse.ingest;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import com.lolinhouse.champion.DataDragonSource;
import javax.imageio.ImageIO;

/**
 * 역할: 네트워크 없이 Data Dragon을 흉내 내는 가짜. 색 블록 무늬가 서로 다른 합성 아이콘 N개와 versions.json·champion.json(ko_KR)을 메모리에서 돌려준다.
 * 챔피언 i의 id는 "Champion%02d", 숫자 키는 i+1, 한글 이름은 "챔피언%02d"다(아이콘 무늬는 i에서 결정적으로 만든다).
 * {@link SyntheticScoreboardImage}가 같은 아이콘을 결과창 합성에 쓰므로 매칭 정답이 결정적이다.
 *
 * 공유 빈 규칙: TestIngestConfiguration의 @Primary 빈 하나를 컨텍스트 전체 테스트가 같이 쓴다.
 * 오프라인 모드·호출 횟수를 바꾸는 테스트는 반드시 @AfterEach에서 {@link #reset()}을 부른다.
 * 설명: docs/design/m4-design.md §7
 */
public class FakeDataDragonSource implements DataDragonSource {

    public static final String DEFAULT_VERSION = "16.20.1";
    public static final int DEFAULT_CHAMPION_COUNT = 20;
    public static final int ICON_SIZE = 120;

    private final int championCount;
    private final String version;
    private final AtomicInteger fetchCount = new AtomicInteger();
    private volatile boolean offline;

    public FakeDataDragonSource() {
        this(DEFAULT_CHAMPION_COUNT, DEFAULT_VERSION);
    }

    public FakeDataDragonSource(int championCount, String version) {
        this.championCount = championCount;
        this.version = version;
    }

    public static String championId(int index) {
        return String.format("Champion%02d", index);
    }

    public static String championKey(int index) {
        return String.valueOf(index + 1);
    }

    public static String championName(int index) {
        return String.format("챔피언%02d", index);
    }

    /** index번 챔피언의 합성 아이콘(120x120). 4x4 색 블록 무늬를 index로 결정적으로 만든다. */
    public static BufferedImage iconImage(int index) {
        BufferedImage image = new BufferedImage(ICON_SIZE, ICON_SIZE, BufferedImage.TYPE_INT_RGB);
        Random random = new Random(index * 7919L + 13);
        int block = ICON_SIZE / 4;
        Graphics2D g = image.createGraphics();
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 4; col++) {
                g.setColor(new Color(random.nextInt(256), random.nextInt(256), random.nextInt(256)));
                g.fillRect(col * block, row * block, block, block);
            }
        }
        g.dispose();
        return image;
    }

    public String version() {
        return version;
    }

    public int championCount() {
        return championCount;
    }

    /** true면 모든 fetch가 IOException(네트워크 실패). */
    public void setOffline(boolean offline) {
        this.offline = offline;
    }

    /** fetch 호출 횟수(오프라인으로 실패한 호출 포함). 캐시 재사용·동시 호출 시험에 쓴다. */
    public int fetchCount() {
        return fetchCount.get();
    }

    /** 테스트가 끝날 때 부른다: 오프라인 해제, 호출 횟수 0. */
    public void reset() {
        offline = false;
        fetchCount.set(0);
    }

    @Override
    public byte[] fetch(String path) throws IOException {
        fetchCount.incrementAndGet();
        if (offline) {
            throw new IOException("가짜 Data Dragon: 오프라인");
        }
        if (path.equals("/api/versions.json")) {
            return utf8("[\"" + version + "\"]");
        }
        if (path.equals("/cdn/" + version + "/data/ko_KR/champion.json")) {
            return utf8(championJson());
        }
        String iconPrefix = "/cdn/" + version + "/img/champion/";
        if (path.startsWith(iconPrefix) && path.endsWith(".png")) {
            String id = path.substring(iconPrefix.length(), path.length() - ".png".length());
            for (int i = 0; i < championCount; i++) {
                if (championId(i).equals(id)) {
                    return png(iconImage(i));
                }
            }
        }
        throw new IOException("가짜 Data Dragon: 알 수 없는 경로 " + path);
    }

    private String championJson() {
        List<String> entries = new ArrayList<>();
        for (int i = 0; i < championCount; i++) {
            entries.add("\"" + championId(i) + "\":{\"id\":\"" + championId(i) + "\",\"key\":\"" + championKey(i)
                    + "\",\"name\":\"" + championName(i) + "\"}");
        }
        return "{\"type\":\"champion\",\"version\":\"" + version + "\",\"data\":{" + String.join(",", entries) + "}}";
    }

    private static byte[] utf8(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    static byte[] png(BufferedImage image) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }
}
