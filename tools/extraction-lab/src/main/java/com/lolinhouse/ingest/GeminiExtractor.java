package com.lolinhouse.ingest;

import com.fasterxml.jackson.databind.*;
import com.google.genai.Client;
import com.google.genai.types.*;
import java.util.*;

/** 구조화 출력과 박스만 모델에 요청한다 */
public final class GeminiExtractor implements ScoreboardExtractor,AutoCloseable {
    private final Client client;
    private final String model;
    private final ObjectMapper mapper=new ObjectMapper();
    public GeminiExtractor(String key,String model) {
        client=Client.builder().apiKey(key).httpOptions(HttpOptions.builder()
                .timeout(120_000).retryOptions(HttpRetryOptions.builder().attempts(1).build()).build())
                .build();this.model=model;
    }
    private static Map<String,Object> integer() { return Map.of("type","integer","minimum",0); }
    private static Map<String,Object> object(Map<String,Object> properties) {
        return Map.of("type","object","properties",properties,"required",new ArrayList<>(properties.keySet()));
    }
    private static Map<String,Object> array(Object items,int size) {
        return Map.of("type","array","items",items,"minItems",size,"maxItems",size);
    }
    /** 좌표는 공식 문서의 [ymin,xmin,ymax,xmax], 0~1000이다 */
    public static Map<String,Object> schema() {
        var box=array(Map.of("type","integer","minimum",0,"maximum",1000),4);
        var row=object(Map.of("nickname",Map.of("type","string"),"kills",integer(),"deaths",integer(),
                "assists",integer(),"cs",integer(),"gold",integer(),"portraitBox",box));
        var team=object(Map.of("kills",integer(),"deaths",integer(),"assists",integer(),
                "gold",integer(),"rows",array(row,5),"banBoxes",array(box,5)));
        return object(Map.of("result",Map.of("type","string","enum",List.of("승리","패배")),
                "date",Map.of("type","string"),"duration",Map.of("type","string"),"top",team,"bottom",team));
    }
    /** 정답값을 프롬프트에 넣지 않는다 */
    @Override public JsonNode extract(byte[] png) throws Exception {
        String prompt="""
                롤 사용자 설정 게임의 결과창이다. 화면에 보이는 값만 읽고 JSON으로 반환하라.
                top은 위쪽 팀, bottom은 아래쪽 팀이다. rows는 각 팀 화면 순서 그대로 5명이다.
                result는 화면 상단 승리/패배이며 위쪽 팀 결과다.
                date는 YYYY/MM/DD, duration은 경기 소요 시간 mm:ss이며 시각이 아니다.
                nickname은 보이는 문자 그대로, 공백과 일본어·한자와 끝의 …도 유지하라.
                kills,deaths,assists,cs,gold는 정수다. 쉼표는 제거하라.
                팀 kills,deaths,assists,gold는 팀 이름 옆에 표시된 합계다. 행을 더해서 대신 쓰지 마라.
                portraitBox는 챔피언 얼굴 초상화만 꼭 맞게 둘러싸라. 레벨·룬·아이템을 제외하라.
                banBoxes는 각 팀 '선택 금지 + 목표물' 영역에서 사선 있는 작은 회색 챔피언
                밴 아이콘 5개다. 위쪽 줄 왼쪽부터 3개, 그 아래 줄 왼쪽부터 2개 순서다.
                목표물 아이콘과 혼동하지 말고 밴 얼굴만 꼭 맞게 둘러싸라.
                모든 박스는 원본 전체 이미지 기준 [ymin,xmin,ymax,xmax], 0~1000 정규화 정수다.
                이미지 크기와 잘린 여백이 달라져도 실제 위치를 찾아라. 챔피언 이름은 추측하지 마라.
                """;
        var config=GenerateContentConfig.builder().responseMimeType("application/json")
                .responseJsonSchema(schema()).temperature(0.0f).build();
        var response=client.models.generateContent(model,
                Content.fromParts(Part.fromText(prompt),Part.fromBytes(png,"image/png")),config);
        String text=response.text();
        if(text==null || text.isBlank()) throw new IllegalStateException("구조화 응답 없음");
        JsonNode data=mapper.readTree(text);
        validate(data);
        return data;
    }
    /** 잘못된 응답을 정상 초안으로 취급하지 않는다 */
    public static void validate(JsonNode data) {
        if(!List.of("승리","패배").contains(data.path("result").asText())
                || !isRealDate(data.path("date").asText())
                || !data.path("duration").asText().matches("[0-9]{2,3}:[0-5][0-9]"))
            throw new IllegalArgumentException("경기 필드 형식 오류");
        for(String side:List.of("top","bottom")) {
            var team=data.path(side);
            // 원소 5개짜리 "객체"도 size()==5가 되므로 배열인지 먼저 확인한다(Codex 리뷰)
            if(!team.path("rows").isArray() || team.path("rows").size()!=5
                    || !team.path("banBoxes").isArray() || team.path("banBoxes").size()!=5)
                throw new IllegalArgumentException("팀별 행 또는 밴 개수 오류");
            numbers(team,List.of("kills","deaths","assists","gold"));
            for(var row:team.path("rows")) {
                if(!row.path("nickname").isTextual() || row.path("nickname").asText().isBlank())
                    throw new IllegalArgumentException("닉네임 누락");
                numbers(row,List.of("kills","deaths","assists","cs","gold"));
                box(row.path("portraitBox"));
            }
            for(var box:team.path("banBoxes")) box(box);
        }
    }
    /** YYYY/MM/DD 형식이면서 실제로 있는 날짜인지. 2026/02/31 같은 값은 정규식만으로는 통과하므로 달력으로 확인한다. */
    static boolean isRealDate(String text) {
        try {
            java.time.LocalDate.parse(text, java.time.format.DateTimeFormatter.ofPattern("uuuu/MM/dd")
                    .withResolverStyle(java.time.format.ResolverStyle.STRICT));
            return true;
        } catch(java.time.format.DateTimeParseException e) {
            return false;
        }
    }
    private static void numbers(JsonNode node,List<String> fields) {
        for(String field:fields) if(!node.path(field).canConvertToInt()
                || !node.path(field).isIntegralNumber() || node.path(field).asInt()<0)
            throw new IllegalArgumentException("숫자 형식 오류");
    }
    private static void box(JsonNode node) {
        if(!node.isArray() || node.size()!=4) throw new IllegalArgumentException("박스 형식 오류");
        int[] values=new int[4];
        for(int i=0;i<4;i++) {
            // canConvertToInt 없이 asInt()를 쓰면 4294967296 같은 큰 수가 0으로 잘려 범위 검사를 통과한다(Codex 리뷰)
            if(!node.path(i).isIntegralNumber() || !node.path(i).canConvertToInt()
                    || node.path(i).asInt()<0 || node.path(i).asInt()>1000)
                throw new IllegalArgumentException("박스 범위 오류");
            values[i]=node.path(i).asInt();
        }
        Boxes.pixels(values,1000,1000);
    }
    /** SDK 연결을 닫는다 */
    @Override public void close() { client.close(); }
}
