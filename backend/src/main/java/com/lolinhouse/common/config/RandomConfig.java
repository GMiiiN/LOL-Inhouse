package com.lolinhouse.common.config;

import java.util.random.RandomGenerator;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 역할: 무작위 요소(팀 짜기 결과의 좌우 교체)에 쓰는 RandomGenerator 빈. 테스트는 @Primary 고정 빈으로 바꿔 끼운다.
 * 설명: docs/design/m45-design.md D3
 */
@Configuration(proxyBeanMethods = false)
public class RandomConfig {

    @Bean
    RandomGenerator randomGenerator() {
        return RandomGenerator.getDefault();
    }
}
