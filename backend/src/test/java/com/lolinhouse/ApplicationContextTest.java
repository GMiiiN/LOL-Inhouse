package com.lolinhouse;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 역할: 진짜 MySQL 컨테이너(Testcontainers)로 앱 전체 컨텍스트를 띄우고 /actuator/health가 200 + UP인지 확인한다.
 * Flyway 실행 → JPA validate → 헬스(DB 포함)까지 한 번에 검증하는 M1의 스모크 테스트.
 */
@IntegrationTest
@AutoConfigureMockMvc
class ApplicationContextTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void healthEndpointReturnsUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
