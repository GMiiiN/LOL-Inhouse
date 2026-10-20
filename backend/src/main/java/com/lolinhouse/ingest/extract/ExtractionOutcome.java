package com.lolinhouse.ingest.extract;

import java.util.List;

/**
 * 역할: 재시도·마감을 적용한 추출이 성공했을 때의 결과. 검증된 결과창과 시도 기록(계측)을 함께 돌려준다.
 * 실패 때의 시도 기록은 {@link ExtractionException#attempts()}에 있다.
 * 설명: docs/design/m4-design.md §11.2-10
 */
public record ExtractionOutcome(ExtractedScoreboard scoreboard, List<Attempt> attempts) {

    public ExtractionOutcome {
        attempts = List.copyOf(attempts);
    }
}
