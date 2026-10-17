package com.lolinhouse.ingest;

import com.google.genai.errors.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/** 외부 재시도를 한 곳에서 계측한다 */
public final class Retry {
    /** 응답 본문·예외 메시지는 비밀값 보호를 위해 저장하지 않는다 */
    public record Attempt(int number,Integer httpStatus,String errorType,long latencyMs,long backoffMs) {}
    private final List<Attempt> attempts=new ArrayList<>();
    /** 이번 이미지의 시도 기록이다 */
    public List<Attempt> attempts() { return List.copyOf(attempts); }
    /** 429·일시적 서버 오류만 최대 네 번 호출한다 */
    public JsonNode extract(ScoreboardExtractor extractor,byte[] image) throws Exception {
        for(int number=1;number<=4;number++) {
            long start=System.nanoTime();
            try {
                var result=extractor.extract(image);
                attempts.add(new Attempt(number,null,null,(System.nanoTime()-start)/1_000_000,0));
                return result;
            } catch(Exception e) {
                Integer status=e instanceof ApiException api?api.code():null;
                boolean transientError=status!=null && (status==429 || status==408 || status>=500);
                long delay=transientError && number<4?Math.min(60_000,5_000L*(1L<<(number-1)))
                        +ThreadLocalRandom.current().nextLong(1000):0;
                attempts.add(new Attempt(number,status,e.getClass().getSimpleName(),
                        (System.nanoTime()-start)/1_000_000,delay));
                if(delay==0) throw e;
                Thread.sleep(delay);
            }
        }
        throw new IllegalStateException("재시도 종료");
    }
}
