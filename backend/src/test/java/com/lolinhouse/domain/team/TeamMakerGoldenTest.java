package com.lolinhouse.domain.team;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import com.lolinhouse.domain.Position;
import com.lolinhouse.domain.tier.LaneTier;
import com.lolinhouse.domain.tier.ScoreTable;
import com.lolinhouse.domain.tier.TierSub;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

/**
 * 역할: 아티팩트 원본 JS로 만든 골든 픽스처(golden-v2.json) 410개와 TeamMaker 결과가 완전히 같은지 대조한다.
 * 교체(swaps) 시나리오는 점수행렬만으로 슬롯을 복원해 재계산하고, total은 비교에서 뺀다(설계 §11 P2-6).
 */
class TeamMakerGoldenTest {

    private static final String RESOURCE = "/team-maker/golden-v2.json";

    private static JsonNode scenarios() throws IOException {
        try (InputStream in = TeamMakerGoldenTest.class.getResourceAsStream(RESOURCE)) {
            return JsonMapper.builder().build().readTree(in).get("scenarios");
        }
    }

    @Test
    void 골든은_410개다() throws IOException {
        assertThat(scenarios().size()).isEqualTo(410);
    }

    @TestFactory
    Stream<DynamicTest> 시나리오마다_원본과_완전히_같다() throws IOException {
        List<DynamicTest> tests = new ArrayList<>();
        for (JsonNode scenario : scenarios()) {
            tests.add(dynamicTest(scenario.get("id").asText(), () -> verify(scenario)));
        }
        return tests.stream();
    }

    private static void verify(JsonNode sc) {
        Map<Long, TeamMakerPlayer> players = new java.util.LinkedHashMap<>();
        for (JsonNode p : sc.get("players")) {
            Map<Position, LaneTier> lanes = new EnumMap<>(Position.class);
            for (Position position : Position.values()) {
                JsonNode lane = p.get("lanes").get(position.name());
                if (lane != null && !lane.isNull()) {
                    lanes.put(position, new LaneTier(lane.get("tier").asInt(), TierSub.valueOf(lane.get("sub").asText())));
                }
            }
            players.put(p.get("id").asLong(), new TeamMakerPlayer(p.get("id").asLong(), lanes));
        }
        ScoreTable table = table(sc.get("scoreTable"));
        Set<TogetherPair> together = new HashSet<>();
        for (JsonNode pair : sc.get("together")) {
            together.add(new TogetherPair(pair.get(0).asLong(), pair.get(1).asLong()));
        }

        TeamMakeResult result;
        if ("auto".equals(sc.get("mode").asText())) {
            List<TeamMakerPlayer> ordered = new ArrayList<>();
            sc.get("participantIds").forEach(id -> ordered.add(players.get(id.asLong())));
            Map<Long, Position> fixedLanes = new java.util.HashMap<>();
            JsonNode fixed = sc.get("fixedLanes");
            if (fixed != null) {
                fixed.properties().forEach(e -> fixedLanes.put(Long.parseLong(e.getKey()), Position.valueOf(e.getValue().asText())));
            }
            result = TeamMaker.auto(ordered, together, table, fixedLanes);
        } else {
            Map<Position, List<TeamMakerPlayer>> picks = new EnumMap<>(Position.class);
            for (Position position : Position.values()) {
                JsonNode pair = sc.get("picks").get(position.name());
                picks.put(position, List.of(players.get(pair.get(0).asLong()), players.get(pair.get(1).asLong())));
            }
            result = TeamMaker.manual(picks, together, table);
        }

        JsonNode expected = sc.get("expected");
        if (!expected.get("ok").asBoolean()) {
            assertThat(result.isSuccess()).as("실패여야 함").isFalse();
            boolean hasFix = sc.get("fixedLanes") != null && !sc.get("fixedLanes").isEmpty();
            String code = result.failure() == TeamMakeResult.Failure.NO_VALID_SPLIT
                    ? (hasFix ? "TEAM_FIXED_LANE_BLOCKED" : "TEAM_NO_VALID_SPLIT")
                    : "TEAM_PAIR_UNSATISFIABLE";
            assertThat(code).isEqualTo(expected.get("failure").asText());
            return;
        }
        assertThat(result.isSuccess()).as("성공이어야 함").isTrue();
        JsonNode options = expected.get("options");
        assertThat(result.options()).hasSize(options.size());
        for (int i = 0; i < options.size(); i++) {
            assertOption(result.options().get(i), options.get(i), true, "방안 " + i);
        }

        ScoreMatrix matrix = ScoreMatrix.of(players.values(), table);
        assertMatrix(matrix, sc.get("scoreMatrix"));

        JsonNode swaps = sc.get("swaps");
        if (swaps != null) {
            replaySwaps(result.options().get(swaps.get("optionIndex").asInt()), swaps.get("steps"), matrix);
        }
    }

    private static ScoreTable table(JsonNode json) {
        List<Integer> base = new ArrayList<>();
        for (int tier = 1; tier <= 6; tier++) {
            base.add(json.get("base").get(String.valueOf(tier)).asInt());
        }
        Map<TierSub, Integer> sub = new EnumMap<>(TierSub.class);
        for (TierSub s : TierSub.values()) {
            sub.put(s, json.get("sub").get(s.name()).asInt());
        }
        Map<Position, Integer> lane = new EnumMap<>(Position.class);
        for (Position p : Position.values()) {
            lane.put(p, json.get("lane").get(p.name()).asInt());
        }
        return new ScoreTable(base, sub, lane);
    }

    private static void assertOption(TeamOption actual, JsonNode expected, boolean compareTotal, String label) {
        assertSlots(actual.blue(), expected.get("blue"), label + " blue");
        assertSlots(actual.red(), expected.get("red"), label + " red");
        assertThat(actual.blueSum()).as(label + " blueSum").isEqualTo(expected.get("blueSum").asInt());
        assertThat(actual.redSum()).as(label + " redSum").isEqualTo(expected.get("redSum").asInt());
        assertThat(actual.diff()).as(label + " diff").isEqualTo(expected.get("diff").asInt());
        assertThat(actual.gap()).as(label + " gap").isEqualTo(expected.get("gap").asInt());
        assertThat(actual.cost()).as(label + " cost").isEqualTo(expected.get("cost").asInt());
        if (compareTotal) {
            assertThat(actual.total()).as(label + " total").isEqualTo(expected.get("total").asInt());
        } else {
            assertThat(actual.total()).as(label + " total = 합계").isEqualTo(actual.blueSum() + actual.redSum());
        }
    }

    private static void assertSlots(List<Slot> actual, JsonNode expected, String label) {
        assertThat(actual).as(label).hasSize(expected.size());
        for (int i = 0; i < expected.size(); i++) {
            JsonNode e = expected.get(i);
            Slot a = actual.get(i);
            assertThat(a.playerId()).as(label + "[" + i + "].playerId").isEqualTo(e.get("playerId").asLong());
            assertThat(a.position().name()).as(label + "[" + i + "].position").isEqualTo(e.get("position").asText());
            assertThat(a.score()).as(label + "[" + i + "].score").isEqualTo(e.get("score").asInt());
            if (e.get("tier").isNull()) {
                assertThat(a.tier()).as(label + "[" + i + "].tier").isNull();
            } else {
                assertThat(a.tier()).as(label + "[" + i + "].tier")
                        .isEqualTo(new LaneTier(e.get("tier").asInt(), TierSub.valueOf(e.get("sub").asText())));
            }
        }
    }

    private static void assertMatrix(ScoreMatrix matrix, JsonNode expected) {
        assertThat(matrix.cells()).hasSize(expected.size());
        for (Map.Entry<Long, Map<Position, ScoreMatrix.Cell>> row : matrix.cells().entrySet()) {
            JsonNode expectedRow = expected.get(String.valueOf(row.getKey()));
            for (Position position : Position.values()) {
                JsonNode e = expectedRow.get(position.name());
                ScoreMatrix.Cell cell = row.getValue().get(position);
                assertThat(cell.score()).as("행렬 점수").isEqualTo(e.get("score").asInt());
                assertThat(cell.hasTier()).as("행렬 hasTier").isEqualTo(e.get("hasTier").asBoolean());
                if (cell.hasTier()) {
                    assertThat(cell.tier()).isEqualTo(
                            new LaneTier(e.get("tier").asInt(), TierSub.valueOf(e.get("sub").asText())));
                }
            }
        }
    }

    /** 두 사람이 자리를 바꾸고 각자 새 라인의 점수를 점수행렬에서 찾아 다시 계산한다(프런트와 같은 방식). */
    private static void replaySwaps(TeamOption start, JsonNode steps, ScoreMatrix matrix) {
        List<Slot> blue = new ArrayList<>(start.blue());
        List<Slot> red = new ArrayList<>(start.red());
        int n = 0;
        for (JsonNode step : steps) {
            List<Slot> sideA = "blue".equals(step.get("a").get("side").asText()) ? blue : red;
            List<Slot> sideB = "blue".equals(step.get("b").get("side").asText()) ? blue : red;
            int ia = step.get("a").get("i").asInt();
            int ib = step.get("b").get("i").asInt();
            long playerA = sideA.get(ia).playerId();
            long playerB = sideB.get(ib).playerId();
            sideA.set(ia, slotFrom(matrix, playerB, Position.values()[ia]));
            sideB.set(ib, slotFrom(matrix, playerA, Position.values()[ib]));

            int blueSum = blue.stream().mapToInt(Slot::score).sum();
            int redSum = red.stream().mapToInt(Slot::score).sum();
            int diff = Math.abs(blueSum - redSum);
            int gap = 0;
            for (int i = 0; i < 5; i++) {
                gap = Math.max(gap, Math.abs(blue.get(i).score() - red.get(i).score()));
            }
            TeamOption replayed = new TeamOption(blue, red, blueSum, redSum, diff, gap, diff * 3 + gap,
                    blueSum + redSum);
            assertOption(replayed, step.get("result"), false, "교체 " + n++);
        }
    }

    private static Slot slotFrom(ScoreMatrix matrix, long playerId, Position position) {
        ScoreMatrix.Cell cell = matrix.cells().get(playerId).get(position);
        return new Slot(playerId, position, cell.tier(), cell.score());
    }
}
