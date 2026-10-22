package com.lolinhouse.common.web;

/**
 * 역할: M4.5(라인 티어·점수표·팀 짜기)에서 새로 쓰는 오류 code 목록. 상태 코드는 주석에 적는다(설계 §4.3, §11).
 * 기존 코드(ALIAS_CONFLICT, DUPLICATE_NICKNAME_IN_REQUEST, INVALID_NICKNAME, INVALID_REAL_NAME, REAL_NAME_CANNOT_BE_CLEARED,
 * PLAYER_NOT_FOUND, CONCURRENT_REQUEST_CONFLICT)는 그대로 쓴다. 회차 관련 코드는 없다(Q2).
 */
public final class TierTeamErrorCodes {

    /** 400 티어가 1~6 밖, sub 값 오류, 모르는 라인 */
    public static final String INVALID_LANE_TIER = "INVALID_LANE_TIER";
    /** 400 만들 때 라인이 0개 */
    public static final String LANE_TIER_REQUIRED = "LANE_TIER_REQUIRED";
    /** 400 닉네임 목록이 빔 */
    public static final String NICKNAME_REQUIRED = "NICKNAME_REQUIRED";
    /** 409 다른 선수와 실명이 같음(앞뒤 공백 제외, 정확히 같음) */
    public static final String DUPLICATE_REAL_NAME = "DUPLICATE_REAL_NAME";
    /** 409 세트 기록이 있는 선수 삭제 */
    public static final String PLAYER_HAS_RECORDS = "PLAYER_HAS_RECORDS";
    /** 400 합치기 대상이 등록 선수 */
    public static final String MERGE_SOURCE_REGISTERED = "MERGE_SOURCE_REGISTERED";
    /** 400 자기 자신과 합치기 */
    public static final String MERGE_SAME_PLAYER = "MERGE_SAME_PLAYER";
    /** 409 두 선수가 같은 세트에 함께 있음 */
    public static final String PLAYER_MERGE_CONFLICT = "PLAYER_MERGE_CONFLICT";
    /** 400 점수표 칸 누락·범위 밖·정수 아님 */
    public static final String INVALID_SCORE_TABLE = "INVALID_SCORE_TABLE";
    /** 400 자동 모드 인원이 10명이 아님(count 포함) */
    public static final String TEAM_PARTICIPANT_COUNT = "TEAM_PARTICIPANT_COUNT";
    /** 400 같은 사람이 두 번(수동은 playerId 포함) */
    public static final String TEAM_DUPLICATE_PARTICIPANT = "TEAM_DUPLICATE_PARTICIPANT";
    /** 400 수동 모드에서 어떤 라인이 2명이 아님(position 포함) */
    public static final String TEAM_LANE_PICK_INCOMPLETE = "TEAM_LANE_PICK_INCOMPLETE";
    /** 400 수동 모드에서 그 라인에 티어 없는 사람이 있는데 tierGaps가 없음(position 포함) */
    public static final String TEAM_TIER_GAP_REQUIRED = "TEAM_TIER_GAP_REQUIRED";
    /** 400 tierGaps 값이 규칙(stronger 0·1·null, tiers 0~5)에 맞지 않음(position 포함) */
    public static final String TEAM_TIER_GAP_INVALID = "TEAM_TIER_GAP_INVALID";
    /** 400 없는 선수 id */
    public static final String TEAM_PLAYER_NOT_FOUND = "TEAM_PLAYER_NOT_FOUND";
    /** 400 쌍에 참가자가 아닌 사람, 자기 자신과의 쌍, 또는 쌍이 5개 초과 */
    public static final String TEAM_PAIR_INVALID = "TEAM_PAIR_INVALID";
    /** 422 자동 모드 결과 0개 */
    public static final String TEAM_NO_VALID_SPLIT = "TEAM_NO_VALID_SPLIT";
    /** 422 수동 모드 결과 0개 */
    public static final String TEAM_PAIR_UNSATISFIABLE = "TEAM_PAIR_UNSATISFIABLE";
    /** 400 라인 고정에 참가자가 아닌 사람, 또는 한 라인에 3명 이상(position·count 포함) */
    public static final String TEAM_LANE_FIX_INVALID = "TEAM_LANE_FIX_INVALID";
    /** 422 라인 고정이 있는데 자동 모드 결과 0개 */
    public static final String TEAM_FIXED_LANE_BLOCKED = "TEAM_FIXED_LANE_BLOCKED";
    /** 503 팀 짜기 계산 동시 실행 한도(2개) 초과 */
    public static final String TEAM_MAKER_BUSY = "TEAM_MAKER_BUSY";
    /** 429 접속 주소별 분당 30회 초과 */
    public static final String RATE_LIMITED = "RATE_LIMITED";

    private TierTeamErrorCodes() {
    }
}
