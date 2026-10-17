package com.lolinhouse.domain.player;

/**
 * 역할: 닉네임 매칭 결과 종류
 */
public enum MatchType {
    /** 정규화 후 별칭과 완전히 같다. */
    EXACT,
    /** 잘린 닉네임의 앞부분이 한 선수와 충분히 비슷해 자동 연결한다. */
    TRUNCATED_AUTO,
    /** 잘리지 않은 닉네임이 한 선수의 별칭과 한 글자(치환·삽입·삭제) 차이라 자동 연결한다(사용자 결정 2026-10-16). */
    ONE_CHAR_AUTO,
    /** 자동 연결하지 않고 담당자가 후보 중에서 고른다. */
    CANDIDATES,
    /** 비슷한 선수가 없어 미등록 선수를 새로 만든다. */
    NEW_UNREGISTERED
}
