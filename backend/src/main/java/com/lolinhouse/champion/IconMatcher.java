package com.lolinhouse.champion;

import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.Comparator;
import java.util.List;

/**
 * 역할: 결과창에서 잘라낸 초상화·밴 아이콘을 Data Dragon 공식 아이콘과 비교해 챔피언 후보를 고른다(M0 실험 코드 이식).
 * 픽과 밴은 화면에 그려지는 모양이 달라서 비교 설정(Settings)을 따로 쓴다. 공식 아이콘은 생성할 때 설정별로 한 번만 줄여 둔다
 * (매 호출마다 다시 줄이면 아이콘 수 x 칸 수만큼 낭비). 점수가 같으면 id 순으로 정렬해 결과가 결정적이다.
 * 설정값을 정한 근거(샘플 3장 실측): docs/adr/003-extraction-approach.md. 설명: docs/design/m4-design.md §2.2
 */
public final class IconMatcher implements ChampionIconMatcher {

    /** 비교할 때 양쪽 이미지를 줄이는 크기(픽셀). */
    static final int SIZE = 48;

    /** 돌려줄 상위 후보 수. */
    static final int TOP_COUNT = 3;

    /** 공식 챔피언 아이콘 한 장. key=숫자 키, id=영문 식별자(예: Ahri). */
    public record Icon(String key, String id, BufferedImage image) {
    }

    /**
     * 비교 설정.
     *
     * @param cropInset   잘라낸 이미지 가장자리를 걷어낼 비율(밴 아이콘의 사각 테두리 제거용)
     * @param iconInset   공식 아이콘 가장자리를 걷어낼 비율(결과창 초상화가 아이콘 가운데를 확대해 보여주므로)
     * @param circle      원 밖은 비교하지 않음(초상화의 금색 원형 테두리 제거용)
     * @param slash       오른쪽 위→왼쪽 아래 사선 띠를 비교하지 않음(밴 표시 사선)
     * @param colorWeight 색 차이 비중(나머지는 명암 패턴 비중)
     */
    public record Settings(double cropInset, double iconInset, boolean circle, boolean slash, double colorWeight) {
    }

    /** 픽 초상화: 샘플 3장에서 29/29 일치(MatchTuner 실측, 2026-10-16). */
    public static final Settings PICK = new Settings(0.0, 0.08, true, false, 0.7);

    /** 밴 아이콘: 샘플 3장에서 20/30 일치, 정답이 3위 안에 23/30(MatchTuner 실측, 2026-10-16). */
    public static final Settings BAN = new Settings(0.12, 0.0, false, true, 1.0);

    private final List<Icon> icons;
    /** icons와 같은 순서. PICK/BAN 설정으로 미리 줄여 둔 공식 아이콘. */
    private final List<BufferedImage> preparedForPick;
    private final List<BufferedImage> preparedForBan;

    public IconMatcher(List<Icon> icons) {
        if (icons.size() < 2) {
            throw new IllegalArgumentException("아이콘이 두 개 이상 필요합니다");
        }
        this.icons = List.copyOf(icons);
        this.preparedForPick = this.icons.stream().map(icon -> prepare(icon.image(), PICK.iconInset())).toList();
        this.preparedForBan = this.icons.stream().map(icon -> prepare(icon.image(), BAN.iconInset())).toList();
    }

    /** 잘라낸 이미지 하나를 모든 공식 아이콘과 비교해 상위 3개와 1·2순위 점수 차이(margin)를 돌려준다. */
    @Override
    public IconMatch top(BufferedImage crop, boolean ban) {
        List<IconMatch.Candidate> ranked = rank(crop, ban);
        List<IconMatch.Candidate> top = ranked.subList(0, Math.min(TOP_COUNT, ranked.size()));
        double margin = ranked.size() < 2 ? 0 : ranked.get(0).score() - ranked.get(1).score();
        return new IconMatch(top, margin, false);
    }

    /** 모든 후보를 유사한 순서(점수 내림차순, 같으면 id 오름차순)로 정렬한다. */
    List<IconMatch.Candidate> rank(BufferedImage crop, boolean ban) {
        Settings settings = ban ? BAN : PICK;
        List<BufferedImage> prepared = ban ? preparedForBan : preparedForPick;
        BufferedImage cropPrepared = prepare(crop, settings.cropInset());
        List<IconMatch.Candidate> candidates = new java.util.ArrayList<>(icons.size());
        for (int i = 0; i < icons.size(); i++) {
            double score = Math.max(0, 1 - distance(cropPrepared, prepared.get(i), settings));
            candidates.add(new IconMatch.Candidate(icons.get(i).id(), score));
        }
        candidates.sort(Comparator.comparingDouble(IconMatch.Candidate::score).reversed()
                .thenComparing(IconMatch.Candidate::id));
        return candidates;
    }

    /** 가장자리를 inset 비율만큼 걷어내고 SIZE x SIZE로 줄인다. */
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
     * 두 이미지(이미 prepare된 SIZE x SIZE)의 차이. 0이면 같다.
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
                if (ignored(x, y, s)) {
                    continue;
                }
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
                if (ignored(x, y, s)) {
                    continue;
                }
                int rgb = img.getRGB(x, y);
                for (int c = 0; c < 3; c++) {
                    m[c] += channel(rgb, c);
                }
                n++;
            }
        }
        for (int c = 0; c < 3; c++) {
            m[c] /= n;
        }
        return m;
    }

    /** c=0 빨강, 1 초록, 2 파랑을 0~1로. */
    private static double channel(int rgb, int c) {
        return ((rgb >> (16 - 8 * c)) & 255) / 255.0;
    }

    private static boolean ignored(int x, int y, Settings s) {
        double center = (SIZE - 1) / 2.0;
        if (s.circle() && Math.hypot(x - center, y - center) > SIZE * 0.45) {
            return true;
        }
        // 밴 사선은 오른쪽 위에서 왼쪽 아래로 그어져 있다: x + y ≈ SIZE - 1 근처 띠를 제외
        return s.slash() && Math.abs(x + y - (SIZE - 1)) < SIZE * 0.08;
    }
}
