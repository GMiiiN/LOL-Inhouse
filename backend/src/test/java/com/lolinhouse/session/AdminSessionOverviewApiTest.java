package com.lolinhouse.session;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import com.jayway.jsonpath.JsonPath;
import com.lolinhouse.IntegrationTest;
import com.lolinhouse.domain.DuelTeam;
import com.lolinhouse.domain.Side;
import com.lolinhouse.session.GameSet.DisplayedTotals;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 역할: 관리자 묶음 목록의 카운트(duelCount·setCount·activeSetCount)와 상세의 대결별 세트 요약(source·deleted·정렬)을 검증한다. 날짜는 2150~2159년만 쓴다. */
@IntegrationTest
@AutoConfigureMockMvc
@WithMockUser(roles = "ADMIN")
class AdminSessionOverviewApiTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    GameSetRepository gameSets;

    static LocalDate randomDate() {
        return LocalDate.of(2150, 1, 1).plusDays(ThreadLocalRandom.current().nextInt(3650));
    }

    int createSessionId(LocalDate date) throws Exception {
        String body = mockMvc.perform(post("/api/admin/inhouse-sessions").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"startDate\":\"" + date + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    long createDuelId(int sessionId) throws Exception {
        String body = mockMvc.perform(post("/api/admin/inhouse-sessions/" + sessionId + "/duels").with(csrf()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    /** withImage=false면 가져온 세트(IMPORT, 이미지 키 없음), true면 결과창 세트(IMAGE). 지문 중복을 피하려 골드를 무작위로 둔다. */
    GameSet saveSet(long duelId, int setNo, boolean withImage, boolean deleted, int team1Kills, int team2Kills) {
        String hash = withImage ? "a".repeat(60) + String.format("%04d", ThreadLocalRandom.current().nextInt(10000)) : null;
        String key = withImage ? "k/" + ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE) + ".png" : null;
        Integer duration = withImage ? ThreadLocalRandom.current().nextInt(1, 2_000_000_000) : null;
        GameSet set = GameSet.create(duelId, setNo, Side.RED, DuelTeam.TEAM2, DuelTeam.TEAM1,
                LocalDate.of(2150, 3, 4), duration,
                new DisplayedTotals(team1Kills, 1, 1, ThreadLocalRandom.current().nextInt(1, 900_000)),
                new DisplayedTotals(team2Kills, 1, 1, ThreadLocalRandom.current().nextInt(1, 900_000)), hash, key);
        if (deleted) {
            set.markDeleted();
        }
        return gameSets.saveAndFlush(set);
    }

    @Test
    void listHasDuelSetAndActiveSetCountsAndIsNewestFirst() throws Exception {
        LocalDate older = randomDate();
        int olderId = createSessionId(older);
        int newerId = createSessionId(older.plusDays(1));
        long duelA = createDuelId(olderId);
        long duelB = createDuelId(olderId);
        saveSet(duelA, 1, true, false, 10, 20);
        saveSet(duelA, 2, false, true, 11, 21);
        saveSet(duelB, 1, false, false, 12, 22);

        String list = mockMvc.perform(get("/api/admin/inhouse-sessions")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<Integer> ids = JsonPath.read(list, "$[*].id");
        assertThat(ids.indexOf(newerId)).isNotNegative().isLessThan(ids.indexOf(olderId));
        assertThat(JsonPath.<List<Integer>>read(list, "$[?(@.id==" + olderId + ")].duelCount")).containsExactly(2);
        assertThat(JsonPath.<List<Integer>>read(list, "$[?(@.id==" + olderId + ")].setCount")).containsExactly(3);
        assertThat(JsonPath.<List<Integer>>read(list, "$[?(@.id==" + olderId + ")].activeSetCount")).containsExactly(2);
        assertThat(JsonPath.<List<Integer>>read(list, "$[?(@.id==" + newerId + ")].setCount")).containsExactly(0);
        assertThat(JsonPath.<List<Integer>>read(list, "$[?(@.id==" + newerId + ")].activeSetCount")).containsExactly(0);
    }

    @Test
    void detailListsSetsPerDuelBySetNoWithSourceAndDeletedFlag() throws Exception {
        int id = createSessionId(randomDate());
        long duelA = createDuelId(id);
        long duelB = createDuelId(id);
        createDuelId(id);
        saveSet(duelA, 2, true, false, 30, 40);
        GameSet imported = saveSet(duelA, 1, false, false, 5, 6);
        saveSet(duelA, 3, false, true, 7, 8);
        saveSet(duelB, 1, true, false, 1, 2);

        mockMvc.perform(get("/api/admin/inhouse-sessions/" + id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.duels.length()").value(3))
                .andExpect(jsonPath("$.duels[0].seq").value(1))
                .andExpect(jsonPath("$.duels[0].sets.length()").value(3))
                .andExpect(jsonPath("$.duels[0].sets[0].setNo").value(1))
                .andExpect(jsonPath("$.duels[0].sets[0].setId").value(imported.id()))
                .andExpect(jsonPath("$.duels[0].sets[0].source").value("IMPORT"))
                .andExpect(jsonPath("$.duels[0].sets[0].durationSec").doesNotExist())
                .andExpect(jsonPath("$.duels[0].sets[0].team1Side").value("RED"))
                .andExpect(jsonPath("$.duels[0].sets[0].winnerTeam").value("TEAM2"))
                .andExpect(jsonPath("$.duels[0].sets[0].shotDate").value("2150-03-04"))
                .andExpect(jsonPath("$.duels[0].sets[0].team1Kills").value(5))
                .andExpect(jsonPath("$.duels[0].sets[0].team2Kills").value(6))
                .andExpect(jsonPath("$.duels[0].sets[0].deleted").value(false))
                .andExpect(jsonPath("$.duels[0].sets[0].createdAt").isNotEmpty())
                .andExpect(jsonPath("$.duels[0].sets[1].setNo").value(2))
                .andExpect(jsonPath("$.duels[0].sets[1].source").value("IMAGE"))
                .andExpect(jsonPath("$.duels[0].sets[1].durationSec").isNumber())
                .andExpect(jsonPath("$.duels[0].sets[2].setNo").value(3))
                .andExpect(jsonPath("$.duels[0].sets[2].deleted").value(true))
                .andExpect(jsonPath("$.duels[1].sets.length()").value(1))
                .andExpect(jsonPath("$.duels[1].sets[0].source").value("IMAGE"))
                .andExpect(jsonPath("$.duels[2].sets.length()").value(0));
    }
}
