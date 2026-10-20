package com.lolinhouse.champion;

import java.util.Optional;

import com.lolinhouse.common.web.ProblemResponses;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 역할: 챔피언 목록 HTTP 입구(담당자 전용, /api/admin/** 인가는 SecurityConfig). 확인 화면이 챔피언 검색과 아이콘 표시에 쓴다.
 * 카탈로그를 한 번도 읽지 못했으면 503 CHAMPION_CATALOG_UNAVAILABLE(오류 본문 모양은 다른 API와 같은 ProblemDetail + code).
 * 설명: docs/design/m4-design.md §5
 */
@RestController
@RequestMapping("/api/admin/champions")
public class ChampionController {

    private final ChampionCatalog championCatalog;

    public ChampionController(ChampionCatalog championCatalog) {
        this.championCatalog = championCatalog;
    }

    @GetMapping
    ResponseEntity<?> list() {
        Optional<CatalogSnapshot> snapshot = championCatalog.current();
        if (snapshot.isEmpty()) {
            // ApiException에는 503 팩토리가 없어(공용 파일) 같은 모양의 ProblemDetail을 직접 만든다
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ProblemResponses.of(
                    HttpStatus.SERVICE_UNAVAILABLE, "CHAMPION_CATALOG_UNAVAILABLE",
                    "챔피언 목록을 아직 불러오지 못했습니다. 잠시 뒤 다시 시도하세요."));
        }
        return ResponseEntity.ok(ChampionListResponse.from(snapshot.get()));
    }
}
