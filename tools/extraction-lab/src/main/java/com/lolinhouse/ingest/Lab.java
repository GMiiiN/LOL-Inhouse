package com.lolinhouse.ingest;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import javax.imageio.ImageIO;

/** 샘플 실행·평가·보고서 생성을 조율한다 */
public final class Lab {
    private static final ObjectMapper JSON=new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private Lab() {}
    /** 프로젝트 또는 저장소 루트에서 실행한다 */
    public static void main(String[] args) {
        try { execute(findRoot(Path.of("").toAbsolutePath())); }
        catch(Exception e) {
            System.err.println("실험 실패: "+e.getClass().getSimpleName()+" (비밀값 보호를 위해 상세 예외 생략)");
            System.exit(1);
        }
    }
    private static Path findRoot(Path current) {
        for(Path p=current;p!=null;p=p.getParent())
            if(Files.exists(p.resolve("CLAUDE.md")) && Files.isDirectory(p.resolve("backend"))) return p;
        throw new IllegalArgumentException("저장소 루트를 찾지 못했습니다");
    }
    private static int[] box(JsonNode node) {
        int[] result=new int[4];for(int i=0;i<4;i++)result[i]=node.path(i).asInt();return result;
    }
    /** crop 파일 이름의 앞부분. 정답지의 file(예: 1.png)과 같은 이름(1)을 써서 MatchTuner가 순번이 아니라 파일로 짝을 맞춘다. */
    static String stem(Path file) {
        String name=file.getFileName().toString();
        return name.substring(0,name.lastIndexOf('.'));
    }
    /** 초상화 10개·밴 10개를 잘라 매칭한다. 잘라내기에 실패한 개수를 돌려준다(0이 아니면 실행 상태를 "일부 실패"로). */
    private static int predictions(ObjectNode report,JsonNode data,java.awt.image.BufferedImage image,
                                    IconMatcher matcher,List<String> warnings,Path output,String stem) throws Exception {
        int failures=0;
        var all=report.putArray("predictions");
        for(String side:List.of("top","bottom")) {
            var team=data.path(side);
            for(int i=0;i<10;i++) {
                boolean ban=i>=5;int index=ban?i-5:i;
                var node=ban?team.path("banBoxes").path(index):team.path("rows").path(index).path("portraitBox");
                var entry=all.addObject();
                entry.put("side",side).put("kind",ban?"밴":"선수").put("index",index+1);
                entry.set("box",node);
                try {
                    var crop=Boxes.crop(image,box(node));
                    var match=matcher.match(crop,ban);
                    entry.set("match",JSON.valueToTree(match));
                    String cropFile="crops/"+stem+"-"+side+"-"+(ban?"ban":"row")+"-"+(index+1)+".png";
                    ImageIO.write(crop,"png",output.resolve(cropFile).toFile());
                    entry.put("crop",cropFile);
                } catch(IllegalArgumentException e) {
                    entry.put("error","박스를 자를 수 없음");warnings.add(side+" "+(index+1)+" 박스 오류");
                    failures++;
                }
            }
        }
        return failures;
    }
    private static void execute(Path root) throws Exception {
        Path output=root.resolve("tools/extraction-lab/out");
        Files.createDirectories(output.resolve("crops"));
        var report=JSON.createObjectNode();
        report.put("createdAt",Instant.now().toString());
        report.put("status","실행 중");
        report.put("championAccuracy","챔피언·밴 정답지가 없어 정확도 미측정. 후보와 잘린 아이콘을 사람이 확인해야 합니다.");
        var images=report.putArray("images");
        var comparison=new Comparison();
        boolean failed=false;
        try {
            var settings=Env.load(root);
            String key=Env.choose("GEMINI_API_KEY",settings,System.getenv());
            String model=Env.choose("GEMINI_MODEL",settings,System.getenv());
            report.put("model",model);
            JsonNode truth=JSON.readTree(root.resolve("samples/ground-truth.json").toFile());
            var expected=new HashMap<String,JsonNode>();
            for(var sample:truth.path("samples")) {
                String file=sample.path("file").asText();
                if(expected.put(file,sample)!=null) throw new IllegalArgumentException("정답지 파일 중복");
            }
            List<Path> files;
            try(var stream=Files.list(root.resolve("samples"))) {
                files=stream.filter(p->p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png")).sorted().toList();
            }
            if(files.isEmpty()) throw new IllegalArgumentException("샘플 이미지 없음");
            IconMatcher matcher=null;
            try(DataDragon dragon=new DataDragon(root.resolve("tools/extraction-lab/cache/ddragon"))) {
                matcher=new IconMatcher(dragon.load());
                report.put("dataDragonVersion",dragon.version());
            } catch(Exception e) {
                failed=true;
                report.put("iconError",e.getClass().getSimpleName());
            }
            try(var extractor=new GeminiExtractor(key,model)) {
                int imageNumber=0;
                for(Path file:files) {
                    if(imageNumber>0) Thread.sleep(5_000);
                    imageNumber++;
                    long started=System.nanoTime();
                    var imageReport=images.addObject().put("sample",imageNumber)
                            .put("file",file.getFileName().toString());
                    var warnings=new ArrayList<String>();
                    var retry=new Retry();
                    JsonNode data=MissingNode.getInstance();
                    // 어느 단계에서 실패했는지 남긴다(같은 예외 종류라도 단계로 원인을 구분). SDK 원문 메시지는 키가 섞일 수 있어 저장하지 않는다.
                    String stage="이미지 읽기";
                    try {
                        var image=ImageIO.read(file.toFile());
                        if(image==null) throw new IllegalArgumentException("이미지 형식 오류");
                        stage="Gemini 추출·검증";
                        long apiStarted=System.nanoTime();
                        data=retry.extract(extractor,Files.readAllBytes(file));
                        imageReport.put("extractionLatencyMs",(System.nanoTime()-apiStarted)/1_000_000);
                        imageReport.set("extraction",data);
                        stage="팀 합계 검사";
                        for(String side:List.of("top","bottom"))
                            warnings.addAll(Comparison.teamWarnings(data.path(side),side.equals("top")?"상단":"하단"));
                        stage="아이콘 자르기·매칭";
                        if(matcher!=null) {
                            int cropFailures=predictions(imageReport,data,image,matcher,warnings,output,stem(file));
                            if(cropFailures>0) failed=true;
                        }
                        else warnings.add("공식 아이콘 준비 실패: 챔피언·밴 미측정");
                    } catch(Exception e) {
                        failed=true;
                        imageReport.put("errorStage",stage);
                        imageReport.put("errorType",e.getClass().getSimpleName());
                        warnings.add(data.isMissingNode()?"추출 실패: 이 이미지의 정답 필드는 불일치로 집계"
                                :"후처리 실패: 추출값은 유지하며 아이콘·보고서 오류를 확인해야 합니다");
                    }
                    var sampleTruth=expected.remove(file.getFileName().toString());
                    if(sampleTruth==null) warnings.add("정답지 없음: 정확도 집계 제외");
                    else {
                        comparison.sample(sampleTruth,data);
                        var perImage=new Comparison();perImage.sample(sampleTruth,data);
                        imageReport.set("metrics",JSON.valueToTree(perImage.metrics()));
                    }
                    imageReport.set("attempts",JSON.valueToTree(retry.attempts()));
                    imageReport.put("latencyMs",(System.nanoTime()-started)/1_000_000);
                    imageReport.set("warnings",JSON.valueToTree(warnings));
                    report.set("metrics",JSON.valueToTree(comparison.metrics()));
                    write(output,report);
                    System.out.println("샘플 "+imageNumber+" 처리 완료 (닉네임 출력 생략)");
                }
            }
            for(var missing:expected.values()) {comparison.sample(missing,MissingNode.getInstance());failed=true;}
            report.put("missingSampleCount",expected.size());
            report.set("metrics",JSON.valueToTree(comparison.metrics()));
            report.put("status",failed?"일부 실패":"완료");
        } catch(Exception e) {
            failed=true;
            report.put("status","실행 실패").put("errorStage","준비(설정·정답지·샘플 목록)")
                    .put("errorType",e.getClass().getSimpleName());
        } finally { write(output,report); }
        if(failed) throw new IllegalStateException("보고서에 실패 기록");
    }
    private static void write(Path output,ObjectNode report) throws Exception {
        Files.writeString(output.resolve("report.json"),JSON.writeValueAsString(report));
        StringBuilder text=new StringBuilder("# AI 추출 실험 결과\n\n");
        text.append("- 상태: ").append(report.path("status").asText()).append("\n");
        text.append("- 생성: ").append(report.path("createdAt").asText()).append("\n");
        text.append("- 모델: ").append(report.path("model").asText("미설정")).append("\n");
        text.append("- Data Dragon: ").append(report.path("dataDragonVersion").asText("준비 실패")).append("\n\n");
        text.append(report.path("championAccuracy").asText()).append("\n\n");
        if(report.has("errorType")) text.append("실행 오류 유형: ").append(report.path("errorType").asText()).append("\n\n");
        if(report.has("iconError")) text.append("아이콘 준비 오류 유형: ").append(report.path("iconError").asText()).append("\n\n");
        text.append("| 필드 | 일치/전체 | 정확도 |\n|---|---|---|\n");
        var metrics=report.path("metrics").properties();
        for(var entry:metrics) {
            int c=entry.getValue().path("correct").asInt(),n=entry.getValue().path("total").asInt();
            text.append("| ").append(entry.getKey()).append(" | ").append(c).append("/").append(n)
                .append(" | ").append(String.format(Locale.ROOT,"%.2f%%",n==0?0:100.0*c/n)).append(" |\n");
        }
        text.append("\n실패 이미지도 정답 필드 분모에 포함합니다. 정답 없는 필드는 집계하지 않습니다.\n");
        for(var image:report.path("images")) {
            int retries=Math.max(0,image.path("attempts").size()-1),limited=0;
            for(var attempt:image.path("attempts")) if(attempt.path("httpStatus").asInt()==429)limited++;
            text.append("\n## 샘플 ").append(image.path("sample").asInt()).append("\n\n");
            text.append("- 전체 지연: ").append(image.path("latencyMs").asLong()).append(" ms, API·백오프: ")
                .append(image.path("extractionLatencyMs").asText("실패")).append(" ms\n");
            text.append("- 재시도: ").append(retries).append("회, HTTP 429: ").append(limited).append("회\n");
            if(image.has("errorStage")) text.append("- 실패 단계: ").append(image.path("errorStage").asText())
                    .append(" (").append(image.path("errorType").asText()).append(")\n");
            for(var warning:image.path("warnings")) text.append("- ").append(warning.asText()).append("\n");
            text.append("\n| 구분 | 위치 | 순번 | 1순위 키/식별자 | 유사도 | 2순위 키/식별자 | 유사도 | 간격 | 잘린 아이콘 |\n")
                .append("|---|---|---|---|---|---|---|---|---|\n");
            for(var prediction:image.path("predictions")) {
                var m=prediction.path("match");
                text.append("| ").append(prediction.path("kind").asText()).append(" | ")
                    .append(prediction.path("side").asText().equals("top")?"상단":"하단").append(" | ")
                    .append(prediction.path("index").asInt()).append(" | ")
                    .append(m.path("best").path("championKey").asText()).append("/")
                    .append(m.path("best").path("id").asText()).append(" | ")
                    .append(m.path("best").path("score").asText()).append(" | ")
                    .append(m.path("runnerUp").path("championKey").asText()).append("/")
                    .append(m.path("runnerUp").path("id").asText()).append(" | ")
                    .append(m.path("runnerUp").path("score").asText()).append(" | ")
                    .append(m.path("margin").asText()).append(" | [확인](")
                    .append(prediction.path("crop").asText()).append(") |\n");
            }
        }
        text.append("\n유사도는 확률이 아닙니다. 픽은 원형 마스크·아이콘 8% 확대(IconMatcher.PICK), ")
            .append("밴은 테두리 12% 제거·사선 띠 무시·색 비교(IconMatcher.BAN)를 씁니다.\n")
            .append("챔피언·밴 정확도는 ./gradlew tune으로 확인합니다. 표본이 적어 자동 확정 기준은 아직 정하지 않습니다.\n");
        Files.writeString(output.resolve("report.md"),text.toString());
    }
}
