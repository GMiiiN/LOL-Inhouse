package com.lolinhouse.session;

import java.time.LocalDate;
import java.util.concurrent.ThreadLocalRandom;

import com.jayway.jsonpath.JsonPath;
import com.lolinhouse.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 역할: 내전 묶음·대결 API(설계 §5)의 상태 코드와 응답 모양을 MockMvc로 검증한다.
 * 묶음 날짜는 UNIQUE라 테스트마다 먼 미래의 임의 날짜를 써서 서로 간섭하지 않게 한다.
 */
@IntegrationTest
@AutoConfigureMockMvc
@WithMockUser(roles = "ADMIN")
class InhouseSessionApiTest {

    @Autowired
    MockMvc mockMvc;

    /** 3000-01-01 이후 임의 날짜(MySQL DATE 범위 안). 다른 테스트와 겹칠 확률은 무시할 수준. */
    static LocalDate randomDate() {
        return LocalDate.of(3000, 1, 1).plusDays(ThreadLocalRandom.current().nextInt(2_000_000));
    }

    ResultActions createSession(String body) throws Exception {
        return mockMvc.perform(post("/api/admin/inhouse-sessions").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    int createSessionId(LocalDate date) throws Exception {
        String body = createSession("{\"startDate\":\"" + date + "\"}").andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    ResultActions createDuel(long sessionId) throws Exception {
        return mockMvc.perform(post("/api/admin/inhouse-sessions/" + sessionId + "/duels").with(csrf()));
    }

    @Test
    @WithAnonymousUser
    void anonymousRequestIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/admin/inhouse-sessions"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void createSessionReturns201WithLocationAndDateString() throws Exception {
        LocalDate date = randomDate();
        createSession("{\"startDate\":\"" + date + "\"}")
                .andExpect(status().isCreated())
                .andExpect(header().string("Location",
                        org.hamcrest.Matchers.matchesPattern("/api/admin/inhouse-sessions/\\d+")))
                .andExpect(jsonPath("$.startDate").value(date.toString()))
                .andExpect(jsonPath("$.duelCount").value(0))
                .andExpect(jsonPath("$.id").isNumber());
    }

    @Test
    void createSessionWithoutOrWithBadDateIsBadRequest() throws Exception {
        createSession("{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("startDate"));
        createSession("{\"startDate\":\"2026-13-45\"}")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        createSession("{\"startDate\":\"어제\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void createSessionOnSameDateIsConflictWithExistingSessionId() throws Exception {
        LocalDate date = randomDate();
        int existing = createSessionId(date);
        createSession("{\"startDate\":\"" + date + "\"}")
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("SESSION_DATE_CONFLICT"))
                .andExpect(jsonPath("$.existingSessionId").value(existing));
    }

    @Test
    void listIsSortedByStartDateDescendingWithDuelCount() throws Exception {
        LocalDate older = randomDate();
        LocalDate newer = older.plusDays(1);
        int olderId = createSessionId(older);
        int newerId = createSessionId(newer);
        createDuel(olderId).andExpect(status().isCreated());
        createDuel(olderId).andExpect(status().isCreated());

        String list = mockMvc.perform(get("/api/admin/inhouse-sessions")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        java.util.List<Integer> ids = JsonPath.read(list, "$[*].id");
        assertThat(ids.indexOf(newerId)).isNotNegative().isLessThan(ids.indexOf(olderId));
        java.util.List<Integer> olderCount = JsonPath.read(list, "$[?(@.id==" + olderId + ")].duelCount");
        java.util.List<Integer> newerCount = JsonPath.read(list, "$[?(@.id==" + newerId + ")].duelCount");
        assertThat(olderCount).containsExactly(2);
        assertThat(newerCount).containsExactly(0);
        java.util.List<String> dates = JsonPath.read(list, "$[*].startDate");
        assertThat(dates).isSortedAccordingTo(java.util.Comparator.reverseOrder());
    }

    @Test
    void getSessionShowsDuelsInSeqOrderAndUnknownIs404() throws Exception {
        LocalDate date = randomDate();
        int id = createSessionId(date);
        createDuel(id);
        createDuel(id);
        mockMvc.perform(get("/api/admin/inhouse-sessions/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startDate").value(date.toString()))
                .andExpect(jsonPath("$.duels.length()").value(2))
                .andExpect(jsonPath("$.duels[0].seq").value(1))
                .andExpect(jsonPath("$.duels[1].seq").value(2));
        mockMvc.perform(get("/api/admin/inhouse-sessions/999999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SESSION_NOT_FOUND"));
    }

    @Test
    void createDuelIncrementsSeqPerSessionAndReturnsLocation() throws Exception {
        int first = createSessionId(randomDate());
        int second = createSessionId(randomDate());
        createDuel(first)
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.matchesPattern("/api/admin/duels/\\d+")))
                .andExpect(jsonPath("$.seq").value(1))
                .andExpect(jsonPath("$.sessionId").value(first));
        createDuel(first).andExpect(jsonPath("$.seq").value(2));
        createDuel(first).andExpect(jsonPath("$.seq").value(3));
        // 다른 묶음은 1부터 다시 시작한다
        createDuel(second).andExpect(jsonPath("$.seq").value(1));
    }

    @Test
    void createDuelInUnknownSessionIs404() throws Exception {
        createDuel(999999999L)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SESSION_NOT_FOUND"));
    }

    @Test
    void getDuelReturnsItAndUnknownIs404() throws Exception {
        int sessionId = createSessionId(randomDate());
        String duel = createDuel(sessionId).andReturn().getResponse().getContentAsString();
        int duelId = JsonPath.read(duel, "$.id");
        mockMvc.perform(get("/api/admin/duels/" + duelId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(duelId))
                .andExpect(jsonPath("$.sessionId").value(sessionId))
                .andExpect(jsonPath("$.seq").value(1));
        mockMvc.perform(get("/api/admin/duels/999999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DUEL_NOT_FOUND"));
    }

    @Test
    void postWithoutCsrfIsForbidden() throws Exception {
        mockMvc.perform(post("/api/admin/inhouse-sessions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startDate\":\"" + randomDate() + "\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
    }
}
