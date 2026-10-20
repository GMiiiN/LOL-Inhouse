package com.lolinhouse.session;

/** 역할: POST .../duels 의 선택 본문. fearlessMode를 생략하거나 본문이 없으면 RESET(docs/design/m4-design.md §11.1) */
public record DuelCreateRequest(FearlessMode fearlessMode) {
}
