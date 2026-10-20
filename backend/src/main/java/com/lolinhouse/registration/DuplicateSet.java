package com.lolinhouse.registration;

/** 역할: 같은 지문의 유효 세트가 이미 있을 때 그 세트의 위치(409 DUPLICATE_SET의 existing* 값과 같다) */
public record DuplicateSet(Long setId, Long duelId, int setNo) {
}
