# ADR 006. 중복 방지는 "삭제되지 않은 세트"에서만 유일하도록 생성 컬럼 + UNIQUE로 DB가 보장한다

- 상태: 확정 (M3, 2026-10-16)
- 관련: [ADR 004 세트 지문](004-set-fingerprint.md), `docs/design/m3-design.md` §1.3, `V2__core_schema.sql`(`game_set`)

## 배경

세트는 두 가지 이유로 중복되면 안 된다.
1. **같은 판을 두 번 등록**하면 통계가 두 배가 된다 → 같은 지문(ADR 004)의 세트는 하나만.
2. **한 대결 안에서 같은 세트 번호**가 두 번 있으면 안 된다 → `(duel_id, set_no)`는 하나만.

그런데 세트는 소프트 삭제(`deleted_at`)되고 나중에 복구될 수 있다. 삭제된 세트가 있는 상태에서 같은 세트를 다시 등록하는 것은 허용해야 하고(잘못 올렸다가 지우고 다시 올리는 흐름), 이미 다시 등록해 버렸다면 옛것의 복구는 막아야 한다. 또 동시 요청(담당자의 더블클릭, 두 탭)이 있어도 중복이 새면 안 된다 → 앱의 "먼저 조회 후 저장"만으로는 부족하고 DB가 막아야 한다.

## 결정

`game_set`에 **STORED 생성 컬럼** 둘을 두고 거기에 UNIQUE를 건다.

```sql
active_fingerprint VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin
    GENERATED ALWAYS AS (IF(deleted_at IS NULL, fingerprint, NULL)) STORED,
active_set_no INT GENERATED ALWAYS AS (IF(deleted_at IS NULL, set_no, NULL)) STORED,
UNIQUE KEY uk_game_set_active_fingerprint (active_fingerprint),
UNIQUE KEY uk_game_set_duel_active_set_no (duel_id, active_set_no)
```

- 삭제되지 않은 세트는 값을 가져 서로 비교되고, 삭제된 세트는 NULL이다. InnoDB UNIQUE는 NULL끼리 충돌하지 않으므로 삭제된 세트는 중복 검사에서 자동으로 빠진다.
- 복구는 `deleted_at = NULL`로 되돌리는 UPDATE이고, 그 순간 생성 컬럼이 다시 값을 가져 UNIQUE에 걸리면 오류 1062가 난다. 서비스는 이를 409(복구 충돌)로 바꾼다.
- 지문 계산은 자바(`SetFingerprint`)가 하고 결과 문자열만 `fingerprint` 컬럼에 저장한다. SQL로 규칙을 두 번 구현하지 않는다.
- `active_*` 컬럼은 엔티티에 **매핑하지 않는다**(직접 쓰면 MySQL 오류 3105).
- 모든 경로(JdbcTemplate 직접 INSERT 포함)가 DB 제약을 통과해야 하므로 `SchemaConstraintTest`에서 엔티티를 거치지 않고 검증한다.

## 대안

| 대안 | 버린 이유 |
|---|---|
| 앱에서 조회 후 중복이면 거절 | 동시 요청에서 둘 다 "없다"고 보고 둘 다 저장되는 경쟁 조건 |
| 일반 UNIQUE `(fingerprint)` | 삭제된 세트가 있으면 같은 세트를 다시 등록할 수 없음 |
| 삭제 시 지문을 변경(예: 뒤에 `#deleted-<id>` 붙이기) | 원래 값이 훼손되고 복구 때 되돌려야 함. 규칙이 코드에 흩어짐 |
| 삭제된 세트를 별도 테이블로 이동 | 이동·복구 로직과 FK가 복잡해짐. 소프트 삭제의 단순함을 잃음 |
| 함수형 인덱스(`UNIQUE ((IF(...)))`) | MySQL 8에서 가능하지만 오류 메시지·조회·디버깅이 불편하고, 실제 값을 눈으로 볼 수 있는 STORED 컬럼이 낫다 |
| 가상(VIRTUAL) 생성 컬럼 | UNIQUE를 걸 수는 있지만 STORED가 조회·디버깅에 직관적(값이 보임). 저장 공간은 무시할 규모 |

## 결과 · 한계

- 장점: 동시 저장·복구 충돌까지 DB가 보장한다. 앱 코드는 단순하다(실패하면 409로 변환).
- 한계 1: 복구가 거절되면 사용자가 옛 세트와 새 세트 중 하나를 정리해야 한다. 세트 삭제 후 번호의 틈 처리는 M6 UI에서 정한다.
- 한계 2: `ascii_bin`은 PAD SPACE라 끝 공백만 다른 지문을 같은 값으로 본다(JPA 경로에서는 발생하지 않음, 설계 §9-6). 손으로 SQL을 쓸 때만 주의한다.
- 한계 3: 생성 컬럼 정의를 바꾸려면 새 마이그레이션(V3)으로 `ALTER`해야 하고, 기존 행 수만큼 재계산한다(현재 규모에서는 문제 없음).
- 근거 테스트: `SchemaConstraintTest`의 `activeFingerprintDuplicateIsRejectedEvenAcrossDuels`, `deletedSetDoesNotBlockReRegistrationOfSameFingerprint`, `restoringDeletedSetFailsWhenActiveSetWithSameFingerprintExists`, `activeSetNoMustBeUniquePerDuel`, `deletedSetDoesNotBlockSameSetNoAndRestoreFailsOnSetNoConflict`, `generatedColumnsCannotBeWrittenDirectly`, 그리고 엔티티 경로의 `PersistenceMappingTest.softDeletePersistsAndFreesFingerprintForReRegistrationButRestoreConflicts`.
