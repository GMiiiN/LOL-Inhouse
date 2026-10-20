package com.lolinhouse.registration;

/**
 * 역할: 결과창 한 행의 선수를 어떻게 확정했는가. UNRESOLVED는 아직 안 골랐음(저장 거부), EXISTING은 playerId 필수,
 * NEW는 playerId 없이 nickname으로 미등록 선수 생성. 설명: docs/design/m4-design.md §11.2-1
 */
public enum RowResolution {
    UNRESOLVED,
    EXISTING,
    NEW
}
