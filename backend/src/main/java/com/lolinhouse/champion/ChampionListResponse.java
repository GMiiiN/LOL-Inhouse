package com.lolinhouse.champion;

import java.util.List;

/**
 * 역할: GET /api/admin/champions 응답. 확인 화면의 챔피언 검색·표시용 목록이다.
 * iconUrl은 Data Dragon CDN 주소라 프런트가 직접 불러온다(서버는 아이콘 바이트를 전달하지 않는다).
 * 설명: docs/design/m4-design.md §5
 *
 * @param version   Data Dragon 버전
 * @param champions 한글 이름순
 */
public record ChampionListResponse(String version, List<Item> champions) {

    /** 챔피언 하나. id가 DB champion_key에 저장되는 값이다. */
    public record Item(String id, String key, String name, String iconUrl) {
    }

    static ChampionListResponse from(CatalogSnapshot snapshot) {
        String iconBase = HttpDataDragonSource.BASE_URL + "/cdn/" + snapshot.version() + "/img/champion/";
        return new ChampionListResponse(snapshot.version(), snapshot.champions().stream()
                .map(champion -> new Item(champion.id(), champion.key(), champion.name(),
                        iconBase + champion.id() + ".png"))
                .toList());
    }
}
