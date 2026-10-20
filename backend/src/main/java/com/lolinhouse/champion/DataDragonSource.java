package com.lolinhouse.champion;

import java.io.IOException;

/**
 * 역할: Data Dragon(챔피언 목록·아이콘) 바이트를 가져오는 경계. 운영 구현은 HTTP(HttpDataDragonSource, 작업자 A),
 * 테스트는 Map 기반 가짜. 캐시 정책은 이 인터페이스 밖(카탈로그)에서 한다.
 * 설명: docs/design/m4-design.md §2.2
 */
public interface DataDragonSource {

    /**
     * 기준 주소(https://ddragon.leagueoflegends.com) 이하의 경로를 가져온다. 예: "/api/versions.json",
     * "/cdn/16.20.1/data/ko_KR/champion.json", "/cdn/16.20.1/img/champion/Ahri.png".
     *
     * @throws IOException 네트워크 오류, 200이 아닌 응답
     */
    byte[] fetch(String path) throws IOException;
}
