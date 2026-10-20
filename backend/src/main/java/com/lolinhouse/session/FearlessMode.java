package com.lolinhouse.session;

/**
 * 역할: 대결을 만들 때 고르는 피어리스 방식. RESET = 제한 없이 시작, CONTINUE = 같은 내전 묶음에서
 * 직전 RESET 대결 이후의 유효 세트 픽을 이어서 제한한다. 묶음의 첫 대결은 항상 RESET.
 * 설명: docs/design/m4-design.md §11.1
 */
public enum FearlessMode {
    RESET,
    CONTINUE
}
