package com.lolinhouse.champion;

import java.util.List;
import java.util.Optional;

import com.lolinhouse.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 공개 챔피언 카탈로그 API: 로그인 없이 담당자용과 같은 본문, 카탈로그가 없으면 503. */
@IntegrationTest
@AutoConfigureMockMvc
class PublicChampionControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ChampionCatalog catalog;

    @Test
    void 비로그인도_버전과_챔피언_아이콘_주소를_받는다() throws Exception {
        when(catalog.current()).thenReturn(Optional.of(new CatalogSnapshot("16.20.1",
                List.of(new Champion("Ahri", "103", "아리")), (crop, ban) -> null)));

        mockMvc.perform(get("/api/champions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value("16.20.1"))
                .andExpect(jsonPath("$.champions[0].id").value("Ahri"))
                .andExpect(jsonPath("$.champions[0].name").value("아리"))
                .andExpect(jsonPath("$.champions[0].iconUrl")
                        .value("https://ddragon.leagueoflegends.com/cdn/16.20.1/img/champion/Ahri.png"));
    }

    @Test
    void 카탈로그가_없으면_503_CHAMPION_CATALOG_UNAVAILABLE이다() throws Exception {
        when(catalog.current()).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/champions"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("CHAMPION_CATALOG_UNAVAILABLE"));
    }

    @Test
    void POST는_거부된다() throws Exception {
        mockMvc.perform(post("/api/champions")).andExpect(status().is4xxClientError());
    }
}
