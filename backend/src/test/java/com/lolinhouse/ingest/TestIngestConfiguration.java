package com.lolinhouse.ingest;

import java.nio.file.Path;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistrar;

/**
 * 역할: 통합 테스트(@IntegrationTest)에서 외부 서비스를 가짜로 바꾸는 설정. Gemini 추출기와 Data Dragon을 @Primary 가짜 빈으로 두고,
 * 이미지 저장소·Data Dragon 캐시 경로를 빌드 디렉터리 밑 작업 폴더(시스템 속성 app.test.work-dir, build.gradle이 전달)로 돌려
 * 개발자의 실제 data/ 폴더를 건드리지 않고 작업자별 빌드 디렉터리(-PbuildDirName)끼리도 섞이지 않게 한다.
 *
 * 공유 빈 규칙: 가짜 빈은 컨텍스트 캐시 때문에 모든 통합 테스트가 하나를 같이 쓴다. 시나리오·호출 횟수·오프라인 모드를 바꾼 테스트는
 * 반드시 @AfterEach에서 FakeScoreboardExtractor.reset(), FakeDataDragonSource.reset()을 부른다(안 하면 다음 테스트가 오염된다).
 * 설명: docs/design/m4-design.md §7, §11.2-10
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestIngestConfiguration {

    /** 테스트 작업 폴더. Gradle 밖(IDE)에서 돌려도 동작하도록 기본값을 둔다. */
    public static Path workDir() {
        return Path.of(System.getProperty("app.test.work-dir", "build/test-work")).toAbsolutePath();
    }

    @Bean
    @Primary
    FakeScoreboardExtractor fakeScoreboardExtractor() {
        return new FakeScoreboardExtractor();
    }

    @Bean
    @Primary
    FakeDataDragonSource fakeDataDragonSource() {
        return new FakeDataDragonSource();
    }

    @Bean
    DynamicPropertyRegistrar ingestTestPaths() {
        return registry -> {
            registry.add("app.image-store.local-dir", () -> workDir().resolve("images").toString());
            registry.add("app.ddragon.cache-dir", () -> workDir().resolve("ddragon").toString());
        };
    }
}
