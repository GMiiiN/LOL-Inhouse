package com.lolinhouse.registration;

import com.lolinhouse.session.FearlessMode;
import jakarta.validation.constraints.NotNull;

/** 역할: "새 대결로 등록" 옵션. 서버가 같은 저장 트랜잭션에서 이 방식으로 새 대결을 만든다(docs/design/m4-design.md §11.1). */
public record NewDuelOption(@NotNull FearlessMode fearlessMode) {
}
