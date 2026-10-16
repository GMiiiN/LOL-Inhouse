package com.lolinhouse.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 역할: M1 임시 보안 설정. 헬스체크(/actuator/health/**)와 /actuator/info만 열고 나머지 요청은 모두 거부한다.
 * 실제 담당자 로그인(세션 쿠키 + BCrypt 단일 계정)과 CSRF 정책은 M3에서 만든다(docs/implementation-plan.md).
 * CSRF는 기본값(켜짐)을 그대로 둔다.
 * 학습 노트: docs/study/02-backend-skeleton.md
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                .anyRequest().denyAll());
        return http.build();
    }
}
