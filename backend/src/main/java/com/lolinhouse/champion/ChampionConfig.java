package com.lolinhouse.champion;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 역할: Data Dragon 요청에 쓰는 java.net.http.HttpClient 빈. 여러 요청이 연결을 재사용하고, 컨텍스트가 닫힐 때
 * Spring이 close()를 불러 연결·스레드를 정리한다(Java 21 HttpClient는 AutoCloseable).
 * 설명: docs/design/m4-design.md §2.2
 */
@Configuration(proxyBeanMethods = false)
public class ChampionConfig {

    @Bean
    HttpClient dataDragonHttpClient() {
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
    }
}
