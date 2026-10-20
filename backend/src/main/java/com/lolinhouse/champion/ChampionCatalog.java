package com.lolinhouse.champion;

import java.util.Optional;

import com.lolinhouse.ingest.extract.Deadline;

/**
 * 역할: 챔피언 카탈로그를 읽는 경계. 구현(DataDragonChampionCatalog)은 작업자 A.
 * 사용 규칙: 한 작업/요청 안에서는 current()를 한 번만 부르고 그 스냅샷을 끝까지 쓴다(중간에 버전이 바뀌어도 일관되게).
 * 설명: docs/design/m4-design.md §2.2, §11.2-2
 */
public interface ChampionCatalog {

    /** 현재 스냅샷. 네트워크도 디스크 캐시도 없어 한 번도 못 읽었으면 empty. 처음 호출 때 읽을 수 있으며 동시에 불러도 한 번만 읽는다. */
    Optional<CatalogSnapshot> current();

    /**
     * 작업 마감 안에서만 기다리는 current(). 처음 읽는 중이면 마감까지만 기다리고, 그때까지 못 읽으면 empty
     * (읽기는 백그라운드에서 계속된다). 구현이 마감을 모르면 current()와 같다.
     */
    default Optional<CatalogSnapshot> current(Deadline deadline) {
        return current();
    }
}
