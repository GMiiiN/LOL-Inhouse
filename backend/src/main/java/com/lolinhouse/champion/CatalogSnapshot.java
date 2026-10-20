package com.lolinhouse.champion;

import java.util.List;

/**
 * 역할: 어느 한 시점의 챔피언 카탈로그(Data Dragon 버전, 챔피언 목록, 아이콘 매처). 한 번 만들면 바뀌지 않으며
 * 카탈로그가 새 버전을 읽으면 스냅샷을 통째로 교체한다. 작업 하나는 한 스냅샷만 보고 끝까지 처리한다.
 * 설명: docs/design/m4-design.md §2.2
 *
 * @param version   Data Dragon 버전(예: 16.20.1)
 * @param champions 한글 이름순 목록
 * @param matcher   이 버전의 공식 아이콘으로 만든 매처
 */
public record CatalogSnapshot(String version, List<Champion> champions, ChampionIconMatcher matcher) {

    public CatalogSnapshot {
        champions = List.copyOf(champions);
    }

    /** 이 id(Data Dragon 식별자)의 챔피언이 목록에 있는가. */
    public boolean contains(String id) {
        return champions.stream().anyMatch(champion -> champion.id().equals(id));
    }
}
