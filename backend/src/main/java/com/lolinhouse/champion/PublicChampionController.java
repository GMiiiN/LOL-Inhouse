package com.lolinhouse.champion;

import com.lolinhouse.common.web.ApiException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 역할: 공개 화면용 챔피언 카탈로그(GET /api/champions, 로그인 불필요). 본문은 담당자용 /api/admin/champions와 같다(Data Dragon 공개 데이터).
 * 카탈로그를 한 번도 읽지 못했으면 503 CHAMPION_CATALOG_UNAVAILABLE이고 프런트는 챔피언 키를 글자로 보여 준다.
 */
@RestController
public class PublicChampionController {

    private final ChampionCatalog championCatalog;

    public PublicChampionController(ChampionCatalog championCatalog) {
        this.championCatalog = championCatalog;
    }

    @GetMapping("/api/champions")
    ChampionListResponse list() {
        return championCatalog.current().map(ChampionListResponse::from).orElseThrow(() -> ApiException
                .serviceUnavailable("CHAMPION_CATALOG_UNAVAILABLE", "챔피언 목록을 아직 불러오지 못했습니다. 잠시 뒤 다시 시도하세요."));
    }
}
