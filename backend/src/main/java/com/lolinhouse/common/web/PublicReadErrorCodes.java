package com.lolinhouse.common.web;

/**
 * 역할: M5(공개 조회)에서 새로 쓰는 오류 code 목록. 상태 코드는 주석에 적는다(설계 §3.2).
 * 기존 코드(DUEL_NOT_FOUND, PLAYER_NOT_FOUND, CHAMPION_CATALOG_UNAVAILABLE)는 그대로 쓴다.
 */
public final class PublicReadErrorCodes {

    /** 404 없는 세트 또는 삭제된 세트 */
    public static final String SET_NOT_FOUND = "SET_NOT_FOUND";
    /** 404 원본 이미지가 없는 세트(수동 입력) 또는 파일이 없음 */
    public static final String SET_IMAGE_NOT_FOUND = "SET_IMAGE_NOT_FOUND";
    /** 400 from이 to보다 늦음, limit 범위 밖, 모르는 position·sort·championScope 값, 형식이 틀린 날짜 */
    public static final String INVALID_QUERY = "INVALID_QUERY";

    private PublicReadErrorCodes() {
    }
}
