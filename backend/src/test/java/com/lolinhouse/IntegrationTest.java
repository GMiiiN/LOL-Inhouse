package com.lolinhouse;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * 역할: 앱 전체 컨텍스트 + Testcontainers MySQL + 테스트 전용 담당자 계정 속성을 한 번에 붙이는 메타 어노테이션.
 * 같은 설정을 쓰는 테스트끼리 Spring이 컨텍스트와 컨테이너를 재사용해 느려지지 않는다.
 * 비밀번호는 테스트용 더미 값이다(실제 계정 아님).
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@SpringBootTest(properties = {
		"app.admin.username=test-admin",
		"app.admin.password=test-password-1234"
})
@Import(TestcontainersConfiguration.class)
public @interface IntegrationTest {
}
