package com.lolinhouse;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

/**
 * 역할: JPA 슬라이스(엔티티·리포지토리·Flyway만) + 진짜 MySQL 컨테이너. 보안·웹 계층은 올리지 않아 빠르고 다른 작업의 영향을 받지 않는다.
 * replace = NONE: 슬라이스가 기본으로 끼우는 임베디드 DB 대체를 끄고 Testcontainers MySQL을 쓴다.
 * 각 테스트는 트랜잭션 안에서 돌고 끝나면 롤백된다.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
public @interface PersistenceTest {
}
