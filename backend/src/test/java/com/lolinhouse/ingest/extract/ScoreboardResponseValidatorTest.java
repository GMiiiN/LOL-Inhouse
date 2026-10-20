package com.lolinhouse.ingest.extract;

import com.lolinhouse.ingest.extract.ExtractedScoreboard.TopResult;
import com.lolinhouse.ingest.job.ExtractionErrorCode;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 응답 검증 규칙(순수 함수) 시험. 실험 코드 PureLogicTest의 검증 케이스 + 설계 §7 추가 케이스. */
class ScoreboardResponseValidatorTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private static ObjectNode validResponse() {
        ObjectNode root = MAPPER.createObjectNode().put("result", "승리").put("date", "2026/10/11")
                .put("duration", "18:14");
        for (String side : new String[] {"top", "bottom"}) {
            ObjectNode team = root.putObject(side).put("kills", 1).put("deaths", 1).put("assists", 1).put("gold", 1);
            ArrayNode rows = team.putArray("rows");
            ArrayNode bans = team.putArray("banBoxes");
            for (int i = 0; i < 5; i++) {
                ObjectNode row = rows.addObject().put("nickname", "n" + i).put("kills", 0).put("deaths", 0)
                        .put("assists", 0).put("cs", 0).put("gold", 0);
                row.putArray("portraitBox").add(10).add(10).add(20).add(20);
                bans.addArray().add(10).add(10).add(20).add(20);
            }
        }
        return root;
    }

    private static void assertInvalid(ObjectNode response) {
        assertThatThrownBy(() -> ScoreboardResponseValidator.validate(response.toString()))
                .isInstanceOfSatisfying(ExtractionException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ExtractionErrorCode.EXTRACTION_INVALID_RESPONSE));
    }

    @Test
    void validResponseBecomesTypedScoreboard() throws Exception {
        ExtractedScoreboard result = ScoreboardResponseValidator.validate(validResponse().toString());

        assertThat(result.topResult()).isEqualTo(TopResult.WIN);
        assertThat(result.shotDate()).hasToString("2026-10-11");
        assertThat(result.duration()).isEqualTo("18:14");
        assertThat(result.top().rows()).hasSize(5);
        assertThat(result.bottom().banBoxes()).hasSize(5);
        assertThat(result.top().rows().get(0).portraitBox()).isEqualTo(new Box(10, 10, 20, 20));
    }

    @Test
    void defeatMapsToLoss() throws Exception {
        ExtractedScoreboard result = ScoreboardResponseValidator
                .validate(validResponse().put("result", "패배").toString());
        assertThat(result.topResult()).isEqualTo(TopResult.LOSS);
    }

    @Test
    void nicknameIsKeptExactlyAsRead() throws Exception {
        ObjectNode response = validResponse();
        ((ObjectNode) response.get("top").get("rows").get(0)).put("nickname", " 가나 다…");
        ExtractedScoreboard result = ScoreboardResponseValidator.validate(response.toString());
        assertThat(result.top().rows().get(0).nickname()).isEqualTo(" 가나 다…");
    }

    @Test
    void rejectsNonJsonAndBlank() {
        assertThatThrownBy(() -> ScoreboardResponseValidator.validate("{not json"))
                .isInstanceOf(ExtractionException.class);
        assertThatThrownBy(() -> ScoreboardResponseValidator.validate("  ")).isInstanceOf(ExtractionException.class);
        assertThatThrownBy(() -> ScoreboardResponseValidator.validate("[1,2]")).isInstanceOf(ExtractionException.class);
    }

    @Test
    void rejectsObjectWithFiveEntriesInsteadOfArray() {
        ObjectNode response = validResponse();
        ObjectNode rowsAsObject = ((ObjectNode) response.get("top")).putObject("rows");
        for (int i = 0; i < 5; i++) {
            rowsAsObject.putObject("r" + i);
        }
        assertInvalid(response);
    }

    @Test
    void rejectsWrongRowOrBanCount() {
        ObjectNode response = validResponse();
        ((ArrayNode) response.get("top").get("rows")).remove(0);
        assertInvalid(response);
        ObjectNode bans = validResponse();
        ((ArrayNode) bans.get("bottom").get("banBoxes")).addArray().add(1).add(1).add(2).add(2);
        assertInvalid(bans);
    }

    @Test
    void rejectsHugeNumbersNegativesAndNonIntegers() {
        ObjectNode huge = validResponse();
        ((ArrayNode) huge.get("top").get("banBoxes").get(0)).set(0, MAPPER.getNodeFactory().numberNode(4294967296L));
        assertInvalid(huge);

        ObjectNode hugeKills = validResponse();
        ((ObjectNode) hugeKills.get("top")).put("kills", 4294967296L);
        assertInvalid(hugeKills);

        ObjectNode negative = validResponse();
        ((ObjectNode) negative.get("top").get("rows").get(1)).put("cs", -1);
        assertInvalid(negative);

        ObjectNode fractional = validResponse();
        ((ObjectNode) fractional.get("bottom")).put("gold", 1.5);
        assertInvalid(fractional);
    }

    @Test
    void rejectsImpossibleDatesAndBadDurations() {
        assertInvalid(validResponse().put("date", "2026/02/31"));
        assertInvalid(validResponse().put("date", "2026-10-11"));
        assertInvalid(validResponse().put("duration", "5:07"));
        assertInvalid(validResponse().put("duration", "25:99"));
        assertInvalid(validResponse().put("result", "무승부"));
    }

    @Test
    void rejectsBlankNicknameAndBoxesWithoutArea() {
        ObjectNode blank = validResponse();
        ((ObjectNode) blank.get("top").get("rows").get(0)).put("nickname", "  ");
        assertInvalid(blank);

        ObjectNode outOfRange = validResponse();
        ((ArrayNode) outOfRange.get("top").get("rows").get(0).get("portraitBox")).set(3,
                MAPPER.getNodeFactory().numberNode(1001));
        assertInvalid(outOfRange);

        ObjectNode noArea = validResponse();
        ArrayNode box = (ArrayNode) noArea.get("bottom").get("banBoxes").get(2);
        box.set(2, MAPPER.getNodeFactory().numberNode(10));
        assertInvalid(noArea);
    }

    @Test
    void failureMessageDoesNotContainResponseContent() {
        ObjectNode response = validResponse();
        ((ObjectNode) response.get("top").get("rows").get(0)).put("nickname", "비밀닉네임").put("kills", -5);
        assertThatThrownBy(() -> ScoreboardResponseValidator.validate(response.toString()))
                .hasMessageNotContaining("비밀닉네임");
    }
}
