package com.lolinhouse.common.web;

/** 역할: M6(저장된 기록 삭제·복구·수정)에서 새로 쓰는 오류 code 목록. 없는 세트는 PublicReadErrorCodes.SET_NOT_FOUND(404)를 쓴다. */
public final class RecordEditErrorCodes {

    /** 409 이미 삭제된 세트를 삭제하려 함 */
    public static final String SET_ALREADY_DELETED = "SET_ALREADY_DELETED";
    /** 409 삭제되지 않은 세트를 복구하려 함 */
    public static final String SET_NOT_DELETED = "SET_NOT_DELETED";
    /** 409 같은 지문의 유효 세트가 있어 복구할 수 없음(existingSetId·existingDuelId·existingSetNo) */
    public static final String RESTORE_FINGERPRINT_CONFLICT = "RESTORE_FINGERPRINT_CONFLICT";
    /** 409 같은 대결 같은 번호의 유효 세트가 있어 복구할 수 없음(existingSetId) */
    public static final String RESTORE_SET_NO_CONFLICT = "RESTORE_SET_NO_CONFLICT";

    /** 409 삭제된 세트를 수정하려 함 */
    public static final String SET_DELETED = "SET_DELETED";
    /** 409 폼을 받은 뒤 다른 요청이 세트를 먼저 바꿈(expectedUpdatedAt 불일치) */
    public static final String SET_MODIFIED = "SET_MODIFIED";

    private RecordEditErrorCodes() {
    }
}
