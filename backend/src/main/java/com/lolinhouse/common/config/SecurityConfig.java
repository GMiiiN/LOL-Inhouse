package com.lolinhouse.common.config;

import com.lolinhouse.auth.AuthJsonResponder;
import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

/**
 * 역할: 담당자 로그인(세션 쿠키 + BCrypt) · CSRF(XSRF-TOKEN 쿠키 + X-XSRF-TOKEN 헤더) · 인가 규칙 · JSON 오류 응답을 묶는 보안 필터 체인.
 * 인가 순서(위에서부터 처음 맞는 규칙): health/info 공개 → GET /api/csrf 공개 → 티어리스트·점수표 조회·팀 짜기 계산 공개(M4.5) → 공개 조회 GET(M5) → 로그인 공개 → /api/admin/** ADMIN → 오류 디스패치 공개 → 나머지 전부 거부.
 * M5 공개 조회는 GET만 연다(같은 경로의 다른 메서드는 거부). 원본 결과창 이미지는 /api/admin/sets/{id}/image라 담당자 전용이다. 설명: ADR 007
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, AuthJsonResponder responder,
            @Value("${COOKIE_SECURE:false}") boolean cookieSecure) throws Exception {
        CookieCsrfTokenRepository csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        // 쿠키 속성: JS가 읽어야 하므로 HttpOnly 아님(withHttpOnlyFalse). SameSite=Lax, Secure는 운영 HTTPS에서만.
        csrfRepository.setCookieCustomizer(cookie -> cookie.sameSite("Lax").secure(cookieSecure));

        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/csrf").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/tier-list", "/api/score-table").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/records", "/api/records/export", "/api/duels/*",
                                "/api/sets/*", "/api/players", "/api/players/*", "/api/rankings",
                                "/api/champion-stats", "/api/champions", "/api/home", "/api/duos").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/team-maker/auto", "/api/team-maker/manual").permitAll()
                        .requestMatchers("/api/admin/login").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .anyRequest().denyAll())
                // spa(): 쿠키 저장소 + 헤더로 온 토큰은 그대로 비교하는 핸들러. 저장소만 SameSite/Secure를 위해 교체한다.
                .csrf(csrf -> csrf.spa().csrfTokenRepository(csrfRepository))
                .formLogin(form -> form
                        // 기본 HTML 로그인 페이지를 끄기 위한 값. 이 경로는 프런트(React) 화면이고 백엔드는 만들지 않는다.
                        .loginPage("/admin/login")
                        .loginProcessingUrl("/api/admin/login")
                        .successHandler(responder)
                        .failureHandler(responder))
                .logout(logout -> logout
                        .logoutRequestMatcher(PathPatternRequestMatcher.withDefaults()
                                .matcher(HttpMethod.POST, "/api/admin/logout"))
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(responder)
                        .accessDeniedHandler(responder))
                .requestCache(cache -> cache.disable())
                .httpBasic(AbstractHttpConfigurer::disable);
        return http.build();
    }

    @Bean
    BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
