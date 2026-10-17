package com.lolinhouse.ingest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;

/**
 * 오프라인 매칭 튜닝. Gemini를 다시 부르지 않고, 지난 `./gradlew run`에서 잘라 둔 out/crops 이미지를
 * 여러 비교 설정으로 공식 아이콘과 맞춰 보고 정답지(samples/ground-truth.json) 기준 정확도를 출력한다.
 * 실행: ./gradlew tune  ·
 */
public final class MatchTuner {

    record Trial(String name, IconMatcher.Settings settings) {}

    private MatchTuner() {}

    public static void main(String[] args) throws Exception {
        Path root = Path.of("").toAbsolutePath();
        while (!Files.exists(root.resolve("CLAUDE.md"))) root = root.getParent();
        JsonNode truth = new ObjectMapper().readTree(root.resolve("samples/ground-truth.json").toFile());
        Path crops = root.resolve("tools/extraction-lab/out/crops");
        IconMatcher matcher = new IconMatcher(loadIcons(root));

        // 실험한 설정들. 결과는 학습 노트 05에 표로 정리했다.
        List<Trial> pickTrials = List.of(
                new Trial("픽: 그대로", new IconMatcher.Settings(0, 0, false, false, 0.7)),
                new Trial("픽: 아이콘 8% 확대", new IconMatcher.Settings(0, 0.08, false, false, 0.7)),
                new Trial("픽: 원형 · 아이콘 8% (채택)", IconMatcher.PICK));
        List<Trial> banTrials = List.of(
                new Trial("밴: 그대로 · 사선 무시", new IconMatcher.Settings(0, 0, false, true, 1.0)),
                new Trial("밴: 테두리 8%", new IconMatcher.Settings(0.08, 0, false, true, 1.0)),
                new Trial("밴: 테두리 10%", new IconMatcher.Settings(0.10, 0, false, true, 1.0)),
                new Trial("밴: 테두리 12% (채택)", IconMatcher.BAN),
                new Trial("밴: 테두리 14%", new IconMatcher.Settings(0.14, 0, false, true, 1.0)),
                new Trial("밴: 테두리 12% · 사선 포함", new IconMatcher.Settings(0.12, 0, false, false, 1.0)));

        for (Trial t : pickTrials) report(t, matcher, truth, crops, false);
        for (Trial t : banTrials) report(t, matcher, truth, crops, true);
    }

    /** 한 설정으로 모든 샘플을 맞춰 보고 "1위 일치 수"와 "정답이 몇 위였는지" 분포를 출력한다. */
    private static void report(Trial t, IconMatcher matcher, JsonNode truth, Path crops, boolean ban) throws Exception {
        int total = 0;
        int[] rankCount = new int[4]; // 1위, 2위, 3위, 4위 이하
        List<String> unknown = new ArrayList<>();
        for (JsonNode sample : truth.path("samples")) {
            // 순번이 아니라 정답지의 파일 이름으로 crop을 찾는다(Lab이 crop 이름을 파일 이름 기준으로 붙임)
            String stem = Lab.stem(Path.of(sample.path("file").asText()));
            for (String side : List.of("top", "bottom")) {
                for (int i = 0; i < 5; i++) {
                    JsonNode answer = ban ? sample.path(side).path("bans").get(i)
                            : sample.path(side).path("rows").get(i).path("champion");
                    if (answer == null || answer.isNull()) continue;
                    String file = stem + "-" + side + "-" + (ban ? "ban" : "row") + "-" + (i + 1) + ".png";
                    BufferedImage crop = ImageIO.read(crops.resolve(file).toFile());
                    List<String> ranked = matcher.rank(crop, t.settings()).stream().map(IconMatcher.Candidate::id).toList();
                    int rank = ranked.indexOf(answer.asText()) + 1;
                    if (rank == 0) { // 정답 id가 아이콘 목록에 없음: 정답지 오타이거나 아이콘 누락
                        unknown.add(answer.asText());
                        continue;
                    }
                    rankCount[Math.min(rank, 4) - 1]++;
                    total++;
                }
            }
        }
        System.out.printf("%-28s 1위 %2d/%d · 3위 안 %2d/%d%s%n", t.name(), rankCount[0], total,
                rankCount[0] + rankCount[1] + rankCount[2], total,
                unknown.isEmpty() ? "" : " · 아이콘 목록에 없는 정답 " + unknown);
    }

    private static List<IconMatcher.Icon> loadIcons(Path root) throws Exception {
        Path iconDir;
        try (var versions = Files.list(root.resolve("tools/extraction-lab/cache/ddragon"))) {
            iconDir = versions.filter(Files::isDirectory).max(Comparator.naturalOrder()).orElseThrow();
        }
        List<IconMatcher.Icon> icons = new ArrayList<>();
        try (var files = Files.list(iconDir)) {
            for (Path p : files.filter(f -> f.toString().endsWith(".png")).sorted().toList()) {
                String id = p.getFileName().toString().replace(".png", "");
                icons.add(new IconMatcher.Icon(id, id, ImageIO.read(p.toFile())));
            }
        }
        return icons;
    }
}
