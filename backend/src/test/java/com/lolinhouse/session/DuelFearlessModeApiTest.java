package com.lolinhouse.session;

import java.time.LocalDate;
import java.util.concurrent.ThreadLocalRandom;

import com.jayway.jsonpath.JsonPath;
import com.lolinhouse.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 역할: 대결 생성 API의 피어리스 방식(fearlessMode) 본문·응답·첫 대결 CONTINUE 거절을 검증한다(docs/design/m4-design.md §11.1).
 */
@IntegrationTest
@AutoConfigureMockMvc
@WithMockUser(roles = "ADMIN")
class DuelFearlessModeApiTest {

    @Autowired
    MockMvc mockMvc;

    long createSession() throws Exception {
        LocalDate date = LocalDate.of(3000, 1, 1).plusDays(ThreadLocalRandom.current().nextInt(2_000_000));
        String body = mockMvc.perform(post("/api/admin/inhouse-sessions").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"startDate\":\"" + date + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    ResultActions createDuel(long sessionId, String jsonBody) throws Exception {
        var request = post("/api/admin/inhouse-sessions/" + sessionId + "/duels").with(csrf());
        if (jsonBody != null) {
            request = request.contentType(MediaType.APPLICATION_JSON).content(jsonBody);
        }
        return mockMvc.perform(request);
    }

    @Test
    void 본문이_없으면_RESET이다() throws Exception {
        createDuel(createSession(), null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fearlessMode").value("RESET"));
    }

    @Test
    void 본문이_비어_있거나_방식을_생략해도_RESET이다() throws Exception {
        long sessionId = createSession();
        createDuel(sessionId, "{}").andExpect(status().isCreated()).andExpect(jsonPath("$.fearlessMode").value("RESET"));
    }

    @Test
    void 묶음의_첫_대결에_CONTINUE는_400이다() throws Exception {
        createDuel(createSession(), "{\"fearlessMode\":\"CONTINUE\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FEARLESS_CONTINUE_WITHOUT_PREVIOUS"));
    }

    @Test
    void 이전_대결이_있으면_CONTINUE로_만들고_조회에도_나온다() throws Exception {
        long sessionId = createSession();
        createDuel(sessionId, null).andExpect(status().isCreated());
        String body = createDuel(sessionId, "{\"fearlessMode\":\"CONTINUE\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fearlessMode").value("CONTINUE"))
                .andReturn().getResponse().getContentAsString();
        int duelId = JsonPath.read(body, "$.id");

        mockMvc.perform(get("/api/admin/duels/" + duelId))
                .andExpect(jsonPath("$.fearlessMode").value("CONTINUE"));
        mockMvc.perform(get("/api/admin/inhouse-sessions/" + sessionId))
                .andExpect(jsonPath("$.duels[0].fearlessMode").value("RESET"))
                .andExpect(jsonPath("$.duels[1].fearlessMode").value("CONTINUE"));
    }

    @Test
    void 알_수_없는_방식은_400이다() throws Exception {
        createDuel(createSession(), "{\"fearlessMode\":\"FOO\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }
}
