package com.lolinhouse.champion;

import java.util.List;
import java.util.Optional;

import com.lolinhouse.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 챔피언 목록 API: 가짜 카탈로그로 응답 모양, 없을 때 503, 인증을 확인한다. */
@IntegrationTest
@AutoConfigureMockMvc
@WithMockUser(roles = "ADMIN")
class ChampionControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ChampionCatalog catalog;

    @Test
    void returnsVersionAndChampionsWithIconUrls() throws Exception {
        when(catalog.current()).thenReturn(Optional.of(new CatalogSnapshot("16.20.1",
                List.of(new Champion("Ahri", "103", "아리"), new Champion("MonkeyKing", "62", "오공")),
                (crop, ban) -> null)));

        mockMvc.perform(get("/api/admin/champions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value("16.20.1"))
                .andExpect(jsonPath("$.champions.length()").value(2))
                .andExpect(jsonPath("$.champions[0].id").value("Ahri"))
                .andExpect(jsonPath("$.champions[0].key").value("103"))
                .andExpect(jsonPath("$.champions[0].name").value("아리"))
                .andExpect(jsonPath("$.champions[1].iconUrl")
                        .value("https://ddragon.leagueoflegends.com/cdn/16.20.1/img/champion/MonkeyKing.png"));
    }

    @Test
    void returns503WithCodeWhenCatalogIsUnavailable() throws Exception {
        when(catalog.current()).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/admin/champions"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("CHAMPION_CATALOG_UNAVAILABLE"));
    }

    @Test
    @WithAnonymousUser
    void anonymousIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/admin/champions")).andExpect(status().isUnauthorized());
    }
}
