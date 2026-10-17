package com.lolinhouse.ingest;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import javax.imageio.ImageIO;

/** 버전별 공식 자료만 캐시한다 */
public final class DataDragon implements AutoCloseable {
    private static final String BASE="https://ddragon.leagueoflegends.com";
    private final Path cache;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
    private final ObjectMapper mapper=new ObjectMapper();
    private String version;
    public DataDragon(Path cache) { this.cache=cache; }
    /** 이번 실행의 버전을 리포트에 남긴다 */
    public String version() { return version; }
    /** 직접 만든 HttpClient의 연결·스레드를 정리한다(Java 21부터 HttpClient가 AutoCloseable). M4에서는 주입받는 구조로 바꾼다. */
    @Override public void close() { http.close(); }
    private byte[] fetch(String url) throws Exception {
        var response=http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(60)).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
        if(response.statusCode()!=200) throw new IllegalStateException("Data Dragon HTTP "+response.statusCode());
        return response.body();
    }
    private byte[] cached(String relative,String url) throws Exception {
        Path path=cache.resolve(relative);
        if(Files.exists(path)) return Files.readAllBytes(path);
        byte[] bytes=fetch(url);
        Files.createDirectories(path.getParent());
        Files.write(path,bytes);
        return bytes;
    }
    /** 매 실행 최신 버전을 조회하고 해당 버전의 목록·아이콘을 재사용한다 */
    public List<IconMatcher.Icon> load() throws Exception {
        version=mapper.readTree(fetch(BASE+"/api/versions.json")).path(0).asText();
        if(!version.matches("[0-9]+\\.[0-9]+\\.[0-9]+")) throw new IllegalStateException("버전 형식 오류");
        var data=mapper.readTree(cached(version+"/champion.json",
                BASE+"/cdn/"+version+"/data/ko_KR/champion.json")).path("data");
        var icons=new ArrayList<IconMatcher.Icon>();
        for(var champion:data) {
            String id=champion.path("id").asText(),key=champion.path("key").asText();
            if(!id.matches("[A-Za-z0-9]+")) throw new IllegalStateException("아이콘 식별자 오류");
            byte[] bytes=cached(version+"/"+id+".png",BASE+"/cdn/"+version+"/img/champion/"+id+".png");
            var image=ImageIO.read(new java.io.ByteArrayInputStream(bytes));
            if(image==null) throw new IllegalStateException("아이콘 이미지 오류");
            icons.add(new IconMatcher.Icon(key,id,image));
        }
        return icons;
    }
}
