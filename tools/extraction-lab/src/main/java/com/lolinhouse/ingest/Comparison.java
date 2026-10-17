package com.lolinhouse.ingest;

import com.fasterxml.jackson.databind.JsonNode;
import java.text.Normalizer;
import java.util.*;

/** 정답지를 추출기와 분리해 평가한다 */
public final class Comparison {
    /** 누락도 분모에 포함한다 */
    public record Metric(int correct,int total) {
        public double percent() { return total==0?0:100.0*correct/total; }
    }
    private final Map<String,Metric> counts=new LinkedHashMap<>();
    /** 필드별 측정값을 반환한다 */
    public Map<String,Metric> metrics() { return Collections.unmodifiableMap(counts); }
    private void add(String name,boolean correct) {
        var old=counts.getOrDefault(name,new Metric(0,0));
        counts.put(name,new Metric(old.correct()+(correct?1:0),old.total()+1));
    }
    private void equal(String name,JsonNode expected,JsonNode actual) {
        if(expected.isMissingNode() || expected.isNull()) return;
        add(name,expected.equals(actual));
    }
    private static String normalized(String value) {
        return Normalizer.normalize(value,Normalizer.Form.NFKC).replaceAll("\\s+","").replaceAll("(…|\\.\\.\\.)$","");
    }
    /** 말줄임일 때만 최소 70% 접두부 일치를 허용한다 */
    public static boolean prefix(String actual,String expected) {
        String a=normalized(actual),e=normalized(expected);
        if(a.equals(e)) return true;
        boolean truncated=actual.endsWith("…") || actual.endsWith("...");
        return truncated && !a.isEmpty() && e.startsWith(a)
                && a.codePointCount(0,a.length())>=0.7*e.codePointCount(0,e.length());
    }
    /** 한 행을 화면 순서로 비교한다 */
    public void row(JsonNode expected,JsonNode actual) {
        equal("닉네임 정확",expected.path("nick"),actual.path("nickname"));
        if(expected.path("nick").isTextual())
            add("닉네임 접두부 정규화",actual.path("nickname").isTextual()
                    && prefix(actual.path("nickname").asText(),expected.path("nick").asText()));
        String[] fields={"kills","deaths","assists"}, names={"킬","데스","어시스트"};
        for(int i=0;i<3;i++) equal(names[i],expected.path("kda").path(i),actual.path(fields[i]));
        if(expected.path("kda").isArray()) {
            boolean complete=true;
            for(int i=0;i<3;i++) complete &= expected.path("kda").path(i).equals(actual.path(fields[i]));
            add("K/D/A 전체",complete);
        }
        equal("CS",expected.path("cs"),actual.path("cs"));
        equal("골드",expected.path("gold"),actual.path("gold"));
    }
    /** 실패한 이미지도 모든 정답 필드의 분모에 포함한다 */
    public void sample(JsonNode expected,JsonNode actual) {
        equal("상단 승패",expected.path("result"),actual.path("result"));
        equal("날짜",expected.path("date"),actual.path("date"));
        equal("경기 시간",expected.path("duration"),actual.path("duration"));
        for(String side:List.of("top","bottom")) {
            JsonNode e=expected.path(side),a=actual.path(side);
            String[] fields={"kills","deaths","assists"},names={"팀 킬","팀 데스","팀 어시스트"};
            for(int i=0;i<3;i++) equal(names[i],e.path("kda").path(i),a.path(fields[i]));
            equal("팀 골드",e.path("gold"),a.path("gold"));
            for(int i=0;i<e.path("rows").size();i++) row(e.path("rows").path(i),a.path("rows").path(i));
        }
    }
    /** 팀 합계와 행 합계가 다르거나 누락되면 경고한다 */
    public static List<String> teamWarnings(JsonNode team,String side) {
        var warnings=new ArrayList<String>();
        for(String field:List.of("kills","deaths","assists")) {
            int sum=0; boolean complete=team.path(field).isIntegralNumber();
            for(JsonNode row:team.path("rows")) {
                complete &= row.path(field).isIntegralNumber();
                sum+=row.path(field).asInt();
            }
            if(!complete || sum!=team.path(field).asInt())
                warnings.add(side+" "+field+" 팀 합계 불일치 또는 누락");
        }
        return warnings;
    }
}
