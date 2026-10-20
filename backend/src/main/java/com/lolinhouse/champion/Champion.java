package com.lolinhouse.champion;

/**
 * 역할: Data Dragon의 챔피언 한 명. DB의 champion_key에는 {@code id}(예: Ahri, MonkeyKing)를 저장한다.
 * 설명: docs/design/m4-design.md D6
 *
 * @param id   Data Dragon 식별자. 아이콘 URL과 저장 값에 쓴다(영문·숫자만)
 * @param key  Data Dragon 숫자 키(문자열). 저장에는 쓰지 않는다
 * @param name 한국어 이름(ko_KR)
 */
public record Champion(String id, String key, String name) {
}
