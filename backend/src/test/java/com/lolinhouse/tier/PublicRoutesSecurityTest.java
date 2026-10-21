package com.lolinhouse.tier;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.lolinhouse.IntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 역할: M4.5 공개 경로(GET tier-list·score-table, POST team-maker auto·manual)가 로그인 없이 열리고 CSRF는 유지되며,
 * 담당자 경로는 여전히 401인지 확인한다. 컨트롤러가 아직 없는 Phase 0에서는 "공개 = 401·403이 아님"으로 단정한다.
 */
@IntegrationTest
@AutoConfigureMockMvc
class PublicRoutesSecurityTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void 공개_조회는_로그인_없이_인가를_통과한다() throws Exception {
        for (String path : new String[] {"/api/tier-list", "/api/score-table"}) {
            int status = mockMvc.perform(get(path)).andReturn().getResponse().getStatus();
            assertThat(status).as(path).isNotIn(401, 403);
        }
    }

    @Test
    void 공개_계산_POST는_CSRF_토큰이_없으면_403이다() throws Exception {
        for (String path : new String[] {"/api/team-maker/auto", "/api/team-maker/manual"}) {
            mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void 공개_계산_POST는_CSRF_토큰이_있으면_인가를_통과한다() throws Exception {
        for (String path : new String[] {"/api/team-maker/auto", "/api/team-maker/manual"}) {
            int status = mockMvc.perform(post(path).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content("{}")).andReturn().getResponse().getStatus();
            assertThat(status).as(path).isNotIn(401, 403);
        }
    }

    @Test
    void 담당자_경로는_비로그인이면_401이다() throws Exception {
        mockMvc.perform(get("/api/admin/player-profiles")).andExpect(status().isUnauthorized());
    }
}
