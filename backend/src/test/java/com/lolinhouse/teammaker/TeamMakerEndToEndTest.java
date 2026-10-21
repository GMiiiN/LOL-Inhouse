package com.lolinhouse.teammaker;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.lolinhouse.IntegrationTest;
import com.lolinhouse.domain.Position;
import com.lolinhouse.domain.tier.LaneTier;
import com.lolinhouse.domain.tier.TierSub;
import com.lolinhouse.player.Player;
import com.lolinhouse.player.PlayerRepository;
import com.lolinhouse.tier.PlayerLaneTier;
import com.lolinhouse.tier.PlayerLaneTierRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 역할: 가짜 없이 실제 도메인 TeamMaker와 실제 DB로 공개 API가 끝까지 이어지는지 확인한다(성공 모양 · 도메인 실패의 422 변환).
 * 계산 값 자체는 도메인 골든 테스트가 보증하므로 여기서는 연결과 모양만 본다.
 */
@IntegrationTest
@AutoConfigureMockMvc
class TeamMakerEndToEndTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    PlayerRepository playerRepository;
    @Autowired
    PlayerLaneTierRepository laneTierRepository;

    long newPlayer(Position... lanes) {
        Player player = playerRepository.save(Player.unregistered("e2e" + UUID.randomUUID().toString().substring(0, 12)));
        for (Position lane : lanes) {
            laneTierRepository.save(PlayerLaneTier.create(player.id(), lane,
                    new LaneTier(1 + (int) (player.id() + lane.ordinal()) % 6, TierSub.values()[lane.ordinal() % 3])));
        }
        return player.id();
    }

    ResultActions postJson(String path, String body) throws Exception {
        return mockMvc.perform(post(path).with(csrf()).with(request -> {
            request.setRemoteAddr("e2e-" + UUID.randomUUID());
            return request;
        }).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void 자동_모드가_실제_계산으로_방안과_scoreMatrix를_돌려준다() throws Exception {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            ids.add(newPlayer(Position.values()));
        }
        postJson("/api/team-maker/auto", "{\"participantIds\":" + ids + ",\"together\":[]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.options.length()").isNotEmpty())
                .andExpect(jsonPath("$.options[0].blue.length()").value(5))
                .andExpect(jsonPath("$.options[0].red.length()").value(5))
                .andExpect(jsonPath("$.options[0].blue[0].position").value("TOP"))
                .andExpect(jsonPath("$.options[0].total").isNumber())
                .andExpect(jsonPath("$.scoreMatrix.length()").value(10))
                .andExpect(jsonPath("$.scoreMatrix['" + ids.get(0) + "'].MID.hasTier").value(true));
    }

    @Test
    void 수동_모드가_실제_계산으로_방안을_돌려준다() throws Exception {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            ids.add(newPlayer(Position.values()));
        }
        StringBuilder picks = new StringBuilder();
        for (Position position : Position.values()) {
            picks.append(picks.length() == 0 ? "" : ",").append('"').append(position).append("\":[")
                    .append(ids.get(2 * position.ordinal())).append(',').append(ids.get(2 * position.ordinal() + 1))
                    .append(']');
        }
        postJson("/api/team-maker/manual", "{\"picks\":{" + picks + "},\"together\":[]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.options[0].blue.length()").value(5))
                .andExpect(jsonPath("$.scoreMatrix.length()").value(10));
    }

    @Test
    void 모두_탑만_하는_10명은_자동_모드에서_422_TEAM_NO_VALID_SPLIT() throws Exception {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            ids.add(newPlayer(Position.TOP));
        }
        postJson("/api/team-maker/auto", "{\"participantIds\":" + ids + ",\"together\":[]}")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("TEAM_NO_VALID_SPLIT"));
    }

    @Test
    void 같은_라인_두_사람을_같은_팀으로_묶으면_수동_모드에서_422_TEAM_PAIR_UNSATISFIABLE() throws Exception {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            ids.add(newPlayer(Position.values()));
        }
        StringBuilder picks = new StringBuilder();
        for (Position position : Position.values()) {
            picks.append(picks.length() == 0 ? "" : ",").append('"').append(position).append("\":[")
                    .append(ids.get(2 * position.ordinal())).append(',').append(ids.get(2 * position.ordinal() + 1))
                    .append(']');
        }
        postJson("/api/team-maker/manual",
                "{\"picks\":{" + picks + "},\"together\":[[" + ids.get(0) + "," + ids.get(1) + "]]}")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("TEAM_PAIR_UNSATISFIABLE"));
    }
}
