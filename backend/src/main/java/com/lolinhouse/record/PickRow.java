package com.lolinhouse.record;

/** 역할: 유효 세트의 픽 한 개(세트·대결·세트 번호 포함). 피어리스 제한 계산 입력이며 중복 챔피언도 그대로 담긴다. */
public record PickRow(long setId, long duelId, int setNo, String championKey) {
}
