package com.lolinhouse.ingest.job;

import com.lolinhouse.IntegrationTest;
import com.lolinhouse.ingest.FakeScoreboardExtractor;
import com.lolinhouse.session.Duel;
import com.lolinhouse.session.DuelRepository;
import com.lolinhouse.session.InhouseSessionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 역할: Gemini 키가 비어 있으면(키가 없는 CI·로컬) 업로드가 작업을 만들지 않고 503 EXTRACTION_NOT_CONFIGURED로 끝나는지 확인한다.
 * 키 속성을 빈 값으로 명시해 개발자 PC의 .env 값이 섞이지 않게 한다.
 */
@IntegrationTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "GEMINI_API_KEY=")
class ExtractionNotConfiguredTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    FakeScoreboardExtractor extractor;
    @Autowired
    DuelRepository duels;
    @Autowired
    InhouseSessionRepository sessions;

    @Test
    void uploadWithoutKeyIs503AndCreatesNoJob() throws Exception {
        Duel duel = ExtractionTestSupport.newDuel(sessions, duels);

        mockMvc.perform(multipart("/api/admin/duels/{id}/extractions", duel.id())
                .file(new MockMultipartFile("image", "r.png", "image/png", ExtractionTestSupport.seededPng(1)))
                .param("topSide", "BLUE").param("capturerPosition", "ADC")
                .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("EXTRACTION_NOT_CONFIGURED"));

        assertThat(jdbc.queryForObject("select count(*) from extraction_job where duel_id = ?", Integer.class, duel.id()))
                .isZero();
        assertThat(extractor.calls()).isZero();
    }
}
