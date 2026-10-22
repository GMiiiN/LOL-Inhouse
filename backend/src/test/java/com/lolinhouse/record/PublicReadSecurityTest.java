package com.lolinhouse.record;

import com.lolinhouse.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 역할: M5 공개 GET 경로가 로그인 없이 인가를 통과하고(401·403이 아님), 같은 경로의 쓰기 메서드와 원본 이미지 경로는 막히는지 확인한다.
 * 엔드포인트 본문은 다음 작업자가 만들므로 컨트롤러가 없는 동안은 404, 있으면 200·400 등이 나올 수 있다. 그래서 "401·403이 아님"으로 단정한다.
 */
@IntegrationTest
@AutoConfigureMockMvc
class PublicReadSecurityTest {

    private static final String[] PUBLIC_GET_PATHS = {
            "/api/records", "/api/records/export", "/api/duels/1", "/api/sets/1", "/api/players", "/api/players/1",
            "/api/rankings", "/api/champion-stats", "/api/champions", "/api/home",
            "/api/duos"
    };

    @Autowired
    MockMvc mockMvc;

    @Test
    void 공개_GET은_로그인_없이_인가를_통과한다() throws Exception {
        for (String path : PUBLIC_GET_PATHS) {
            int status = mockMvc.perform(get(path)).andReturn().getResponse().getStatus();
            assertThat(status).as(path).isNotIn(401, 403);
        }
    }

    @Test
    void 공개_경로의_쓰기_메서드는_CSRF가_있어도_거부된다() throws Exception {
        for (String path : PUBLIC_GET_PATHS) {
            assertThat(mockMvc.perform(post(path).with(csrf())).andReturn().getResponse().getStatus())
                    .as("POST " + path).isIn(401, 403);
            assertThat(mockMvc.perform(put(path).with(csrf())).andReturn().getResponse().getStatus())
                    .as("PUT " + path).isIn(401, 403);
            assertThat(mockMvc.perform(delete(path).with(csrf())).andReturn().getResponse().getStatus())
                    .as("DELETE " + path).isIn(401, 403);
        }
    }

    @Test
    void 공개_경로에_하위_경로를_더해_우회할_수_없다() throws Exception {
        for (String path : new String[] {"/api/duels/1/extra", "/api/sets/1/image", "/api/players/1/aliases",
                "/api/records/other"}) {
            int status = mockMvc.perform(get(path)).andReturn().getResponse().getStatus();
            assertThat(status).as(path).isIn(401, 403);
        }
    }

    @Test
    void 원본_이미지는_비로그인이면_401이다() throws Exception {
        mockMvc.perform(get("/api/admin/sets/1/image")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void 원본_이미지_경로는_담당자면_인가를_통과한다() throws Exception {
        int status = mockMvc.perform(get("/api/admin/sets/1/image")).andReturn().getResponse().getStatus();
        assertThat(status).isNotIn(401, 403);
    }

    @Test
    @WithMockUser(roles = "USER")
    void 담당자_권한이_없는_로그인은_원본_이미지를_볼_수_없다() throws Exception {
        mockMvc.perform(get("/api/admin/sets/1/image")).andExpect(status().isForbidden());
    }
}
