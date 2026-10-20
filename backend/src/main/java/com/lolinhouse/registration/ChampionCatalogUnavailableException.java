package com.lolinhouse.registration;

/** 역할: 챔피언 카탈로그를 읽을 수 없어 챔피언 id를 검증할 수 없다는 신호. RegistrationController가 503 CHAMPION_CATALOG_UNAVAILABLE로 바꾼다. */
public class ChampionCatalogUnavailableException extends RuntimeException {

    public ChampionCatalogUnavailableException() {
        super("챔피언 카탈로그를 불러올 수 없습니다.");
    }
}
