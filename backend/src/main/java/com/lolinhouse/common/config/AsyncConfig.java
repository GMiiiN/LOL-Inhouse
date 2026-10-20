package com.lolinhouse.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 역할: 비동기 추출 실행기 설정. 스레드 1개 + 대기열 2개라 Gemini 무료 등급 한도를 넘겨 몰아 보내지 않고,
 * 가득 차면 TaskRejectedException이 나서 작업을 FAILED(BUSY)로 처리한다. 종료할 때는 진행 중 작업을 최대 30초 기다리며,
 * 끝나지 못한 작업은 다음 기동의 복구 단계가 정리한다. @Async("extractionExecutor")는 별도 빈(ExtractionRunner)에 단다.
 * 설명: docs/design/m4-design.md D3, §2.4, §11.2-3
 */
@Configuration(proxyBeanMethods = false)
@EnableAsync
public class AsyncConfig {

    public static final String EXTRACTION_EXECUTOR = "extractionExecutor";

    @Bean(name = EXTRACTION_EXECUTOR)
    ThreadPoolTaskExecutor extractionExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(2);
        executor.setThreadNamePrefix("extraction-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        return executor;
    }
}
