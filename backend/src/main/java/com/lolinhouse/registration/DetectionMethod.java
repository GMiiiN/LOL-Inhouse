package com.lolinhouse.registration;

/** 역할: 위쪽 팀이 A팀/B팀 중 어디인지 판별한 방법. FIRST_SET은 대결 1세트 규칙, OVERLAP은 직전 A팀과의 겹침, PENDING은 보류 */
public enum DetectionMethod {
    FIRST_SET,
    OVERLAP,
    PENDING
}
