package com.lolinhouse.champion;

import java.awt.image.BufferedImage;

/**
 * 역할: 잘라낸 초상화·밴 아이콘을 공식 아이콘과 비교해 상위 후보를 돌려주는 경계. 구현(IconMatcher 이식)은 작업자 A,
 * 호출은 추출 실행기(B). 테스트는 합성 아이콘 구현을 쓴다.
 * 설명: docs/design/m4-design.md §2.2
 */
public interface ChampionIconMatcher {

    /**
     * 상위 3개 후보와 margin을 돌려준다. ban이면 BAN 설정(어둡게 + 사선), 아니면 PICK 설정(원형 테두리)으로 비교한다.
     * 호출자가 이미 잘라낸 이미지를 넘기므로 cropFailed는 항상 false다.
     */
    IconMatch top(BufferedImage crop, boolean ban);
}
