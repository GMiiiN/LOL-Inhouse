package com.lolinhouse.ingest.extract;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.List;

import com.lolinhouse.ingest.extract.ExtractedScoreboard.TopResult;
import com.lolinhouse.ingest.job.ExtractionErrorCode;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 역할: Gemini가 돌려준 JSON 문자열이 약속한 형식인지 검사하고 {@link ExtractedScoreboard}로 바꾸는 순수 함수.
 * 잘못된 응답을 정상 초안으로 취급하지 않는 것이 목적이라, 배열 여부·개수 5·정수(int 범위, 0 이상)·박스 0~1000과 면적·
 * 실제 있는 날짜(STRICT)·mm:ss·닉네임 비어 있음을 모두 확인한다. 실패하면 EXTRACTION_INVALID_RESPONSE.
 * 예외 메시지에는 응답 원문·닉네임을 넣지 않고 어느 부분이 틀렸는지만 고정 문구로 적는다.
 * 설명: docs/design/m4-design.md §2.1, §7
 */
public final class ScoreboardResponseValidator {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("uuuu/MM/dd").withResolverStyle(ResolverStyle.STRICT);
    private static final String DURATION_PATTERN = "[0-9]{2,3}:[0-5][0-9]";
    private static final int TEAM_SIZE = 5;
    private static final int BOX_MAX = 1000;

    private ScoreboardResponseValidator() {
    }

    /** JSON 문자열을 파싱하고 검증한다. */
    public static ExtractedScoreboard validate(String responseText) throws ExtractionException {
        if (responseText == null || responseText.isBlank()) {
            throw invalid("응답이 비어 있습니다.");
        }
        JsonNode root;
        try {
            root = MAPPER.readTree(responseText);
        } catch (JacksonException e) {
            throw invalid("응답이 JSON이 아닙니다.");
        }
        return validate(root);
    }

    /** 이미 파싱한 JSON을 검증한다. */
    public static ExtractedScoreboard validate(JsonNode root) throws ExtractionException {
        if (root == null || !root.isObject()) {
            throw invalid("응답 최상위가 객체가 아닙니다.");
        }
        TopResult topResult = switch (text(root.path("result"))) {
            case "승리" -> TopResult.WIN;
            case "패배" -> TopResult.LOSS;
            default -> throw invalid("경기 결과 필드 오류.");
        };
        LocalDate shotDate = parseDate(text(root.path("date")));
        String duration = text(root.path("duration"));
        if (!duration.matches(DURATION_PATTERN)) {
            throw invalid("경기 시간 필드 오류.");
        }
        return new ExtractedScoreboard(topResult, shotDate, duration, team(root.path("top")),
                team(root.path("bottom")));
    }

    private static ExtractedTeam team(JsonNode team) throws ExtractionException {
        // 원소 5개짜리 "객체"도 size()==5가 되므로 배열인지 먼저 확인한다
        JsonNode rows = team.path("rows");
        JsonNode banBoxes = team.path("banBoxes");
        if (!rows.isArray() || rows.size() != TEAM_SIZE || !banBoxes.isArray() || banBoxes.size() != TEAM_SIZE) {
            throw invalid("팀별 행 또는 밴 개수 오류.");
        }
        List<ExtractedRow> extractedRows = new ArrayList<>();
        for (JsonNode row : rows) {
            JsonNode nickname = row.path("nickname");
            if (!nickname.isString() || nickname.asString().isBlank()) {
                throw invalid("닉네임 누락.");
            }
            extractedRows.add(new ExtractedRow(nickname.asString(), number(row, "kills"), number(row, "deaths"),
                    number(row, "assists"), number(row, "cs"), number(row, "gold"), box(row.path("portraitBox"))));
        }
        List<Box> bans = new ArrayList<>();
        for (JsonNode ban : banBoxes) {
            bans.add(box(ban));
        }
        return new ExtractedTeam(number(team, "kills"), number(team, "deaths"), number(team, "assists"),
                number(team, "gold"), extractedRows, bans);
    }

    /** int 범위의 0 이상 정수. canConvertToInt 없이 asInt()만 쓰면 4294967296 같은 큰 수가 0으로 잘려 통과한다. */
    private static int number(JsonNode node, String field) throws ExtractionException {
        JsonNode value = node.path(field);
        if (!value.isIntegralNumber() || !value.canConvertToInt() || value.asInt() < 0) {
            throw invalid("숫자 형식 오류.");
        }
        return value.asInt();
    }

    /** [ymin, xmin, ymax, xmax]. 각 값 0~1000 정수이고 면적이 있어야 한다. */
    private static Box box(JsonNode node) throws ExtractionException {
        if (!node.isArray() || node.size() != 4) {
            throw invalid("박스 형식 오류.");
        }
        int[] values = new int[4];
        for (int i = 0; i < 4; i++) {
            JsonNode value = node.get(i);
            if (!value.isIntegralNumber() || !value.canConvertToInt() || value.asInt() < 0
                    || value.asInt() > BOX_MAX) {
                throw invalid("박스 범위 오류.");
            }
            values[i] = value.asInt();
        }
        if (values[2] <= values[0] || values[3] <= values[1]) {
            throw invalid("면적 없는 박스.");
        }
        return new Box(values[0], values[1], values[2], values[3]);
    }

    /** YYYY/MM/DD이면서 달력에 있는 날짜. 2026/02/31은 정규식만으로는 통과하므로 STRICT 파서로 확인한다. */
    private static LocalDate parseDate(String text) throws ExtractionException {
        try {
            return LocalDate.parse(text, DATE_FORMAT);
        } catch (DateTimeParseException e) {
            throw invalid("날짜 필드 오류.");
        }
    }

    /** 문자열 노드의 값. 문자열이 아니면 빈 문자열(그러면 아래 검사에서 거절된다). */
    private static String text(JsonNode node) {
        return node.isString() ? node.asString() : "";
    }

    private static ExtractionException invalid(String reason) {
        return new ExtractionException(ExtractionErrorCode.EXTRACTION_INVALID_RESPONSE, "응답 검증 실패: " + reason);
    }
}
