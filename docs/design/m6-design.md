# M6 설계: 저장된 기록 수정·삭제·복구·변경 이력

- 작성: 2026-10-22, 브랜치 `m6-edit-history`(main b5398b0)
- 관련: 유저플로우 §5.6·§6·§7·§11-4·§11-6·§12-16, 기능명세서 §7, implementation-plan M6, ADR 004·006·009·010·013·014, m3-design §9, m4-design, m5/m56-design
- 우선순위: **§14 사용자 결정이 다른 절보다 앞선다.** §14가 비어 있으면 각 절의 "추천"을 잠정 결정으로 본다.

## 0. 요약

사용자 피드백: "기록을 잘못 넣으면 고칠 방법이 없다".

1. **P0: 세트 삭제·복구 + 변경 이력** — 코드가 가장 적고 "잘못 넣은 세트를 빼고 다시 넣기"가 바로 된다.
2. **P0: 세트 수정** — 등록 확인 화면 부품(TeamTable·BanGrid·ChampionPicker) 재사용, 지문·피어리스 재검사, A팀/B팀은 **그 세트의 수정 전 선수 구성에 고정**(뒤 세트로 번지지 않게).
3. **P1: 수동 입력·대결 삭제** — 수동 입력은 수정과 같은 화면, 기존 `POST …/sets`에서 `extractionJobId` null 허용. 대결 삭제는 유효 세트가 없을 때만.
4. **P2(지금 안 만듦)**: 세트 번호 정리, 세트를 다른 대결로 옮기기, 대결 피어리스 방식 바꾸기, 수정 화면 아이콘 잘라 보기, 공개 "수정됨" 표시.

별도 기능이 필요 없다고 판단한 것(§2.3): 같은 라인 선수 교환(§6.1, 등록 때 자동 감지 + 세트마다 팀 저장 → `team_swap` 테이블 없음), 피어리스·점수 초기화(§6.2, 이미 "새 대결 RESET/CONTINUE").

DB 변경은 `V8__audit_log.sql` 하나. 새 기술 없음.

## 1. 현재 구조 (코드 확인 사실)

### 1.1 이미 있는 것
- `game_set.deleted_at`, `GameSet.markDeleted()`·`restore()`(잘못된 상태면 `IllegalStateException`).
- 생성 컬럼 `active_fingerprint`·`active_set_no` UNIQUE(ADR 006) → 복구 시 지문·번호가 겹치면 DB 1062. `SchemaConstraintTest`·`PersistenceMappingTest`로 검증됨.
- 공개 조회는 모두 `RecordReader`(`g.deletedAt is null`) → 기록·대결·세트 상세, 통계, 랭킹, CSV, 홈, 듀오, 티어 추천에 **수정·삭제·복구가 자동 반영**(ADR 013).
- 등록 맥락(`RegistrationContextService.stateFor`): 다음 번호 = 유효 세트 최댓값 + 1, 직전 세트 = 마지막 유효 세트, 피어리스 제한 = 체인 대결의 유효 세트 픽 전부.
- 세트 시점 제한 `record/RecordViews.fearlessRestrictedAt`(package-private).
- 관리자 묶음 상세 `AdminSetSummary`는 삭제 세트도 `deleted=true`로 보여 주고 화면에 배지·`set-row-deleted` 있음.
- 잠금 순서 — 등록: 묶음 → 대결 → 추출 작업 → (새 선수·별칭 시) score_setting / 선수 생성·수정·병합: score_setting → 선수.
- 오류 패턴: `ApiException` + `.with(...)`, UNIQUE 위반은 트랜잭션 밖에서 제약 이름으로 409 변환(`SetRegistrationService.translate`), 오류 코드 상수 클래스.
- 관리자 '기록 관리'(`/admin/history`)는 `PlaceholderPage`, NavLink 있음.

### 1.2 숨은 사실
1. **세트 번호 재사용**: *마지막* 세트를 지우고 재등록하면 같은 번호를 다시 쓴다 → 옛 세트 복구 시 `uk_game_set_duel_active_set_no` 충돌. *중간* 세트를 지우면 그 번호는 등록으로 못 채운다(새로 올리면 맨 뒤). → 중간 세트 오류는 **수정**이 주된 방법.
2. **삭제 세트의 원본 이미지를 못 봄**(`SetImageAdminController`가 `!s.isDeleted()`로 막음) → 담당자에게 열어야 함.
3. **같은 이미지 재업로드는 이미 됨**(중복 검사는 유효 세트만, 재사용 작업은 `game_set_id is null`만).
4. **가져오기 세트도** `top_team` non-null, `screen_row` 1~5 → 운영 중 모든 세트를 위쪽/아래쪽 팀·행 번호로 펼칠 수 있다(`screen_row IS NULL` 행 없음은 [확인 필요]).
5. **자식 행 교체 함정**(m3 §9): orphanRemoval은 INSERT가 DELETE보다 먼저 → UNIQUE 위반. "비우기 → flush → 다시 추가".
6. **`@PreUpdate`는 엔티티 자체가 바뀔 때만** → 선수·밴만 바뀌면 `updated_at` 그대로. 동시 수정 검사에 쓰려면 명시 갱신.
7. **중간 빈 대결 삭제가 피어리스 범위를 바꿈**: D1(RESET) → D2(RESET, 빈) → D3(CONTINUE)에서 D2를 지우면 D3 범위가 D1까지 넓어짐.
8. 묶음·선수 삭제는 삭제 표시 세트도 기록으로 센다(그대로 둠).
9. 관리자 표 `source`는 `image_key` 없으면 `IMPORT` → 수동 입력도 "가져오기"로 보임(R14).

## 2. 범위와 우선순위

| 순위 | 기능 | 이유 |
|---|---|---|
| P0-1 | 세트 삭제·삭제 목록·복구, 이력(DELETE/RESTORE) | 코드 최소, 운영 불편 즉시 해소 |
| P0-2 | 세트 수정(확인 화면 부품 재사용, 지문·피어리스 재검사, 동시 수정 보호), 이력(UPDATE), 세트별 이력 | 중간 세트 오류는 수정으로만(§1.2-1) |
| P1-1 | 수동 입력(이미지 없이 새 세트, 수정과 같은 화면) | 유저플로우 2026-10-17 결정 |
| P1-2 | 대결 삭제(유효 세트 0개) | 잘못 만든 "새 대결" 정리 |
| P1-3 | 기록 관리 "최근 변경" | 이력 테이블로 거의 공짜 |
| P2 | 번호 정리, 세트 옮기기, 피어리스 방식 바꾸기, 아이콘 잘라 보기, 공개 "수정됨" | 실사용에서 필요 확인 시 |

배포 단위: **PR-A**(P0-1 + V8) → **PR-B**(P0-2) → **PR-C**(P1). 작업자 파일 담당은 동일.

### 2.3 별도 기능이 필요 없는 것
- **§6.1 같은 라인 교환**: 등록 때 `TeamChangeDetector`가 `SAME_LANE_SWAP`이면 안내만 하고 같은 대결로 저장, 세트마다 `game_set_player.team` 저장 → 이전 소속·승패 유지, 대결별 개인 성적은 실제 뛴 세트 기준, 피어리스·점수 이어짐. 추천: `team_swap` 테이블·교환 화면 없음, 유저플로우 §6.1을 "등록 시 자동 감지로 대체(2026-10-22)"로 수정(§14 Q10).
- **§6.2 초기화**: 묶음 상세 "새 대결"(RESET/CONTINUE), 확인 화면 "새 대결로 등록"(저장할 때만 대결 생성), 잘못 만든 빈 대결은 P1-2로. 세트가 있는 대결의 방식 변경은 P2(§14 Q11).

## 3. 대안 비교

### 3.1 수정 요청 모양
- **A(추천) 등록 명령과 같은 모양**(`TeamInput`/`RowInput` 재사용, `SetEditRequest`): `SetEvaluator`·`RegistrationShape`·reviewModel·TeamTable 재사용. 캡처한 사람 포지션은 요청에서 빼고 서버가 위쪽 1행 포지션으로 채움.
- B 진영 기준(가져오기처럼): 새 선수·별칭·표시 합계·화면 부품을 새로 만들어야 함.
- 수동 입력도 A 모양, 화면에서 "위쪽 = 블루" 고정.

### 3.2 화면 재사용
- **부품만 재사용(추천)**: 새 `SetFormPage`(수정·수동 공용), `TeamTable`·`BanGrid`·`ChampionPicker` 재사용, 초안 없는 행은 새 `ManualPlayerPicker`, `useDebouncedCheck`를 검사 함수 인자로 일반화(ReviewStep 한 줄).
- ReviewStep 통째로: `DraftResponse`·새 대결 흐름과 강결합 → M4 회귀 위험. 완전히 새 화면: 중복·동작 불일치 위험.

### 3.3 수정 시 A팀/B팀
- **A(추천) 수정 전 세트에 고정**: 위쪽 팀이 A팀인지는 *그 세트의 수정 전 A팀 선수*와 겹침 3명 이상(`DuelTeamRules.detectTopTeam(oldTeam1Ids, newTopIds)`). 뒤 세트로 번지지 않음. 1세트 진영을 바꾸면 "1세트 A팀 블루" 관례가 깨지는데 기존 진영 경고로 표시.
- B 직전 세트 기준 재판별 + 뒤 세트 연쇄 뒤집기: 관례 유지, 대신 다른 세트를 건드리고 애매한 경우·코드가 많음.
- C 바뀌면 거절: 정당한 진영 수정을 막음.
- 추가 경고: 앞뒤 세트와 A/B 겹침 규칙이 맞지 않으면 "앞뒤 세트와 A팀 구성이 맞지 않습니다"(§14 Q3).

### 3.4 피어리스 재검사
- 뒤로(앞선 세트 픽과 겹침) / 앞으로(같은 대결 뒤 번호 + 이 대결을 체인에 포함하는 뒤 CONTINUE 대결과 겹침).
- 둘 다 차단: 풀 수 없는 상태(1세트 Ahri↔3세트 Lux가 서로 뒤바뀐 경우).
- **뒤로 차단(422) + 앞으로 경고(추천)**: 등록 규칙과 같고 위 예시도 풀림(1세트 먼저 고침). 경고에 충돌 세트 링크.
- 복구는 두 방향 모두 경고로 허용(§3.6).

### 3.5 세트 번호
- **번호 틈 유지(추천)**: 2세트를 지우면 "1세트, 3세트", 복구하면 원래 번호. 진영 규칙(홀수 = A팀 블루)과 어긋나지 않음.
- 삭제 시 당기기: 복구 끼워 넣기, UNIQUE 임시 번호(+10000 오프셋), 진영 기대값 변화 → 복잡.
- 공개 화면만 순서 표시: 저장값과 화면 번호 불일치.
- 담당자 "번호 정리" 버튼: P2.

### 3.6 복구 충돌 (유저플로우 §11-6 미확정 → 추천)
| 충돌 | 처리 |
|---|---|
| 같은 지문 유효 세트 | 409 `RESTORE_FINGERPRINT_CONFLICT` + `existingSetId·existingDuelId·existingSetNo`. 자동 교체 없음 |
| 같은 대결 같은 번호 유효 세트 | 409 `RESTORE_SET_NO_CONFLICT` + 그 세트 id. "맨 뒤로 복구" 안 함 |
| 피어리스 | 경고로 허용, 충돌 세트 표시 |

사전 조회로 친절한 정보, 최종 보장은 DB UNIQUE(1062 → 제약 이름으로 변환). 삭제 목록에 막는 세트를 미리 계산해 표시.

### 3.7 변경 이력 형식
- **수정 전·후 전체 스냅샷 JSON + 읽을 때 차이 계산(추천)**: 저장 단순, 수동 복원 근거, 차이 규칙을 바꿔도 과거가 다시 읽힘. 세트당 약 3KB.
- 차이만 저장(JSON Patch): 연쇄가 깨지면 못 읽음. 컬럼 단위 정규화: 스키마가 무거움.

### 3.8 대결 삭제 범위
- 세트가 하나도 없을 때만: 단순, "세트를 지웠는데 대결이 남는" 경우는 못 치움.
- **유효 세트 0개, 삭제 표시 세트만 있으면 함께 영구 삭제(추천)**: PURGE 스냅샷을 이력에, 확인 창에 "삭제된 세트 N개도 영구 삭제".
- 유효 세트가 있어도 삭제: 실수 한 번에 큰 범위가 사라짐.
- 공통: **이 대결이 RESET이고 다음 seq 대결이 CONTINUE이면 409 `DUEL_CHAIN_DEPENDENT`**(§1.2-7). CONTINUE 대결은 지워도 범위 불변 → 허용.

## 4. 규칙

### 4.1 세트 수정
- 바꿀 수 있음: 결과창 날짜, 경기 시간(빈 값 = 모름 → 지문 `?`, ADR 014), 위쪽 팀 진영·승패, 팀별 표시 합계 4개, 선수 행(기존 선수 / 새 미등록 선수 / 별칭 추가, 포지션, 챔피언, K/D/A·CS·골드), 밴 10개.
- 바꿀 수 없음: `duel_id`, `set_no`, 이미지 해시·키, 등록 시각.
- 삭제된 세트는 수정 불가(409 `SET_DELETED`).
- 평가(`SetEvaluator` 수정용 진입점, 기존 경로 불변):
  - `setNo` = 저장 번호, 위쪽 팀 판별 = 수정 전 A팀과 겹침(§3.3 A), 팀 변경 감지 `Kind.NONE`.
  - 앞쪽 피어리스(앞 대결 전부 + 같은 대결 작은 번호 세트) → 422 `SET_RULES_VIOLATED`.
  - 앞으로 충돌 → 경고 + `fearlessConflicts[{setId, duelId, duelSeq, setNo, championKey}]`.
  - 지문 중복 = 같은 지문 유효 세트 중 **자기 자신 제외** → 409 `DUPLICATE_SET`.
  - 합계·진영·앞뒤 A/B 불일치는 경고.
- 동시 수정 보호: 요청 `expectedUpdatedAt`, 잠금 뒤 다르면 409 `SET_MODIFIED`. 저장 시 `updated_at` 명시 갱신(스키마 변경 없음).
- 저장: 메타 갱신 + 지문 재계산(`GameSet` 새 메서드) → 선수·밴 비우기 → flush → 다시 추가. `top_team` 유지, `screen_row`는 요청 행 번호.
- 새 선수·별칭은 등록과 같은 `confirmPlayers` 규칙, 응답 `createdPlayers`·`addedAliases`.

### 4.2 수동 입력
- 기존 `POST /api/admin/duels/{duelId}/sets`·`/sets/check`에서 `extractionJobId = null` 허용: 추출 작업 잠금·연결 생략, `image_hash/key = NULL`, `top_team = NULL`(다음 등록 기본 진영은 비움, `DuelTeamRules.defaultTopSide`가 처리).
- 평가는 등록과 완전히 같음(다음 번호, 직전 세트 겹침, 팀 변경 감지, 새 대결로 등록, 피어리스, 지문, 새 선수·별칭).
- 화면: 위쪽 = 블루 고정, 캡처한 사람 포지션 숨김(TOP), 행 1~5 = 탑~서폿, 표시 합계 숨기고 선수 행 합으로 보냄.
- 경기 시간: **필수** 추천(§14 Q9).

### 4.3 세트 삭제
- 유효 세트만(아니면 409 `SET_ALREADY_DELETED`). 공개·통계·CSV에서 빠지고, 등록 맥락 재계산, 지문·번호가 비어 재등록 가능. 피어리스 위반이 새로 생기지 않음. 진영 경고 재계산 없음. 사유 선택 입력.

### 4.4 복구
- 삭제된 세트만(아니면 409 `SET_NOT_DELETED`). 충돌은 §3.6. A/B 재판별 없음.

### 4.5 변경 이력
- 남기는 동작: `UPDATE`, `DELETE`, `RESTORE`, `PURGE`, 대결 `DELETE`, 수동 입력 `CREATE`. 일반 결과창 등록·가져오기는 남기지 않음(첫 UPDATE의 before가 원래 값).
- 같은 트랜잭션에서 씀(실패하면 이력도 없음).
- 공개 범위: **담당자만**(기능명세서 §7-4).
- 보존: **영구**(백업에 포함).
- 차이: 서버 순수 함수가 두 스냅샷을 `{label, before, after}` 줄로. 선수 행은 (팀, 포지션)으로, 밴은 순번으로 짝짓기. 챔피언은 카탈로그 한글(없으면 키). 선수 이름은 스냅샷 당시 이름(합치기 뒤에도 읽힘).

### 4.6 대결 삭제
- §3.8 추천. 유효 세트 있으면 409 `DUEL_HAS_SETS`, 체인 의존이면 409 `DUEL_CHAIN_DEPENDENT`.
- 순서(한 트랜잭션, 묶음 → 대결 잠금): PURGE 스냅샷 → `extraction_job where duel_id` 삭제 → `game_set where duel_id` 삭제(자식 CASCADE) → `duel` 삭제 → 대결 DELETE 이력. 이미지 파일은 지우지 않음(ADR 010).

### 4.7 자동 반영(추가 코드 없음)
세트·대결 상세·점수·개인 승무패(`RecordService`), 기록·필터·총계·CSV(`RecordReader`·`RecordExportService`), 프로필·랭킹·챔피언·홈·듀오(`playerRecords`), 티어 추천(`TierRecommendationService`), 피어리스 표시(조회 시 계산). **프런트 캐시**는 `PUBLIC_KEY`·`SESSIONS_KEY`·`registrationContextKey(duelId)`·`PLAYERS_KEY`(새 선수 시) 무효화.

## 5. 동시성·트랜잭션
- 잠금 순서: **묶음 → 대결 → 세트(새 `findByIdForUpdate`) → (새 선수·별칭 시) score_setting → 자식 행 변경 → 이력 INSERT**. 대상 세트의 대결 id는 트랜잭션 밖에서 먼저 읽음.
- score_setting을 자식 행 삭제보다 먼저 → 선수 합치기와 교착 고리 방지. 1213은 기존 409 `CONCURRENT_REQUEST_CONFLICT`(모든 경로 적용 여부 [확인 필요: `ApiExceptionHandler`]).
- 삭제·복구는 잠금 뒤 상태 확인 → 동시 요청 두 번째는 409. 수정은 `expectedUpdatedAt`.
- UNIQUE 변환(트랜잭션 밖, 제약 이름): 수정 `uk_game_set_active_fingerprint` → `DUPLICATE_SET` / 복구 같은 제약 → `RESTORE_FINGERPRINT_CONFLICT`, `uk_game_set_duel_active_set_no` → `RESTORE_SET_NO_CONFLICT` / `uk_game_set_player_player` → `CONCURRENT_REQUEST_CONFLICT`. 제약 이름 도우미는 Phase 0에서 `common/web`으로.

## 6. DB: `V8__audit_log.sql`

```sql
CREATE TABLE audit_log (
  id           BIGINT       NOT NULL AUTO_INCREMENT,
  target_type  VARCHAR(10)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL, -- SET / DUEL
  target_id    BIGINT       NOT NULL,                                        -- FK 없음: 영구 삭제 뒤에도 이력은 남는다
  duel_id      BIGINT       NULL,                                            -- 목록·필터용 사본(FK 없음)
  session_id   BIGINT       NULL,
  action       VARCHAR(10)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  actor        VARCHAR(50)  NOT NULL,                                        -- 로그인한 담당자 username
  reason       VARCHAR(200) NULL,
  before_data  JSON         NULL,                                            -- 스냅샷 v1 (CREATE·RESTORE면 NULL)
  after_data   JSON         NULL,                                            -- 스냅샷 v1 (DELETE·PURGE면 NULL)
  created_at   DATETIME(6)  NOT NULL,                                        -- UTC
  PRIMARY KEY (id),
  KEY ix_audit_log_target (target_type, target_id, id),
  KEY ix_audit_log_created (created_at),
  CONSTRAINT ck_audit_log_target_type CHECK (target_type IN ('SET','DUEL')),
  CONSTRAINT ck_audit_log_action CHECK (action IN ('CREATE','UPDATE','DELETE','RESTORE','PURGE')),
  CONSTRAINT ck_audit_log_data CHECK (before_data IS NOT NULL OR after_data IS NOT NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
```

- JSON 매핑은 `ExtractionJob.resultJson` 패턴.
- 세트 스냅샷 v1: `{v:1, setId, duelId, setNo, shotDate, durationSec(null 가능), team1Side, winnerTeam, topTeam, displayed:{TEAM1:{k,d,a,gold}, TEAM2:{...}}, fingerprint, hasImage, deletedAt, players:[{playerId, displayName(당시), team, position, screenRow, championKey, kills, deaths, assists, cs, gold}], bans:[{turn, side, championKey}]}`
- 대결 스냅샷: `{v:1, duelId, sessionId, seq, fearlessMode, purgedSetIds:[...]}`
- `team_swap` 없음, `game_set` 변경 없음. 운영 적용 전 **백업 먼저**.

## 7. API (모두 담당자 전용, 변경은 CSRF)

| 메서드·경로 | 용도 | 성공 | 주요 실패 |
|---|---|---|---|
| `GET /api/admin/sets/{id}/form` | 수정 폼(삭제 세트도 읽기) | 200 `SetFormResponse` | 404 `SET_NOT_FOUND` |
| `POST /api/admin/sets/{id}/check` | 수정 검사 | 200 `EditEvaluation` | 400, 404, 409 `SET_DELETED` |
| `PUT /api/admin/sets/{id}` | 수정 저장 | 200 `SetEditResponse` | 422 `SET_RULES_VIOLATED`, 409 `DUPLICATE_SET`·`SET_MODIFIED`·`SET_DELETED`·`ALIAS_CONFLICT`·`CONCURRENT_REQUEST_CONFLICT` |
| `DELETE /api/admin/sets/{id}?reason=` | 소프트 삭제 | 204 | 404, 409 `SET_ALREADY_DELETED` |
| `POST /api/admin/sets/{id}/restore` `{reason?}` | 복구 | 200 `{setId, warnings, fearlessConflicts}` | 404, 409 `SET_NOT_DELETED`·`RESTORE_FINGERPRINT_CONFLICT`·`RESTORE_SET_NO_CONFLICT` |
| `GET /api/admin/deleted-sets` | 삭제 세트 목록(최근 삭제순) | 200 `DeletedSetRow[]` | — |
| `GET /api/admin/sets/{id}/history` | 세트별 이력(오래된 것부터) | 200 `HistoryEntry[]` | 404 |
| `GET /api/admin/history?beforeId=&limit=50` | 최근 변경 | 200 `{entries, nextBeforeId}` | 400 `INVALID_QUERY` |
| `POST /api/admin/duels/{duelId}/sets/check`, `…/sets` | 수동 입력(`extractionJobId: null`) | 기존과 같음 | 기존과 같음 |
| `DELETE /api/admin/duels/{id}` | 대결 삭제 | 204 | 404 `DUEL_NOT_FOUND`, 409 `DUEL_HAS_SETS`·`DUEL_CHAIN_DEPENDENT` |
| `GET /api/admin/sets/{id}/image` | (변경) 삭제 세트도 허용 | — | — |

DTO(Phase 0 확정): `SetEditRequest`(topSide, topResult, shotDate, duration("mm:ss" 또는 "" = 모름), top·bottom `TeamInput`, `expectedUpdatedAt` 필수, reason ≤200), `SetFormResponse`(setId, duelId, duelSeq, sessionId, sessionStartDate, setNo, deleted, updatedAt, hasImage, form, evaluation, restrictedChampions, fearlessConflicts), `EditEvaluation`(= `Evaluation` + fearlessConflicts, teamChange 항상 NONE), `DeletedSetRow`(…, deletedAt, deleteReason, blockers{fingerprintSetId?, setNoSetId?}, fearlessConflicts), `HistoryEntry`(id, targetType, targetId, action, actor, reason, createdAt, summary, changes[{label, before, after}]). 오류 코드 `common/web/RecordEditErrorCodes`, 프런트 `errorMessages.ts`. `Instant` 왕복 정밀도 [확인 필요](첫 통합 테스트, 잘리면 밀리초 비교).

## 8. 백엔드 구성

| 위치 | 새/변경 | 내용 |
|---|---|---|
| `domain/set/FearlessConflicts` | 새(순수) | ① 세트 k 시점 앞쪽 제한 ② 새 픽과 뒤 세트 충돌 ③ 대결 삭제 체인 의존. 수정·복구·대결 삭제 공용 |
| `domain/set/BanOrder` | 변경 | 역변환: 저장 순번 밴 + 위쪽 진영 → 팀별 화면 칸 5개 |
| `session/GameSet` | 변경 | `revise(...)`(메타·합계·지문 재계산·`updatedAt` 명시), `clearRecords()` |
| `session/GameSetRepository` | 변경 | `findByIdForUpdate`, 삭제 목록, 체인 대결 픽 조회 |
| `registration/SetEvaluator` | 변경 | `evaluateEdit(...)`(기존 경로 불변) |
| `registration/RegistrationContextService` | 변경 | `stateForEdit(set)` |
| `registration/SetRegistrationService`·`SetRegistrationRequest` | 변경 | `extractionJobId` null 허용, 이력 CREATE |
| `edit/SetEditService`·`SetEditController`·`SetFormAssembler` | 새 | 폼 펼치기, 검사, 수정 저장 |
| `edit/SetLifecycleService` | 새 | 삭제·복구·삭제 목록 |
| `audit/**` | 새 | 엔티티·저장소·스냅샷·diff(순수)·서비스(`Propagation.MANDATORY`)·컨트롤러 |
| `session/InhouseSessionService`·`Controller` | 변경 | `deleteDuel` |
| `record/SetImageAdminController` | 변경 | 삭제 세트 필터 제거 |
| `common/web/RecordEditErrorCodes`(+제약 이름 도우미) | 새 | Phase 0 |

## 9. 프런트

### 9.1 라우트 (Phase 0)
- `/admin/history` → `RecordManagePage`: 탭 **삭제된 세트**(표, 복구, 막는 세트 링크, 피어리스 경고, 원본 보기, 이력) / **최근 변경**(피드, 더 보기).
- `/admin/sets/:setId/edit` → `SetEditPage`, `/admin/duels/:duelId/sets/manual` → `ManualSetPage`(둘 다 `SetFormPage`), `/admin/sets/:setId/history` → `SetHistoryPage`.

### 9.2 `SetFormPage`
- 머리: "대결 N · M세트 수정" / "대결 N · M세트로 수동 입력". 수정은 원본 이미지 확대, 사유(선택), 오류·경고(`ReviewIssuesList`), 앞으로 피어리스 충돌 목록.
- 경기 정보: 날짜, 시간(수정은 모름 허용), 승패, 진영. 수동은 "블루 팀/레드 팀", 캡처한 사람 포지션 숨김.
- 팀 표: `TeamTable` + 초안 없으면 `ManualPlayerPicker`(검색 선택 + 새 미등록 선수 + 별칭 추가). 수동은 합계 숨김.
- 검사: 일반화한 `useDebouncedCheck`. 첫 평가는 폼 응답 `evaluation`.
- 저장 후: 수정 → 세트 상세, 수동 → "저장됨" 패널. 캐시 무효화. `SET_MODIFIED`면 "새로 불러오기". `beforeunload` 경고.

### 9.3 기존 화면 추가
- `SessionDetailPage`: 세트 표 줄마다 **수정 · 삭제 · 이력**(삭제 줄은 **복구 · 이력**), 삭제는 확인 + 사유. 대결 머리에 **대결 삭제**(유효 세트 0개일 때, 영구 삭제될 수 표시), **수동 입력** 링크.
- `SetDetailPage`: 담당자만 `SetAdminActions`(수정·삭제·이력). 삭제하면 관리자 묶음 상세로.
- `SetRegisterPage`·`ExtractionProgress` 실패 안내에 "수동 입력으로".

## 10. 테스트

### 10.1 도메인 TDD
1. `FearlessConflicts`: 앞 세트 충돌 → 앞쪽 제한, 뒤 세트 충돌 → 목록, CONTINUE 뒤 대결 포함·RESET 제외, seq 정렬, Ahri/Lux 교차 시나리오, 체인 의존(RESET+다음 CONTINUE = 의존, CONTINUE 삭제 = 아님, 마지막 대결 = 아님).
2. `BanOrder` 역변환 왕복(위쪽 블루·레드).
3. A/B 고정: 한 명 교체 유지, 1세트 진영 바꿈 → 같은 A/B + 경고, 통째로 바뀜 → 선수를 따라감.
4. `SetSnapshotDiff`: 변경 없음, 챔피언·KDA·선수 연결·포지션 맞바꿈·밴 순번·시간 null↔값·승리 팀, v1 호환.
5. `GameSet.revise`: 지문(시간 null → `?`), `updatedAt`, 이미지 불변.

### 10.2 통합 (7000~7099년 날짜, 클래스별 하위 구간)
- 수정: 공개·점수·랭킹·CSV 반영(§12-16), 자기 지문 200, 다른 지문 409, 포지션·팀 맞바꿈 UNIQUE 함정 없음, 앞쪽 피어리스 422, 앞으로 충돌 200+경고, 낡은 `expectedUpdatedAt` 409, 자식만 바꿔도 `updated_at` 갱신, 삭제 세트 수정 409, 새 선수·별칭, 실패 시 세트·이력 변경 없음.
- 삭제·복구: 공개 404·통계 제외·등록 맥락 반영, 두 번 삭제 409, 복구 정상·지문 409·번호 409·피어리스 경고, `blockers` 정확성, 같은 이미지 재업로드 회귀.
- 이력: 동작마다 1행, actor, 스냅샷, 순서, 커서.
- 수동 입력: job null 저장(이미지·`top_team` NULL), 새 대결로 등록과 함께, 지문 409, 다음 기본 진영 null.
- 대결 삭제: 빈 대결 204, 유효 세트 409, 다음 CONTINUE 409, 삭제 세트만 → 영구 삭제 + PURGE, 묶음 삭제 이어서 가능.
- 동시성: 같은 세트 동시 수정, 수정·등록 직렬화, 동시 삭제·복구, 수정(새 선수)·합치기 교착 → 409.
- 보안: 익명 401, CSRF 403, 공개 GET 영향 없음. 삭제 세트 원본 담당자 200.

### 10.3 프런트
`setFormModel`, `ManualPlayerPicker`, `TeamTable` 초안 없음 분기(기존 등록 테스트 유지), `useDebouncedCheck` 회귀, `SetEditPage`, `RecordManagePage`, `SessionDetailPage` 버튼·대결 삭제 조건, `SetDetailPage` 담당자 버튼, `routes.test`.

## 11. 순서
1. **Phase 0(리드)**: V8, 오류 코드·제약 이름 도우미, DTO·`AuditService` 시그니처, 프런트 `api/recordEditTypes.ts`·`recordEdit.ts`·`errorMessages.ts`, 라우트·빈 페이지 → 컴파일·기존 테스트 녹색, V8 적용.
2. **P0-1**: `FearlessConflicts`·diff → 삭제·복구·이력 → 기록 관리 화면·묶음 상세 버튼.
3. **P0-2**: `BanOrder` 역변환 → `evaluateEdit`·`stateForEdit` → 수정 서비스 → `SetFormPage` 수정 모드.
4. **P1**: 수동 입력 → 대결 삭제 → 수동 모드 화면·진입점.
5. 리드 검증 → Codex(Astra) 리뷰 → 학습 노트·ADR·AGENTS → 승인 후 커밋·PR → 데스크탑 백업 후 적용.

## 12. 작업 분할

| 담당 | 파일 | 의존 |
|---|---|---|
| 리드 | V8, `RecordEditErrorCodes`(+도우미), DTO, `AuditService` 인터페이스, 프런트 api·errorMessages·routes(+빈 페이지), docs, ADR, AGENTS | 먼저 |
| BE-A 도메인·이력 | `domain/set/FearlessConflicts`, `BanOrder`(역변환), `audit/**` | Phase 0 |
| BE-B 수정·삭제·복구 | `session/GameSet`, `GameSetRepository`, `registration/SetEvaluator`, `RegistrationContextService`, `edit/**` | Phase 0, A 시그니처 |
| BE-C 수동 입력·대결 삭제 | `registration/SetRegistrationService`, `SetRegistrationRequest`, `RegistrationShape`, `session/InhouseSessionService`·`Controller`, `record/SetImageAdminController` | Phase 0, `AuditService`, `FearlessConflicts` |
| FE-D 입력 화면 | `pages/admin/set-form/**`, `register/TeamTable.tsx`, `useDebouncedCheck.ts`, `ReviewStep.tsx`(한 줄), `SetRegisterPage.tsx`·`ExtractionProgress.tsx` | Phase 0 |
| FE-E 관리·이력 화면 | `pages/admin/history/**`, `SessionDetailPage.tsx`, `pages/set/SetAdminActions.tsx`, `SetDetailPage.tsx`(한 줄) | Phase 0 |

Gradle 동시 실행은 빌드 디렉터리를 나눈다.

## 13. 위험
| # | 위험 | 대응 |
|---|---|---|
| R1 | 자식 행 교체 UNIQUE | clear → flush → add, 맞바꿈 테스트 |
| R2 | 자식만 바뀌면 `updated_at` 그대로 | `revise`에서 명시 갱신 |
| R3 | A/B 고정으로 "1세트 A팀 블루" 관례가 깨진 세트 | 진영 경고, §14 Q3 |
| R4 | 앞으로 충돌 경고 허용 → 위반 데이터 | 충돌 세트 링크, P2 "충돌" 배지 |
| R5 | 합치기와 교착 | 잠금 순서, 409, 테스트 |
| R6 | 복구 409로 헷갈림 | 삭제 목록에 막는 세트 표시 |
| R7 | 대결 영구 삭제 | 확인 창 개수, PURGE 스냅샷, 매일 백업 |
| R8 | 이력 JSON에 실명·닉네임 | 담당자만, 로그 출력 금지 |
| R9 | 등록 경로 회귀 | 기존 테스트 유지, 별도 진입점 |
| R10 | `Instant` 정밀도 | 첫 통합 테스트 [확인 필요] |
| R11 | 운영 마이그레이션 | 백업 먼저, V8은 새 테이블만 |
| R12 | 번호 틈이 헷갈림 | §14 Q1, P2 번호 정리 |
| R13 | 삭제 세트 때문에 선수 삭제 409 | 의도된 동작, 안내 문구 |
| R14 | 수동 입력이 "가져오기"로 보임 | "이미지 없음" 표기(P2) |
| R15 | 테스트 날짜 충돌 | 7000~7099년 예약 |

## 14. 사용자 결정 (2026-10-22 확정 — 위 절과 다르면 이 절이 우선)

| # | 질문 | 결정 |
|---|---|---|
| 1 | 중간 세트 삭제 시 번호 재정렬 | 추천대로: **하지 않음**(복구하면 원래 번호, P2 번호 정리) |
| 2 | 수정으로 피어리스 겹침 | 추천대로: **앞 세트와 겹치면 저장 불가, 뒤 세트와 겹치면 경고 후 저장** |
| 3 | 1세트 진영 수정 시 A/B | 추천대로: **선수 구성을 따라감**(진영 경고만, 다른 세트 불변) |
| 4 | 복구 충돌 | 추천대로: **지문·번호 충돌은 막고 막는 세트 표시, 피어리스는 경고 후 복구** |
| 5 | 대결 삭제 범위 | 추천대로: **유효 세트 없는 대결만**, 삭제 표시 세트는 함께 영구 삭제, 다음 대결이 이어가기면 막음 |
| 6 | 변경 이력 | **만들지 않음**(사용자: 관리자가 극소수라 필요 없음). → `V8__audit_log`·`audit/**`·이력 API·`SetHistoryPage`·"최근 변경" 탭·PURGE 스냅샷 **모두 제외**. 기록 관리 화면은 "삭제된 세트" 목록(복구)만 |
| 7 | 사유 입력 | **없음**(삭제·복구·수정 요청에 `reason` 없음) |
| 8 | 수동 입력 경기 시간 | **필수**(수정 화면은 원래 시간을 모르는 가져오기 세트가 있어 "모름" 허용 유지) |
| 9 | 팀 교환 화면 | 추천대로: **만들지 않음**, 유저플로우 §6.1 문구를 "등록 시 자동 감지로 대체"로 수정 |
| 10 | 배포 | 추천대로: **PR-A 삭제·복구 → PR-B 수정 → PR-C 수동 입력·대결 삭제** |
| 11 | 피어리스 방식 변경 | 추천대로: 이번엔 안 만듦(P2) |
| 12 | 세트 옮기기 | 추천대로: 이번엔 삭제 후 재등록·수동 입력(P2) |

### 14.1 결정 6·7에 따른 범위 조정
- DB 변경 없음(V8 없음). `game_set`의 `deleted_at`·`updated_at`만 사용.
- PR-A = 세트 삭제·복구·삭제 목록(+삭제 세트 원본 보기 허용). 세트별 이력·최근 변경 없음.
- 작업 분할의 BE-A는 `FearlessConflicts`·`BanOrder` 역변환만 맡는다(`audit/**` 없음). FE-E는 기록 관리(삭제된 세트 탭만)·묶음 상세 버튼·세트 상세 담당자 버튼.
- 대결 삭제 시 영구 삭제되는 세트는 기록이 남지 않으므로 확인 창에 "삭제된 세트 N개도 완전히 지워지고 되돌릴 수 없어요"를 분명히 표시(복구 수단은 매일 백업).
- ADR 015(이력) 제외, ADR 016(수정 규칙)만 작성.

## 15. ADR 후보
- ~~ADR 015: 변경 이력~~ — 결정 6으로 제외.
- ADR 016: 저장된 세트 수정 규칙(A/B 고정, 피어리스 앞쪽 차단·뒤쪽 경고, 번호 틈 유지, 복구 충돌 409, `updated_at` 동시 수정 보호, `team_swap` 없음, 대결 삭제·영구 삭제).
