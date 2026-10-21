package com.lolinhouse.tier;

import java.util.List;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import com.lolinhouse.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 역할: 점수표 API(GET 공개, PUT 수정, POST reset)의 검증과 티어리스트 점수 반영을 확인한다.
 * 점수표는 전역 한 행이라 각 테스트가 끝나면 기본값으로 되돌린다.
 */
@IntegrationTest
@AutoConfigureMockMvc
@WithMockUser(roles = "ADMIN")
class ScoreTableApiTest {

    private static final String DEFAULT_JSON = table("60,52,44,36,28,20", "3,0,-3", "0,0,0,-4,-3");

    @Autowired
    MockMvc mockMvc;

    @AfterEach
    void restoreDefault() throws Exception {
        mockMvc.perform(post("/api/admin/score-table/reset").with(csrf())).andExpect(status().isOk());
    }

    static String table(String base, String sub, String lane) {
        String[] b = base.split(",");
        String[] s = sub.split(",");
        String[] l = lane.split(",");
        return "{\"base\":{\"1\":" + b[0] + ",\"2\":" + b[1] + ",\"3\":" + b[2] + ",\"4\":" + b[3] + ",\"5\":" + b[4]
                + ",\"6\":" + b[5] + "},\"sub\":{\"HIGH\":" + s[0] + ",\"MID\":" + s[1] + ",\"LOW\":" + s[2]
                + "},\"lane\":{\"TOP\":" + l[0] + ",\"JUNGLE\":" + l[1] + ",\"MID\":" + l[2] + ",\"ADC\":" + l[3]
                + ",\"SUPPORT\":" + l[4] + "}}";
    }

    ResultActions putTable(String json) throws Exception {
        return mockMvc.perform(put("/api/admin/score-table").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    @Test
    void 기본_점수표는_isDefault_true이고_기본값을_돌려준다() throws Exception {
        mockMvc.perform(get("/api/score-table"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.base.1").value(60))
                .andExpect(jsonPath("$.base.6").value(20))
                .andExpect(jsonPath("$.sub.HIGH").value(3))
                .andExpect(jsonPath("$.sub.LOW").value(-3))
                .andExpect(jsonPath("$.lane.ADC").value(-4))
                .andExpect(jsonPath("$.lane.SUPPORT").value(-3))
                .andExpect(jsonPath("$.isDefault").value(true));
    }

    @Test
    void 수정하면_저장되고_isDefault가_false가_되며_티어리스트_점수와_함께_바뀐다() throws Exception {
        String real = "점수" + UUID.randomUUID().toString().substring(0, 8);
        String created = mockMvc.perform(post("/api/admin/player-profiles").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(PlayerProfileApiTest.body(real, "\"ADC\":{\"tier\":2,\"sub\":\"LOW\"}",
                        List.of(real + "닉"), "")))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        int id = JsonPath.read(created, "$.id");

        putTable(table("100,90,80,70,60,50", "10,0,-10", "1,2,3,7,5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.base.2").value(90))
                .andExpect(jsonPath("$.isDefault").value(false));

        mockMvc.perform(get("/api/score-table")).andExpect(jsonPath("$.lane.ADC").value(7))
                .andExpect(jsonPath("$.isDefault").value(false));
        String tierList = mockMvc.perform(get("/api/tier-list")).andExpect(status().isOk())
                .andExpect(jsonPath("$.scoreTable.lane.ADC").value(7)).andReturn().getResponse().getContentAsString();
        // 2티어 하 = 90 - 10, ADC 보정 7
        List<Object> scores = JsonPath.read(tierList, "$.players[?(@.id==" + id + ")].lanes.ADC.score");
        assertThat(scores).containsExactly(87);
    }

    @Test
    void 칸_누락_범위_밖_알_수_없는_칸은_400_INVALID_SCORE_TABLE이고_저장되지_않는다() throws Exception {
        String missing = DEFAULT_JSON.replace(",\"6\":20", "");
        String tooBig = table("60,52,44,36,28,1000", "3,0,-3", "0,0,0,-4,-3");
        String tooSmall = table("60,52,44,36,28,20", "3,0,-1000", "0,0,0,-4,-3");
        String unknown = DEFAULT_JSON.replace("\"6\":20", "\"6\":20,\"7\":1");
        String nullCell = DEFAULT_JSON.replace("\"6\":20", "\"6\":null");
        String noLane = "{\"base\":{\"1\":1,\"2\":1,\"3\":1,\"4\":1,\"5\":1,\"6\":1},\"sub\":{\"HIGH\":0,\"MID\":0,\"LOW\":0}}";

        for (String bad : List.of(missing, tooBig, tooSmall, unknown, nullCell, noLane)) {
            putTable(bad).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_SCORE_TABLE"));
        }
        mockMvc.perform(get("/api/score-table")).andExpect(jsonPath("$.isDefault").value(true));
    }

    @Test
    void 경계값_999와_마이너스_999는_저장되고_reset은_기본값으로_되돌린다() throws Exception {
        putTable(table("999,-999,0,0,0,0", "999,0,-999", "0,0,0,0,0")).andExpect(status().isOk())
                .andExpect(jsonPath("$.base.1").value(999)).andExpect(jsonPath("$.base.2").value(-999));

        mockMvc.perform(post("/api/admin/score-table/reset").with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.isDefault").value(true))
                .andExpect(jsonPath("$.base.1").value(60));
    }

    @Test
    void 소수가_들어온_점수표는_400이고_저장값이_유지된다() throws Exception {
        putTable(DEFAULT_JSON.replace("\"1\":60", "\"1\":60.9")).andExpect(status().isBadRequest());
        putTable(DEFAULT_JSON.replace("\"HIGH\":3", "\"HIGH\":3.5")).andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/score-table")).andExpect(jsonPath("$.base.1").value(60))
                .andExpect(jsonPath("$.isDefault").value(true));
    }
}
