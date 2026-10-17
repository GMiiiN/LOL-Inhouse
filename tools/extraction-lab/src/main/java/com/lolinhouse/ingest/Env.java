package com.lolinhouse.ingest;

import java.nio.file.*;
import java.io.IOException;
import java.util.*;

/** 키를 노출하지 않고 설정을 읽는다 */
public final class Env {
    private Env() {}
    /** KEY=VALUE와 따옴표를 해석한다 */
    public static Map<String,String> parse(String source) {
        var result=new HashMap<String,String>();
        for (String line:source.replace("\uFEFF","").split("\\R")) {
            line=line.trim();
            if(line.startsWith("export ")) line=line.substring(7).trim();
            int eq=line.indexOf('=');
            if(eq<1 || line.startsWith("#")) continue;
            String key=line.substring(0,eq).trim(), value=line.substring(eq+1).trim();
            if(!key.matches("[A-Za-z_][A-Za-z0-9_]*")) continue;
            if(value.startsWith("\"") || value.startsWith("'")) {
                char quote=value.charAt(0); int end=value.indexOf(quote,1);
                if(end<0) throw new IllegalArgumentException(".env 따옴표가 닫히지 않았습니다");
                value=value.substring(1,end);
            } else {
                value=value.replaceFirst("\\s+#.*$","").trim();
            }
            result.put(key,value);
        }
        return Map.copyOf(result);
    }
    /** 환경변수를 파일보다 우선한다 */
    public static String choose(String key,Map<String,String> file,Map<String,String> environment) {
        String value=environment.getOrDefault(key,file.get(key));
        if(value==null || value.isBlank()) throw new IllegalArgumentException("필수 설정 누락: "+key);
        return value;
    }
    /** 파일이 없어도 환경변수로 실행한다 */
    public static Map<String,String> load(Path root) throws IOException {
        Path file=root.resolve(".env");
        return Files.exists(file)?parse(Files.readString(file)):Map.of();
    }
}
