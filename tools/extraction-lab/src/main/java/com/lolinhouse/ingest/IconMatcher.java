package com.lolinhouse.ingest;

import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.Comparator;
import java.util.List;

/**
 * 결과창에서 잘라낸 초상화·밴 아이콘을 Data Dragon 공식 아이콘과 비교해 챔피언을 고른다.
 * 픽과 밴은 화면에 그려지는 모양이 달라서 비교 설정(Settings)을 따로 쓴다.
 * 설정값을 정한 근거(샘플 3장 실측): docs/study/05-extraction-lab.md, docs/adr/003-extraction-approach.md
 */
public final class IconMatcher {
    /** 비교할 때 양쪽 이미지를 줄이는 크기(픽셀). */
    static final int SIZE = 48;

    /** 점수는 확률이 아니며 클수록 유사하다. */
    public record Candidate(String championKey, String id, double score) {}

    /** 1순위·2순위와 그 점수 차이(margin). 차이가 작을수록 애매한 판정이다. */
    public record Match(Candidate best, Candidate runnerUp, double margin) {}

    /** 공식 챔피언 아이콘 한 장. key=숫자 키, id=영문 식별자(예: Ahri). */
    public record Icon(String key, String id, BufferedImage image) {}

    /**
     * 비교 설정.
     * @param cropInset   잘라낸 이미지 가장자리를 걷어낼 비율(밴 아이콘의 사각 테두리 제거용)
     * @param iconInset   공식 아이콘 가장자리를 걷어낼 비율(결과창 초상화가 아이콘 가운데를 확대해 보여주므로)
     * @param circle      원 밖은 비교하지 않음(초상화의 금색 원형 테두리 제거용)
     * @param slash       오른쪽 위→왼쪽 아래 사선 띠를 비교하지 않음(밴 표시 사선)
     * @param colorWeight 색 차이 비중(나머지는 명암 패턴 비중)
     */
    public record Settings(double cropInset, double iconInset, boolean circle, boolean slash, double colorWeight) {}

    /** 픽 초상화: 샘플 3장에서 29/29 일치(MatchTuner 실측, 2026-10-16). */
    public static final Settings PICK = new Settings(0.0, 0.08, true, false, 0.7);

    /** 밴 아이콘: 샘플 3장에서 20/30 일치, 정답이 3위 안에 23/30(MatchTuner 실측, 2026-10-16). */
    public static final Settings BAN = new Settings(0.12, 0.0, false, true, 1.0);

    private final List<Icon> icons;

    public IconMatcher(List<Icon> icons) {
        if (icons.size() < 2) throw new IllegalArgumentException("아이콘이 두 개 이상 필요합니다");
        this.icons = List.copyOf(icons);
    }

    /** 잘라낸 이미지 하나를 모든 공식 아이콘과 비교해 1·2순위를 돌려준다. ban이면 BAN 설정, 아니면 PICK 설정. */
    public Match match(BufferedImage crop, boolean ban) {
        List<Candidate> ranked = rank(crop, ban ? BAN : PICK);
        return new Match(ranked.get(0), ranked.get(1), ranked.get(0).score() - ranked.get(1).score());
    }

    /** 모든 후보를 유사한 순서로 정렬한다. 확인 화면에서 상위 후보 몇 개를 보여줄 때 쓴다. */
    public List<Candidate> rank(BufferedImage crop, Settings settings) {
        BufferedImage prepared = prepare(crop, settings.cropInset());
        return icons.stream()
                .map(icon -> new Candidate(icon.key(), icon.id(),
                        Math.max(0, 1 - distance(prepared, prepare(icon.image(), settings.iconInset()), settings))))
                .sorted(Comparator.comparingDouble(Candidate::score).reversed().thenComparing(Candidate::id))
                .toList();
    }

    /** 가장자리를 inset 비율만큼 걷어내고 SIZE×SIZE로 줄인다. */
    static BufferedImage prepare(BufferedImage src, double inset) {
        int dx = (int) Math.round(src.getWidth() * inset);
        int dy = (int) Math.round(src.getHeight() * inset);
        BufferedImage inner = src.getSubimage(dx, dy, src.getWidth() - 2 * dx, src.getHeight() - 2 * dy);
        BufferedImage out = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_RGB);
        var g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(inner, 0, 0, SIZE, SIZE, null);
        g.dispose();
        return out;
    }

    /**
     * 두 이미지(이미 prepare된 SIZE×SIZE)의 차이. 0이면 같다.
     * 밴 아이콘은 어둡게 처리돼 있어서, 밝기 자체가 아니라 "평균에서 얼마나 벗어났는지"를 비교한다(채널별 평균을 뺌).
     */
    static double distance(BufferedImage a, BufferedImage b, Settings s) {
        double[] meanA = mean(a, s);
        double[] meanB = mean(b, s);
        double color = 0;
        double pattern = 0;
        int n = 0;
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                if (ignored(x, y, s)) continue;
                int ca = a.getRGB(x, y);
                int cb = b.getRGB(x, y);
                double grayA = 0;
                double grayB = 0;
                for (int c = 0; c < 3; c++) {
                    double va = channel(ca, c) - meanA[c];
                    double vb = channel(cb, c) - meanB[c];
                    color += (va - vb) * (va - vb);
                    grayA += va;
                    grayB += vb;
                }
                pattern += (grayA - grayB) * (grayA - grayB) / 9;
                n++;
            }
        }
        return s.colorWeight() * color / n + (1 - s.colorWeight()) * pattern / n;
    }

    private static double[] mean(BufferedImage img, Settings s) {
        double[] m = new double[3];
        int n = 0;
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                if (ignored(x, y, s)) continue;
                int rgb = img.getRGB(x, y);
                for (int c = 0; c < 3; c++) m[c] += channel(rgb, c);
                n++;
            }
        }
        for (int c = 0; c < 3; c++) m[c] /= n;
        return m;
    }

    /** c=0 빨강, 1 초록, 2 파랑을 0~1로. */
    private static double channel(int rgb, int c) {
        return ((rgb >> (16 - 8 * c)) & 255) / 255.0;
    }

    private static boolean ignored(int x, int y, Settings s) {
        double center = (SIZE - 1) / 2.0;
        if (s.circle() && Math.hypot(x - center, y - center) > SIZE * 0.45) return true;
        // 밴 사선은 오른쪽 위에서 왼쪽 아래로 그어져 있다: x + y ≈ SIZE - 1 근처 띠를 제외
        return s.slash() && Math.abs(x + y - (SIZE - 1)) < SIZE * 0.08;
    }
}
