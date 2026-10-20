# M4 설계: 추출 파이프라인, 세트 등록·확인 화면

> 작성: architecture-planner(Opus), 2026-10-17. 리뷰 전 초안이다. 리뷰 반영 내역은 문서 끝 "리뷰 반영" 절에 추가한다.
> 버전에 따라 동작이 달라질 수 있는 부분은 **[확인 필요]**로 표시했다. 대부분 Phase 0 테스트에서 바로 판가름 난다.
> 기준 문서: `docs/유저플로우.md` §3.3·§5, `docs/design/m3-design.md` §5·§9, ADR 003·004·006.

기존 계획에서 바뀌는 점:
- `extraction_job`에는 **추출 사실만** 저장한다. 사실이란 화면 순서의 숫자·닉네임과 아이콘 후보다. 선수 매칭, 1팀/2팀 판별, 검증 같은 "초안"은 조회할 때마다 계산한다. 계획 문서의 "초안 저장"을 대체한다.
- 세트 등록 유스케이스(초안·검사·저장)는 새 패키지 `registration`에 둔다. 엔티티는 계속 `session`에 있다. 챔피언은 계획대로 최상위 `champion` 패키지에 둔다.
- 계획에 있던 2개 API(`extractions`, `sets`)에 더해 `draft`, `sets/check`(저장 없는 검사), `registration-context`, `champions` 조회를 추가한다.

## 0. 결정 요약

| # | 결정 | 이유 |
|---|---|---|
| D1 | 작업에는 추출 사실(화면 순서 행, 숫자, 박스, 챔피언 상위 3개 후보와 점수)만 JSON으로 저장한다. 초안은 `GET draft` 때 현재 DB(선수·별칭·이전 세트)로 계산한다 | 추출 이후 선수·별칭이 바뀌어도 초안이 낡지 않는다. 초안 검사와 저장 검사가 같은 코드(`SetEvaluator`) 하나를 쓴다 |
| D2 | 패키지: `ingest.extract`(Gemini·재시도), `ingest.image`(검사·저장소), `ingest.job`(작업·비동기·API), `champion`(Data Dragon·아이콘 매칭·목록 API), `registration`(초안·검사·저장) | 작업자별로 파일이 겹치지 않는다. `registration`은 다른 패키지를 쓰기만 하고, 다른 패키지는 `registration`을 쓰지 않는다 |
| D3 | `@Async("extractionExecutor")`는 별도 빈(`ExtractionRunner`)에 단다. 실행기는 스레드 1개, 대기열 5개다. 작업 행이 **커밋된 뒤에** 실행을 넘긴다 | 무료 등급 한도가 있고 담당자도 1명이다. 같은 빈 안에서 자기 메서드를 부르면 프록시를 거치지 않아 비동기가 안 걸리고, 커밋 전에 넘기면 실행 스레드가 아직 없는 행을 읽는다. 둘 다 피한다 |
| D4 | 앱이 시작될 때(`ApplicationReadyEvent`) 남아 있는 `QUEUED`·`RUNNING` 작업을 모두 `FAILED(INTERRUPTED)`로 바꾼다. 자동으로 이어서 실행하지 않는다 | 인스턴스가 1대라 안전하고 단순하다. 담당자가 같은 이미지를 다시 붙여넣으면 새 작업이 생긴다 |
| D5 | 재시도는 실험 코드를 옮기되 대기·시계·지터를 주입한다. 최대 4회, 408·429·5xx만 재시도, 대기는 5·10·20초에 0~999ms를 더하고, 요청 하나 90초·작업 전체 5분 제한이다. SDK 자체 재시도는 끈다 | AGENTS §8 지적 사항. 시간을 실제로 기다리지 않고 테스트할 수 있다 |
| D6 | `champion_key`에는 Data Dragon `id` 문자열(예: `Ahri`, `MonkeyKing`)을 저장한다. 아이콘은 프런트가 Data Dragon CDN에서 직접 불러온다. 서버 카탈로그는 처음 쓸 때 불러오고 디스크에 캐시하며, 네트워크가 안 되면 캐시된 최신 버전으로 대체한다 | id는 사람이 읽을 수 있고 아이콘 URL에 그대로 쓰인다. 정답지 형식과도 같다. 숫자 키보다 덜 안정적이라는 위험이 있다(§9 R9, ADR 008) |
| D7 | `ImageStore` 인터페이스(`put`/`get`)를 두고, M4 구현은 `LocalImageStore`로 한다. 저장 위치 `${IMAGE_STORE_DIR:../data/images}`, 키 `{hash 앞 2자}/{hash}.{png\|jpg}`. 저장소 루트 `data/`는 git 제외 | 해시를 키로 쓰면 같은 이미지는 같은 키라 다시 써도 무해하다(멱등). M7에서 S3 구현으로 바꿀 때 키 형식을 그대로 쓴다. 이미지에 친구 닉네임이 들어 있어 git에서 빼야 한다 |
| D8 | 이미지 해시 1차 검사: 같은 해시의 **유효 세트**가 있으면 409 `DUPLICATE_IMAGE`. 같은 대결·같은 해시로 저장 안 됐고 실패하지 않은 작업이 있으면 그 작업을 돌려준다(200, `reused=true`) | 새로고침한 뒤 다시 붙여넣어도 Gemini 한도를 아낀다. 최종 중복 방지는 지문 UNIQUE가 맡는다 |
| D9 | 저장 명령 DTO는 `SetDraft`와 따로 둔다(cs, screenRow, 선수 참조, addAlias 포함). 검증할 때만 `SetDraft`로 바꿔 `SetDraftValidator`에 넘긴다 | m3 §9-10. 도메인 변경이 없어 M2 테스트가 그대로 유지된다 |
| D10 | 행마다 선수 참조는 `playerId`(기존 선수) 또는 `null`(이 행의 `nickname`으로 미등록 선수 생성)이다. `addAlias`는 필수 boolean이고, 서버는 `MatchType`을 보지 않는다 | m3 §5 별칭 저장 규칙 |
| D11 | 밴 턴 순서와 1팀/2팀 판별은 서버가 최종 결정한다. 명령에는 팀별 밴을 화면 순서로 담고, 서버가 `BanOrder.build`로 턴을 정한다. 프런트는 B1~R5 표시용으로만 같은 계산을 한다 | 규칙의 기준을 서버 하나로 둔다. 프런트 계산은 화면 표시 전용이다 |
| D12 | `POST …/sets/check`(부작용 없는 검사)가 오류·경고·판별 결과를 돌려준다. 저장 때 서버가 같은 검사를 다시 한다. 규칙 위반은 422 `SET_RULES_VIOLATED` | 선수 연결을 바꾸면 1팀/2팀 판별이 바뀌는데, 이전 세트 정보는 서버에만 있다. 경고는 저장을 막지 않는다 |
| D13 | 저장은 트랜잭션 하나로 한다. 순서: 대결 행 잠금 → 작업 행 잠금 → 평가 → 선수·별칭 생성 → GameSet 저장 → 작업 연결. 트랜잭션 밖에서 UNIQUE 위반을 제약 이름으로 구분해 409로 바꾼다 | M3의 `InhouseSessionService` 패턴과 같다. 실패하면 생성한 선수·별칭까지 모두 되돌린다 |
| D14 | `set_no`는 유효 세트 최댓값 + 1로 서버가 정하고, 업로드 화면에 "N세트로 등록"이라고 보여준다 | 피어리스와 직전 세트 판별이 "앞 세트가 모두 있다"는 전제에 기대고 있다. 단, 유저플로우 §5.2의 "담당자 지정"과 달라 사용자 확인이 필요하다(§9 Q2) |
| D15 | 작업 상태 응답에는 `Instant` 필드를 넣지 않는다. 경과 시간은 프런트가 직접 잰다 | `spring.jackson.time-zone: Asia/Seoul` 문제(AGENTS §7)를 M4에서 만들지 않는다 |
| D16 | 새 의존성은 백엔드 `com.google.genai:google-genai` 하나다. Awaitility는 넣지 않고 테스트용 폴링 헬퍼로 대신한다 | 근거 없는 기술은 넣지 않는다는 원칙 |
| D17 | 프런트: `document` 전체에서 `paste` 이벤트를 받는다. 폴링은 TanStack `refetchInterval` 2초. 확인 화면 상태는 (팀 top/bottom, 화면 행)을 키로 둔다 | 포지션이나 진영을 바꿔도 행 데이터는 제자리에 있다. 정렬과 턴 표시는 계산해서 만든다 |

## 1. 스키마 (`V3__extraction_job.sql`)

M3 §1.1 공통 규칙을 그대로 따른다(InnoDB/utf8mb4_0900_ai_ci 명시, 열거형은 `ascii_bin` + CHECK, 시각은 `DATETIME(6)` UTC, 제약 이름은 `uk_/fk_/ck_/ix_`, DB DEFAULT 시각 없음). 이미 적용된 V2는 고치지 않는다.

```sql
-- 결과창 추출 작업(M4). 추출 사실만 저장하고 초안은 조회 시 계산한다. 설명: docs/design/m4-design.md §1·§2, ADR 009
CREATE TABLE extraction_job (
  id                BIGINT       NOT NULL AUTO_INCREMENT,
  duel_id           BIGINT       NOT NULL,
  status            VARCHAR(10)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  top_side          VARCHAR(4)   CHARACTER SET ascii COLLATE ascii_bin NOT NULL,  -- 업로드 전 선택값(확인 화면에서 바뀔 수 있음 → 저장 명령이 최종)
  capturer_position VARCHAR(10)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  image_hash        VARCHAR(64)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL,  -- SHA-256 hex(업로드 바이트)
  image_key         VARCHAR(255) NOT NULL,                                        -- ImageStore 키
  result_json       JSON         NULL,                                            -- ExtractionResult v1. SUCCEEDED일 때만
  error_code        VARCHAR(40)  CHARACTER SET ascii COLLATE ascii_bin NULL,      -- FAILED일 때만. 원문 메시지는 저장하지 않음
  attempt_count     INT          NOT NULL,
  game_set_id       BIGINT       NULL,                                            -- 저장된 세트(1:1). NULL = 아직 저장 안 됨
  created_at        DATETIME(6)  NOT NULL,
  started_at        DATETIME(6)  NULL,
  finished_at       DATETIME(6)  NULL,
  updated_at        DATETIME(6)  NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_extraction_job_game_set (game_set_id),
  KEY ix_extraction_job_duel_id (duel_id),
  KEY ix_extraction_job_image_hash (image_hash),
  CONSTRAINT fk_extraction_job_duel     FOREIGN KEY (duel_id)     REFERENCES duel (id)     ON DELETE RESTRICT,
  CONSTRAINT fk_extraction_job_game_set FOREIGN KEY (game_set_id) REFERENCES game_set (id) ON DELETE RESTRICT,
  CONSTRAINT ck_extraction_job_status   CHECK (status IN ('QUEUED','RUNNING','SUCCEEDED','FAILED')),
  CONSTRAINT ck_extraction_job_top_side CHECK (top_side IN ('BLUE','RED')),
  CONSTRAINT ck_extraction_job_capturer_position CHECK (capturer_position IN ('TOP','JUNGLE','MID','ADC','SUPPORT')),
  CONSTRAINT ck_extraction_job_error    CHECK ((status = 'FAILED') = (error_code IS NOT NULL)),
  CONSTRAINT ck_extraction_job_result   CHECK ((status = 'SUCCEEDED') = (result_json IS NOT NULL)),
  CONSTRAINT ck_extraction_job_saved    CHECK (game_set_id IS NULL OR status = 'SUCCEEDED'),
  CONSTRAINT ck_extraction_job_attempts CHECK (attempt_count BETWEEN 0 AND 10)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
```

설계 메모:
- `error_code`에는 CHECK를 걸지 않는다. 코드가 늘 때마다 마이그레이션하지 않으려는 것이고, 값은 Java enum `ExtractionErrorCode`로 제한한다.
- `game_set`은 바꾸지 않는다. 저장할 때 작업의 `image_hash`·`image_key`를 `GameSet.create`에 넘기므로 `ck_game_set_image` 짝 규칙이 지켜진다.
- 이미지 형식(png/jpeg)은 컬럼에 두지 않고 저장소에서 읽은 바이트의 시그니처로 판별한다.
- **[확인 필요]** MySQL은 FK 참조 동작(CASCADE·SET NULL)에 쓰이는 컬럼을 CHECK에 쓰지 못한다(오류 3823). 여기서는 `RESTRICT`라 괜찮을 것으로 본다. 실패하면 `ck_extraction_job_saved`를 빼고 서비스 규칙으로 옮긴다.
- **[확인 필요]** `result_json`의 JPA 매핑: `String` + `@JdbcTypeCode(SqlTypes.JSON)`이 Hibernate 7에서 원문 그대로 저장되고 `validate`를 통과하는지 확인한다. 안 되면 컬럼을 `MEDIUMTEXT`로 바꾼다(아직 V3를 적용하기 전이라 바꿀 수 있다). 직렬화는 앱이 Jackson 3 `JsonMapper`로 직접 한다.

`result_json` 형식(`ExtractionResult` v1, Phase 0에서 record로 고정한다):
- `schemaVersion: 1`, `model`, `dataDragonVersion`, `attempts[{number, httpStatus, latencyMs, backoffMs}]`
- `topResult: WIN|LOSS`(Gemini의 "승리/패배"를 변환), `shotDate: "2026-10-12"`, `duration: "25:42"`
- `top`/`bottom`: `{kills, deaths, assists, gold, rows[5], bans[5]}`
  - row: `{nickname(읽은 그대로), kills, deaths, assists, cs, gold, portraitBox[4], champion}`
  - ban: `{box[4], champion}`
  - champion: `{candidates[{id, score}] 최대 3개, margin, cropFailed}`
- 닉네임이 들어 있으므로 로그에는 남기지 않는다(DB에는 어차피 닉네임이 저장된다).

## 2. 백엔드 패키지·클래스

코드 주석은 "역할 + 설계 문서/ADR 경로"로 단다. AGENTS §2에 따라 `docs/study/` 경로는 코드에 적지 않는다.

### 2.1 `ingest.extract` (Gemini 이식)
| 클래스 | 역할 |
|---|---|
| `ScoreboardExtractor` (인터페이스) | `ExtractedScoreboard extract(byte[] image, String mimeType)`. 공급자를 바꿀 때의 경계. 실패는 `ExtractionException` |
| `ExtractedScoreboard`, `ExtractedTeam`, `ExtractedRow`, `Box` (record) | 검증을 통과한 타입 결과. 실험 코드의 `JsonNode` 반환을 대체한다 |
| `ExtractionException` | `errorCode`, `httpStatus`(nullable), `transientError`를 담는다. 메시지에 원문을 넣지 않는다 |
| `GeminiScoreboardExtractor` | SDK 어댑터. 실험 코드의 `schema()`와 프롬프트를 그대로 옮기고 `responseJsonSchema`, `temperature 0`, `HttpOptions.timeout(요청 제한)`, SDK 재시도 1회(끔)를 쓴다. `response.text()`를 `ScoreboardResponseValidator`에 넘긴다 |
| `ScoreboardResponseValidator` | 실험 코드 `validate`를 옮긴 순수 함수. 배열 여부, 개수 5, 숫자(정수·int 범위·0 이상), 박스 0~1000·면적, STRICT 날짜, `mm:ss`를 검사한다. 실패하면 `EXTRACTION_INVALID_RESPONSE` |
| `RetryingExtractor` | `ScoreboardExtractor`를 감싼다. `RetryPolicy(maxAttempts=4, baseDelay=5s, maxDelay=60s, deadline=5m)`, 주입받는 `Sleeper`, `Clock`, `LongSupplier jitter`. 시도 기록(`Attempt`)을 돌려준다. 다음 대기가 작업 제한 시간을 넘으면 `EXTRACTION_TIMEOUT`. `InterruptedException`은 인터럽트 플래그를 복구한 뒤 `INTERRUPTED`로 바꾼다 |
| `ExtractorConfig` | API 키(`@Value("${GEMINI_API_KEY:}")`, record 속성으로 받지 않음)가 비어 있으면 `DisabledScoreboardExtractor`(항상 `EXTRACTION_NOT_CONFIGURED`)를 만든다. 키가 없는 CI에서도 기동은 성공해야 한다 |

### 2.2 `champion` (Data Dragon·아이콘 매칭 이식)
| 클래스 | 역할 |
|---|---|
| `Champion` (record) | `id`, `key`(숫자 문자열), `name`(ko_KR) |
| `ChampionCatalog` (인터페이스) | `Optional<CatalogSnapshot> current()`. `CatalogSnapshot(version, champions, IconMatcher matcher)`, `contains(id)` |
| `DataDragonSource` (인터페이스) + `HttpDataDragonSource` | `byte[] fetch(String path)`. 주입받은 `HttpClient`(빈)를 쓴다. 테스트는 Map 기반 가짜 구현 |
| `DataDragonChampionCatalog` | 처음 호출될 때 불러온다. 순서: `versions.json` → `champion.json`(ko_KR) → 아이콘. 디스크 캐시는 `${DDRAGON_CACHE_DIR:../data/ddragon}/{version}/`. 버전 확인은 12시간마다 한다. 네트워크가 실패하면 캐시된 최신 버전을 쓰고, 그것도 없으면 empty. `AtomicReference`로 교체하고, 동시에 부르면 한 번만 불러온다. 실험 코드의 id·버전 형식 검사를 유지한다 |
| `IconMatcher`, `Boxes` | 실험 코드를 그대로 옮긴다(`PICK`/`BAN` 설정, `SIZE` 48, 같은 점수일 때 id로 정렬). 덧붙이는 것은 하나뿐이다: 생성자에서 공식 아이콘을 설정별로 미리 `prepare`해 둔다. `top(crop, settings, 3)`는 후보와 차이값을 돌려준다 |
| `ChampionController` | `GET /api/admin/champions`(§5) |

### 2.3 `ingest.image`
| 클래스 | 역할 |
|---|---|
| `ImageInspector` | 바이트 상한(10MB)과 시그니처(PNG `89 50 4E 47`, JPEG `FF D8 FF`)를 확인한다. `ImageIO`로 크기만 먼저 읽어 4096×4096을 넘으면 거절한다(압축 폭탄 방지). SHA-256 hex를 계산하고 `InspectedImage(bytes, mimeType, hash, width, height)`를 돌려준다 |
| `ImageStore` (인터페이스) | `String put(String hash, String mimeType, byte[] bytes)`, `byte[] get(String key)` |
| `LocalImageStore` | 루트 아래에 원자적으로 쓴다(임시 파일 → move). 같은 키가 이미 있으면 다시 쓰지 않는다. 키 형식은 정규식으로 검사하고, 정규화한 경로가 루트 안에 있는지 확인한다(경로 탈출 방지) |

### 2.4 `ingest.job` (작업 상태 머신)
상태 전이:
```
QUEUED ──(실행기가 집어감: UPDATE … SET status='RUNNING' WHERE id=? AND status='QUEUED')──▶ RUNNING
RUNNING ──성공──▶ SUCCEEDED (result_json)        RUNNING ──실패──▶ FAILED(error_code)
QUEUED ──실행기 거절(대기열 가득)──▶ FAILED(BUSY)
QUEUED/RUNNING ──앱 재시작──▶ FAILED(INTERRUPTED)
RUNNING ──조회 시 started_at이 10분 넘게 지남──▶ FAILED(TIMEOUT)  (조건부 UPDATE)
SUCCEEDED ──세트 저장──▶ SUCCEEDED + game_set_id (끝)
FAILED는 끝. 재시도는 새 작업
```

| 클래스 | 역할 |
|---|---|
| `ExtractionJob` (엔티티) | 위 전이만 허용하는 메서드(`markRunning`·`succeed`·`fail`·`linkGameSet`). enum 매핑은 m3 §9-8과 같다 |
| `ExtractionJobStatus`, `ExtractionErrorCode` (enum) | §5 표 참고 |
| `ExtractionJobRepository` | 조건부 상태 UPDATE(`@Modifying`), 재사용 후보 조회(`duelId`, `imageHash`, `gameSetId IS NULL`, status ≠ FAILED), 저장용 `findByIdForUpdate`, 복구 대상 일괄 UPDATE |
| `ExtractionResult` (record 묶음) | §1의 JSON 형식. B가 쓰고 C가 읽는다(Phase 0에서 확정) |
| `ExtractionService` | `start`는 `@Transactional`이 아니다. 순서: 키 설정 확인 → 대결 존재 → `ImageInspector` → 유효 세트 해시 검사(409) → 재사용 작업 조회 → `ImageStore.put` → `TransactionTemplate`으로 작업 저장(커밋) → `runner.run(jobId)`. `TaskRejectedException`이면 `FAILED(BUSY)` + 503. `status(jobId)`는 오래된 RUNNING을 정리하는 일도 한다 |
| `ExtractionRunner` | `@Async("extractionExecutor") run(jobId)`. 예외를 절대 밖으로 던지지 않는다. 순서: RUNNING 선점(실패하면 끝) → 카탈로그 확인(없으면 Gemini를 부르기 전에 `CHAMPION_CATALOG_UNAVAILABLE`로 실패해 한도를 아낌) → 이미지 읽기 → `RetryingExtractor` → 박스 20개를 잘라 상위 3개 매칭(잘라내기 실패는 칸마다 `cropFailed`) → 결과 저장. 로그에는 jobId·상태·코드·지연만 남긴다 |
| `ExtractionJobRecovery` | `@EventListener(ApplicationReadyEvent)`로 QUEUED/RUNNING을 일괄 `FAILED(INTERRUPTED)`로 바꾼다. 인스턴스 1대가 전제다(§9 R10) |
| `ExtractionController` | `POST /api/admin/duels/{duelId}/extractions`(multipart), `GET /api/admin/extractions/{jobId}` |

`common/config/AsyncConfig`: `@EnableAsync`, `ThreadPoolTaskExecutor extractionExecutor`(core 1, max 1, queue 5, 이름 접두사 `extraction-`, `waitForTasksToCompleteOnShutdown=true`, `awaitTerminationSeconds=30`). `server.shutdown: graceful`은 이미 설정돼 있다. 종료할 때 끝나지 못한 작업은 다음 기동 때 복구 단계가 정리한다.

시간 제한 정리:

| 항목 | 값 | 설정 키 |
|---|---|---|
| Gemini 요청 하나 | 90초 [확인 필요: SDK `timeout`이 응답 수신 전체를 덮는지] | `app.gemini.request-timeout` |
| 시도 / 대기 | 4회 / 5·10·20초 + 0~999ms | `app.gemini.max-attempts` |
| 작업 전체 | 5분 | `app.gemini.job-deadline` |
| 오래된 RUNNING 판정 | 10분 | `app.extraction.stale-after` |
| 프런트 폴링 / 포기 | 2초 / 6분 | 프런트 상수 |

### 2.5 `registration`
| 클래스 | 역할 |
|---|---|
| `RegistrationContextService` | 대결 하나의 상태를 모은다. 다음 setNo(유효 max+1), 직전 세트(유효 세트 중 마지막)의 `top_team`과 TEAM1 선수 id 5명, 피어리스 제한(유효 세트 픽 전부), 알려진 별칭 목록(`KnownAlias` + 등록 여부) |
| `SetEvaluator` | 순수 계산에 가깝다. (context, `SetRegistrationRequest`) → `Evaluation`. §3 4~7단계를 맡는다. 미확정 행은 `playerId=null`(검증 오류), 새 선수 행은 자리표시 id `new:{top\|bottom}:{row}`. 챔피언 id가 카탈로그에 없으면 오류로 넣는다 |
| `DraftService` | `ExtractionResult` + context → `DraftResponse`. 기본 명령을 만든 뒤 `SetEvaluator`로 처음 평가한다 |
| `SetRegistrationService` | `check`(읽기 전용 트랜잭션)와 `register`(§4) |
| `RegistrationController` | `GET …/registration-context`, `GET /api/admin/extractions/{jobId}/draft`, `POST …/sets/check`, `POST …/sets` |

### 2.6 기존 파일 변경(소유자 1명씩)
- `session/GameSetRepository`(Phase 0): `findFirstByImageHashAndDeletedAtIsNull`, `findByFingerprintAndDeletedAtIsNull`, `findFirstByDuelIdAndDeletedAtIsNullOrderBySetNoDesc`, 유효 max setNo `@Query`, 대결의 유효 세트 픽 목록 `@Query`(`GameSetPlayer` → `gameSet.duelId`, `deletedAt is null`), 세트 하나의 TEAM1 선수 id `@Query`.
- `session/DuelRepository`(Phase 0): `findByIdForUpdate`(PESSIMISTIC_WRITE).
- `player/PlayerService`(C): `createUnregistered(rawNickname)`(정규화·충돌 409·`Player.unregistered` + 별칭)와 `addAliasIfAbsent(playerId, raw)`(같은 선수가 이미 가진 키면 건너뜀, 다른 선수면 기존 409). 둘 다 호출자 트랜잭션에 참여한다.
- `common/web/ApiExceptionHandler`(B): `MaxUploadSizeExceededException`은 413 `IMAGE_TOO_LARGE`, 이미지 part가 빠지면 400 `IMAGE_REQUIRED` [확인 필요: Spring 7 기본 처리기가 이미 413을 내는지, 상태 이름이 `CONTENT_TOO_LARGE`인지].
- 패키지 의존 방향: `ingest`는 해시 검사 때문에 `session`(GameSetRepository)을 쓰고, `registration`은 `ingest`·`session`·`player`·`champion`을 쓴다. 클래스끼리의 순환은 없다.

## 3. 초안 생성 흐름

"어디서 → 어디로, 어떤 데이터가, 그다음 무엇이":

1. **추출(비동기, `ExtractionRunner`)**: 이미지 바이트가 Gemini로 가서 화면 순서 JSON으로 돌아오고, `ScoreboardResponseValidator`가 `ExtractedScoreboard`로 만든다. 박스를 원본 픽셀로 바꿔 자르고(`Boxes`), `IconMatcher`가 상위 3개를 고른다. 결과는 `ExtractionResult`로 `result_json`에 저장된다.
2. **폴링**: 프런트가 2초마다 `GET status`를 부르고, `SUCCEEDED`가 되면 `GET draft`를 한 번 부른다.
3. **포지션 배정(`DraftService`)**: 위쪽 팀은 `PositionAssignment.topTeamPositions(job.capturerPosition)`, 아래쪽 팀은 `bottomTeamPositions()`. 화면 행 번호는 그대로 둔다.
4. **밴 순서**: 초안은 화면 순서 그대로(팀별 슬롯 1~5, 1순위 후보로 채움) 내려보낸다. 턴(B1~R5)은 `topSide`에 따라 정해지므로 프런트가 표시하고, 서버는 저장할 때 `BanOrder.build(topSide, topBans, bottomBans)`로 확정한다.
5. **닉네임 매칭**: 행마다 `NicknameMatcher.match(readNickname, knownAliases)`. 매칭 결과에 따른 기본값은 다음과 같다.
   - EXACT / ONE_CHAR_AUTO / TRUNCATED_AUTO: `playerId`를 채우고 `addAlias=false`
   - CANDIDATES: `playerId=null`(담당자가 골라야 함)
   - NEW_UNREGISTERED이고 잘리지 않음: 새 선수(`playerId=null`, nickname = 정규화 표기)
   - NEW_UNREGISTERED이고 잘림: 미확정, 전체 닉네임 입력 필요
   - `match`가 빈 닉네임으로 예외를 던지면(예: "…"만 있음) 잘린 NEW와 같이 처리한다.
6. **1팀/2팀 판별(`SetEvaluator`)**:
   - setNo = 1이면 `teamForFirstSet(topSide)`.
   - 그 밖에는 위쪽 팀 id 5개(실제 id와 자리표시 id)가 서로 다를 때만 `detectTopTeam(직전 TEAM1, 위쪽)`.
   - 미확정이나 중복이 있으면 판별을 보류하고 `topTeam=null`(오류 "선수를 모두 고르면 1팀/2팀을 판별합니다").
   - 새 선수는 직전 세트와 겹치지 않으므로 자리표시 id로 판별해도 실제 id로 판별한 결과와 같다.
7. **지문**: `SetFingerprint.of(shotDate, parseDuration(duration), (위쪽 kills, gold), (아래쪽 kills, gold))`로 만들고, `findByFingerprintAndDeletedAtIsNull`이 있으면 `duplicateSet`을 채운다(추출 직후 1차 검사, 유저플로우 §5.1).
8. **검증**: 명령을 `SetDraft`로 바꾼다(cs 제외). `ValidationContext(setNo, topTeam, restricted)`로 `SetDraftValidator.validate`를 돌리고, 아래 서비스 오류를 덧붙인다.
   - 알 수 없는 챔피언 id
   - 같은 닉네임 키로 새 선수가 두 명
   - 새 선수 닉네임이 이미 다른 선수의 별칭
   - 잘린 닉네임으로 새 선수 만들기 또는 별칭 추가 시도
   - 경기 시간 형식
   - 판별 보류
   - 결과는 `Evaluation{setNo, topTeam, detection{method: FIRST_SET|OVERLAP|PENDING, overlap}, team1Side, fingerprint, duplicateSet, errors, warnings}`.

확인 화면에서 무언가를 고치면 프런트가 디바운스(600ms) 후 `POST check`를 부르고, 4~8단계를 같은 코드로 다시 계산한다.

## 4. 저장 명령 DTO와 저장 트랜잭션

### 4.1 `SetRegistrationRequest` (check와 저장이 같은 본문을 쓴다)
```
{ extractionJobId: number,                       // M4 필수(수동 입력은 §9 Q1 결정 후 null 허용)
  topSide: "BLUE"|"RED", capturerPosition: Position,
  topResult: "WIN"|"LOSS", shotDate: "2026-10-12", duration: "25:42",
  top:    TeamInput, bottom: TeamInput }
TeamInput { displayedKills, displayedDeaths, displayedAssists, displayedGold,   // int ≥ 0
            rows: RowInput[5], bans: (string|null)[5] }                         // 화면 순서
RowInput  { screenRow: 1..5, position: Position, nickname: string,             // 확인된 표기(읽은 값 또는 담당자가 고친 전체 닉네임)
            playerId: number|null, addAlias: boolean,                           // null = nickname으로 미등록 선수 생성
            championKey: string|null, kills, deaths, assists, cs, gold }
```
- 400 `VALIDATION_FAILED`(형태 오류): rows·bans 개수 5가 아님, 팀 안 screenRow 중복, 숫자 음수, 필수 필드 null, `nickname` 공백, `addAlias=true`인데 `playerId=null`.
- 그 밖의 내용 오류(빈 챔피언·밴, 중복, 피어리스, 잘린 닉네임 규칙, 알 수 없는 챔피언, 시간 형식)는 422 `SET_RULES_VIOLATED`의 `violations`로 보낸다. 화면은 한 경로로만 처리하면 된다.
- 응답 201: `{setId, duelId, setNo, topTeam, warnings, createdPlayers[{playerId, nickname}], addedAliases[{playerId, nickname}]}`. 세트 조회 API가 M5에 생기므로 `Location`은 그때 붙인다.

### 4.2 `register` 순서 (`SetRegistrationService`, `@Transactional` 아님, 본체는 `TransactionTemplate`)
1. `DuelRepository.findByIdForUpdate(duelId)`: 없으면 404. 같은 대결의 저장을 줄 세워 setNo·피어리스 계산을 결정적으로 만든다.
2. `ExtractionJobRepository.findByIdForUpdate(jobId)`:
   - 없으면 404 `EXTRACTION_NOT_FOUND`
   - 다른 대결이면 400 `EXTRACTION_DUEL_MISMATCH`
   - SUCCEEDED가 아니면 409 `EXTRACTION_NOT_READY`
   - `game_set_id`가 있으면 409 `EXTRACTION_ALREADY_SAVED`(+`savedSetId`)
3. context를 불러오고 `SetEvaluator.evaluate`. errors가 있으면 422(아직 아무것도 쓰지 않았다). `duplicateSet`이 있으면 409 `DUPLICATE_SET`.
4. 선수 확정:
   - `playerId=null` 행: `PlayerService.createUnregistered(nickname)`
   - `addAlias=true` 행: `PlayerService.addAliasIfAbsent(playerId, nickname)`
   - 그 밖의 `playerId`: 존재 확인(없으면 400 `PLAYER_NOT_FOUND`)
   - 별칭 충돌은 PlayerService가 409 `ALIAS_CONFLICT`(+`conflicts`)로 던지고 전체가 롤백된다.
5. 값 계산:
   - `team1Side = topTeam==TEAM1 ? topSide : topSide.opposite()`
   - `winnerTeam = topResult==WIN ? topTeam : topTeam.other()`
   - team1/team2 표시 합계는 topTeam 기준으로 위쪽/아래쪽에서 고른다.
6. `GameSet.create(..., topTeam, shotDate, durationSec, team1, team2, job.imageHash, job.imageKey)` → `addPlayer` 10번(screenRow·cs 포함) → `BanOrder.build`로 `addBan` 10번 → `saveAndFlush`.
7. `job.linkGameSet(set.id())` → flush. `uk_extraction_job_game_set`이 1:1을 보장한다.
8. **트랜잭션 밖**에서 `DataIntegrityViolationException`을 잡아 제약 이름으로 바꾼다 [확인 필요: Hibernate가 넘기는 이름이 `game_set.uk_…`인지 `uk_…`인지 → `endsWith`로 비교].
   - `uk_game_set_active_fingerprint` → 409 `DUPLICATE_SET`. 새 읽기 트랜잭션에서 기존 세트를 찾아 `existingSetId`·`existingDuelId`·`existingSetNo`를 붙인다.
   - `uk_game_set_duel_active_set_no` → 409 `SET_NO_CONFLICT`
   - `uk_extraction_job_game_set` → 409 `EXTRACTION_ALREADY_SAVED`
   - 그 밖에는 다시 던진다(500).
   - 교착(1213)은 기존 처리기가 409 `CONCURRENT_REQUEST_CONFLICT`로 낸다.

주의:
- 피어리스 제한 = 이 대결의 유효 세트 전체의 픽이다. 새 setNo가 max+1이므로 "이전 세트 전부"와 같다. 대결 행 잠금 안에서 읽으므로 동시 저장 경쟁이 없다.
- `PlayerService`의 `@Transactional` 메서드가 ApiException을 던지면 바깥 트랜잭션이 rollback-only가 된다. 원래 전부 실패해야 하는 상황이라 의도한 동작이다.
- 자동 연결 행에 `addAlias=true`가 와도 서버는 막지 않는다(MatchType으로 추측하지 않는다는 원칙). 기본값과 비활성화는 UI가 책임진다.

## 5. API (모두 `/api/admin/**` = ADMIN + CSRF. 오류는 ProblemDetail + `code`)

| 메서드·경로 | 요청 | 성공 | 오류 |
|---|---|---|---|
| `GET /api/admin/duels/{duelId}/registration-context` | - | 200 `{duelId, sessionId, sessionStartDate, duelSeq, nextSetNo, defaultTopSide\|null, restrictedChampions[id], registeredSets[{id, setNo, team1Side, winnerTeam, topTeam}]}` | 404 `DUEL_NOT_FOUND` |
| `GET /api/admin/champions` | - | 200 `{version, champions[{id, key, name, iconUrl}]}`(한글 이름순, iconUrl은 Data Dragon CDN) | 503 `CHAMPION_CATALOG_UNAVAILABLE` |
| `POST /api/admin/duels/{duelId}/extractions` | multipart: `image`(png/jpeg ≤10MB), `topSide`, `capturerPosition` | 202 `{jobId, status, reused:false}` + `Location: /api/admin/extractions/{jobId}` / 200 `{…, reused:true}` | 400 `VALIDATION_FAILED`·`IMAGE_REQUIRED`·`UNSUPPORTED_IMAGE`·`IMAGE_DIMENSIONS_EXCEEDED`, 413 `IMAGE_TOO_LARGE`, 404 `DUEL_NOT_FOUND`, 409 `DUPLICATE_IMAGE`(+`existingSetId`, `existingDuelId`, `existingSetNo`), 503 `EXTRACTION_NOT_CONFIGURED`·`EXTRACTION_BUSY` |
| `GET /api/admin/extractions/{jobId}` | - | 200 `{jobId, duelId, status, errorCode\|null, attemptCount, topSide, capturerPosition, savedSetId\|null}` | 404 `EXTRACTION_NOT_FOUND` |
| `GET /api/admin/extractions/{jobId}/draft` | - | 200 `DraftResponse`(아래) | 404, 409 `EXTRACTION_NOT_READY`, 409 `EXTRACTION_ALREADY_SAVED`(+`savedSetId`) |
| `POST /api/admin/duels/{duelId}/sets/check` | `SetRegistrationRequest` | 200 `Evaluation` | 400 `VALIDATION_FAILED`, 404 |
| `POST /api/admin/duels/{duelId}/sets` | `SetRegistrationRequest` | 201(§4.1) | 400, 404, 409 `EXTRACTION_*`·`DUPLICATE_SET`·`SET_NO_CONFLICT`·`ALIAS_CONFLICT`·`CONCURRENT_REQUEST_CONFLICT`, 422 `SET_RULES_VIOLATED`(+`violations[]`, `warnings[]`), 503 `CHAMPION_CATALOG_UNAVAILABLE` |

`DraftResponse`:
```
{ jobId, duelId, topSide, capturerPosition,          // 작업의 초기 선택값
  topResult, shotDate, duration,
  top|bottom: { displayedKills, displayedDeaths, displayedAssists, displayedGold,
    rows[5]: { screenRow, position, readNickname, truncated,
               match: { type, playerId|null, playerDisplayName|null, similarity,
                        candidates[{playerId, displayName, nickname, similarity}], fullNicknameRequired, newPlayerName|null },
               kills, deaths, assists, cs, gold, portraitBox[4],
               champion: { candidates[{id, score}], margin, uncertain, cropFailed } },
    bans[5]: { slot, box[4], champion: {…} } },
  defaults: SetRegistrationRequest,                  // §3-5 기본값으로 채운 명령(프런트 초기 상태)
  evaluation: Evaluation }
```
`uncertain`은 `margin < app.extraction.uncertain-margin`이다. 기준값은 M0 리포트의 간격 분포로 정한다 [확인 필요: 리드가 `out/report.json`의 margin으로 보정. 그 전에는 밴 전부를 강조].

작업 실패 코드(`errorCode`):

| 코드 | 뜻 |
|---|---|
| `EXTRACTION_RATE_LIMITED` | 429 재시도 소진 |
| `EXTRACTION_UPSTREAM_UNAVAILABLE` | 408·5xx 재시도 소진 |
| `EXTRACTION_REJECTED` | 그 밖의 4xx(키·요청 오류) |
| `EXTRACTION_TIMEOUT` | 작업 제한 시간 초과 또는 오래된 RUNNING |
| `EXTRACTION_INVALID_RESPONSE` | 초안을 만들 수 없음 |
| `IMAGE_UNREADABLE` | 저장된 이미지를 읽을 수 없음 |
| `CHAMPION_CATALOG_UNAVAILABLE` | 아이콘 카탈로그 없음 |
| `BUSY` | 대기열 가득 |
| `INTERRUPTED` | 재시작·인터럽트 |
| `INTERNAL_ERROR` | 그 밖의 예외 |

프런트 `errorMessages.ts`에 한글 문구를 대응시킨다.

설정(`application.yml`):
- `spring.servlet.multipart.max-file-size: 10MB`, `max-request-size: 11MB` [확인 필요: Boot 4 키 이름, Tomcat `max-swallow-size` 때문에 큰 요청에서 연결이 끊기는지]
- `app.gemini.{model: ${GEMINI_MODEL:gemini-3.8-flash}, request-timeout, max-attempts, job-deadline}`
- `app.image-store.local-dir`, `app.ddragon.{cache-dir, refresh-interval}`
- `app.extraction.{max-image-bytes, max-image-side, stale-after, uncertain-margin}`
- `.env.example`에 `IMAGE_STORE_DIR`, `DDRAGON_CACHE_DIR`(선택)를 설명과 함께 추가하고, `.gitignore`에 `data/`를 추가한다.

## 6. 프런트

파일은 `src/pages/admin/register/*`, `src/api/registration.ts`, `src/api/registrationTypes.ts`, `src/styles/register.css`다. `routes.tsx`의 placeholder를 바꾸고, `client.ts`에 `formData` 옵션을 추가한다. FormData를 보낼 때는 Content-Type을 지정하지 않는다(브라우저가 boundary를 붙인다). CSRF 헤더와 403 재시도는 그대로 둔다.

- **`SetRegisterPage`**: 단계 상태 머신 `SETUP → EXTRACTING → REVIEW → SAVED`. 시작할 때 `registration-context`와 `champions`를 불러온다. 머리말에 "대결 N · M세트로 등록", 등록된 세트 목록, 피어리스 제한 챔피언을 보여준다.
- **`UploadStep`**:
  - 위쪽 팀 진영 라디오(기본값 `defaultTopSide`, 없으면 선택 안 됨)와 캡처한 사람 포지션 라디오.
  - `document`에서 `paste`를 받아 `clipboardData.items` 중 `image/*`를 `getAsFile()`로 꺼낸다. 파일 선택도 보조로 둔다.
  - 10MB와 형식은 클라이언트에서 먼저 검사한다. 미리보기는 objectURL로 보여준다.
  - 선택이 다 돼 있으면 붙여넣는 즉시 시작하고, 아니면 이미지를 들고 있다가 "추출 시작" 버튼을 켠다.
  - 409 `DUPLICATE_IMAGE`는 "이미 N세트로 등록된 결과창"이라고 안내한다.
- **`ExtractionProgress`**:
  - `useQuery(['admin','extractions',jobId], refetchInterval: 끝난 상태면 false, 아니면 2000)`.
  - 경과 시간을 표시하고 "보통 20초~1분 반"이라고 안내한다(ADR 003).
  - 6분이 지나면 폴링을 멈추고 "다시 시도"를 띄운다.
  - FAILED면 코드별 문구와 "다시 추출"을 보여준다. 다시 추출은 들고 있는 blob을 다시 보내 새 작업을 만든다.
- **`ReviewStep`**:
  - 상태는 `reviewModel.ts`(순수 함수)에 둔다. 초기값은 `draft.defaults`, 키는 (team, screenRow)다.
  - 경기 정보: 날짜, 경기 시간 `mm:ss`, 위쪽 팀 승리/패배, 진영·포지션 변경 컨트롤. 캡처한 사람 포지션을 바꾸면 위쪽 팀 포지션을 규칙대로 다시 매긴다.
  - 1팀/2팀 판별(겹침 n명 / 1세트 규칙 / 보류)을 표시한다.
  - 지문 중복이면 붉은 띠와 저장 비활성화. 숫자를 고치면 check로 다시 검사한다.
  - 원본 썸네일은 클릭하면 확대한다.
  - `TeamTable` × 2: 라인 순(탑→서폿)으로 정렬하고 각 행에 결과창 행 번호를 표시한다. 포지션 드롭다운으로 바꾸면 같은 팀의 해당 포지션 행과 맞바꾼다(`swapPositions`와 같은 규칙). K/D/A·CS·골드는 숫자 입력이고, 팀 표시 합계 4개도 고칠 수 있다.
  - `PlayerPicker`:
    - 상태 배지: 정확 / 자동(한 글자 차이) / 자동(잘림) / 후보 n명 / 새 선수 / 전체 닉네임 필요.
    - 후보는 라디오로 고르고, 전체 선수 검색(`listPlayers` 재사용)과 "새 미등록 선수로"를 둔다.
    - "이 닉네임을 별칭으로 추가" 체크박스: 정확 일치면 숨긴다. 자동 연결 상태면 해제·비활성. 담당자가 직접 다른 선수를 고르면 기본으로 체크한다. 닉네임이 잘렸으면 전체 닉네임을 입력해야 체크할 수 있다.
  - `ChampionPicker`: 원본에서 잘라낸 아이콘(`IconCrop`, blob과 0~1000 박스로 CSS/canvas 잘라내기) 옆에 상위 3개 칩(아이콘 + 한글 이름)과 검색 콤보박스(카탈로그 전체)를 둔다. 피어리스 제한 챔피언은 흐리게 하고 "피어리스" 표시를 붙인다. `uncertain`이면 강조한다.
  - `BanGrid`: 팀별로 결과창 배치(위 3, 아래 2)를 그대로 따르고, 칸마다 B1~R5 라벨을 붙인다(`banTurns.ts`가 `topSide`로 계산). 10칸 모두 필수이고 1순위로 미리 채운다(§9 Q3).
  - 경고·오류: check/저장 응답의 `errors`(빨강, 저장 버튼 비활성)와 `warnings`(노랑, 저장 가능)를 상단에 목록으로 보여준다. 행 위치 강조는 프런트가 따로 계산한다(빈 챔피언·밴, 세트 안 중복, 제한 챔피언, 미확정 선수, 같은 선수 중복). 화면 강조용일 뿐이고 최종 판단은 서버가 한다.
  - 저장 처리:
    - 422는 목록을 표시하고 입력을 유지한다.
    - 409 `ALIAS_CONFLICT`는 해당 행에 `conflicts`를 표시한다.
    - 409 `DUPLICATE_SET`은 기존 세트 번호를 안내한다.
    - 409 `SET_NO_CONFLICT`는 context를 다시 불러온 뒤 다시 저장하게 안내한다.
    - 성공하면 `SAVED` 단계로 가서 "M세트 저장됨 · 다음 세트 등록 / 내전 묶음으로"를 보여준다. 이때 players·registration-context 쿼리를 무효화한다.
  - REVIEW 단계에서는 `beforeunload` 경고를 건다. 새로고침하면 편집 내용이 사라지고, 다시 붙여넣으면 `reused` 작업으로 이어진다.

## 7. 테스트 계획 (외부 API·네트워크 없이)

공통 테스트 자원(Phase 0, `src/test/.../ingest`):
- `FakeScoreboardExtractor`: 정해진 `ExtractedScoreboard`를 돌려주거나, 차례대로 지정한 예외를 던진다. 호출 횟수를 기록한다.
- `FakeDataDragonSource`: 색·무늬가 다른 합성 아이콘 N개를 만든다.
- `SyntheticScoreboardImage`: 합성 아이콘을 알려진 박스 위치에 그린 PNG를 만든다. 초상화는 금색 원 테두리를, 밴은 어둡게 하고 사선을 덧그린다. 가짜 추출기가 같은 박스를 돌려주므로 매칭까지 결정적으로 검증할 수 있다.
- `TestIngestConfiguration`(`@Primary` 가짜 빈, 이미지 저장 경로 `build/test-images`)을 `@IntegrationTest`의 `@Import`에 추가해 컨텍스트 하나를 같이 쓴다.
- 비동기 완료 대기는 `awaitStatus(jobId, timeout)` 폴링 헬퍼로 한다(Awaitility 없음).

단위:
- `ScoreboardResponseValidatorTest`: 실험 코드 `PureLogicTest`를 옮기고 다음 경우를 추가한다. 원소 5개짜리 객체, 4294967296, 2026/02/31, `5:07`, 빈 닉네임, 면적 0 박스, 승리/패배 → WIN/LOSS.
- `RetryingExtractorTest`:
  - 503, 503, 성공 → 3회 호출, 대기 [5000+j, 10000+j]
  - 400 → 1회 호출 후 `EXTRACTION_REJECTED`
  - 429 네 번 → `RATE_LIMITED`
  - 가짜 시계로 제한 시간 초과 → `TIMEOUT`
  - Sleeper가 인터럽트 → `INTERRUPTED`이고 인터럽트 플래그가 복구됨
- `BoxesTest`, `IconMatcherTest`(합성 아이콘으로 PICK·BAN 각각 정답 1위, 상위 3개와 margin, 동점 정렬), `DataDragonChampionCatalogTest`(@TempDir: 처음 불러오기, 캐시 재사용, 네트워크 실패 시 캐시 버전 사용, 캐시도 없으면 empty, 잘못된 id·버전 거절, 동시 호출 시 한 번만 불러오기).
- `ImageInspectorTest`(PNG·JPEG 통과, GIF·쓰레기 바이트·빈 값 거절, 크기 초과, 해시 hex 64자), `LocalImageStoreTest`(쓰기·읽기, 같은 키 다시 쓰기, 잘못된 키·경로 탈출 거절).
- `SetEvaluatorTest`(TDD, 순수):
  - setNo 1 블루/레드 → TEAM1/TEAM2
  - 2세트 겹침 3명 이상 → TEAM1
  - 미확정 → PENDING + 오류
  - 새 선수 자리표시 id
  - 같은 키 새 선수 둘 → 오류
  - 잘린 닉네임으로 새 선수 만들기 또는 addAlias → 오류
  - 알 수 없는 챔피언
  - 피어리스 위반
  - 지문 계산이 위쪽/아래쪽 순서와 무관함
  - 진영 경고가 경고로만 남음
- `DraftServiceTest`: 캡처한 사람 포지션별 배정(ADC → 원딜·탑·정글·미드·서폿), 매칭 결과별 기본값(EXACT/AUTO는 addAlias=false, CANDIDATES는 playerId=null), 빈 닉네임 예외 처리.

통합(Testcontainers MySQL):
- `SchemaConstraintTest`에 V3 추가: 상태·진영·포지션 CHECK, error/result/saved 짝 CHECK, `uk_extraction_job_game_set`, FK RESTRICT. 엔티티 경로로 JSON 왕복도 확인한다(§1 [확인 필요] 판정).
- `ExtractionApiTest`:
  - 미인증 401, CSRF 없음 403
  - multipart 202 → 폴링 SUCCEEDED → draft 형식
  - 잘못된 이미지 400, 큰 이미지 413
  - 유효 세트 해시 409, 저장 전 같은 해시는 같은 작업 재사용 200
  - 가짜 추출기 실패 → FAILED + 코드, 카탈로그가 없으면 Gemini 0회 호출
  - 키 미설정 503
- `ExtractionJobRecoveryTest`(JdbcTemplate로 RUNNING을 넣고 복구 메서드 직접 호출 → FAILED INTERRUPTED), 오래된 RUNNING을 조회하면 TIMEOUT으로 정리되는지.
- `SetRegistrationApiTest`:
  - 1세트 201: 세트 10행·밴 10행(turn/side 쌍), `image_hash`/`image_key` 복사, 작업 연결
  - 2세트 판별 TEAM1/TEAM2와 team1Side
  - 피어리스 위반 422
  - 지문 중복 409 `DUPLICATE_SET` + `existingSetId`
  - 같은 작업 두 번 → 409 `EXTRACTION_ALREADY_SAVED`
  - `addAlias=true` → 별칭 생성, 자동 연결 행 false → 별칭 없음
  - 새 선수 생성
  - 새 선수 닉네임 충돌 409 → 선수·별칭·세트가 하나도 남지 않음(롤백)
  - 다른 대결의 작업 400
  - check는 부작용 없음(전후 행 수 동일)
- `SetRegistrationConcurrencyTest`(`ConcurrencyServiceTest` 방식): 같은 지문 두 저장 동시 → 201 하나 + 409 하나. 같은 대결 서로 다른 세트 둘 동시 → 대결 잠금으로 둘 다 성공하고 setNo가 1, 2.

프런트(Vitest + mockFetch):
- `reviewModel.test`: 포지션 맞바꾸기, 캡처한 사람 포지션 변경 시 다시 매기기, 진영별 B1~R5 라벨, 라인 순 정렬, 명령 만들기(addAlias 규칙, 잘린 닉네임).
- `UploadStep.test`: paste 처리 함수에 가짜 이벤트를 넣어 시험한다. jsdom의 ClipboardEvent/DataTransfer 지원은 [확인 필요]이고, 안 되면 처리 함수만 따로 시험한다. 형식·크기 거절도 확인한다.
- `SetRegisterPage.test`: 시작 → 폴링 2회 → 초안 → 후보 선택 → 저장 201 / 422 표시 / 409 표시. `client.test`에 FormData 전송(Content-Type 미지정, CSRF 헤더 포함)을 추가한다.

수동 종단 점검(리드, 실제 키·`samples/`, CI 제외): 실제 결과창 3장을 Ctrl+V → 확인 → 저장한다. 같은 이미지를 다시 넣으면 409. 반대편 캡처는 지문 409. 앱을 재시작하면 실행 중 작업이 INTERRUPTED가 된다. 결과는 학습 노트에 수치로 남긴다(닉네임 제외).

## 8. 작업 분할 (같은 트리, 파일 겹침 없음)

공통 규칙: 자기 파일을 컴파일이 깨진 상태로 두지 않는다. Gradle을 동시에 돌리면 `build/test-results`가 충돌하므로(AGENTS §3), Phase 0에서 `build.gradle`에 `-PbuildDirName=…`로 빌드 디렉터리를 바꿀 수 있게 하고 작업자마다 다른 이름을 쓴다 [확인 필요: Gradle 9 `layout.buildDirectory.set`]. 이게 안 되면 `--tests` 필터로 순서대로 돌린다.

| 단계 | 담당 | 파일 | 확인 |
|---|---|---|---|
| **Phase 0** (먼저, 단독) | 리드 또는 A | `backend/build.gradle`(google-genai 1.78.0, buildDir 옵션), `application.yml`(§5 설정), `.env.example`, `.gitignore`(`data/`), `V3__extraction_job.sql`, `ingest/job/{ExtractionJob, ExtractionJobStatus, ExtractionErrorCode, ExtractionJobRepository, ExtractionResult}`, `ingest/extract/{ScoreboardExtractor, ExtractedScoreboard…, ExtractionException}`, `ingest/image/ImageStore`, `champion/{Champion, ChampionCatalog, CatalogSnapshot}`, `common/config/AsyncConfig`, `session/{GameSetRepository, DuelRepository}` 메서드 추가, 테스트 `IntegrationTest`(@Import 추가), `ingest/{TestIngestConfiguration, FakeScoreboardExtractor, FakeDataDragonSource, SyntheticScoreboardImage}`, 프런트 `src/api/registrationTypes.ts`(§4·§5 계약) | `./gradlew test` 녹색(V3 validate, 기존 398개 유지), 컨텍스트 기동 시 Jackson 2/3 공존 확인, `npm run build` |
| **Phase 1-A** | A | `champion/**`(나머지: DataDragonSource·Http 구현·Catalog·IconMatcher·Boxes·ChampionController·응답 DTO), `ingest/extract/**`(Gemini·Validator·Retrying·RetryPolicy·Sleeper·ExtractorConfig·Disabled) + 같은 패키지 테스트 | 단위 테스트 녹색 |
| **Phase 1-B** | B | `ingest/image/**`(ImageInspector·LocalImageStore·설정), `ingest/job/**`(Service·Runner·Recovery·Controller·DTO), `common/web/ApiExceptionHandler`(413·part 누락만), `SchemaConstraintTest`(V3 추가), Extraction 통합 테스트 | A와 병렬. Phase 0 가짜 빈으로 테스트하고, 실제 A 구현은 Phase 2에서 연결 |
| **Phase 1-C** | C | `registration/**` 전부, `player/PlayerService`(2개 메서드) + 해당 테스트. 작업 픽스처는 `ExtractionResult`로 직접 INSERT | `SetEvaluatorTest` 먼저(TDD), 통합·동시성 테스트 녹색 |
| **Phase 1-D** | D | `frontend/src/**`(위 §6 파일, `client.ts`, `routes.tsx`, `errorMessages.ts`, `register.css`) | `npm run lint && npm test && npm run build` |
| **Phase 2** | 리드 | 전체 clean build, 실제 Gemini 수동 종단 점검, `uncertain-margin` 보정, AGENTS.md, implementation-plan 데이터 모델 절(`extraction_job`), ADR 008(챔피언 키), 009(추출 작업: 사실만 저장·비동기·재시작), 010(이미지 저장소), Codex(Astra) 리뷰 | 사용자 승인 후 커밋·PR |

의존 관계:
- 0 → (A, B, C, D 병렬).
- B와 C는 A의 실제 구현 없이 가짜로 끝낼 수 있다.
- D는 계약 파일만 있으면 된다.
- 학습 노트: A `12-extraction-icons`, B `13-extraction-job`, C `14-set-registration`, D `15-frontend-register`(로컬 전용).

## 9. 위험과 사용자에게 물어야 할 질문

### 위험
- R1 **[확인 필요]** google-genai SDK는 Jackson 2를 쓰고 Boot 4는 Jackson 3를 쓴다. 패키지가 달라 공존할 것으로 보지만, 전이 의존성(HTTP 클라이언트 등)이 충돌하는지 Phase 0 컨텍스트 테스트로 확인한다.
- R2 **[확인 필요]** `result_json`의 Hibernate JSON/String 매핑, CHECK와 FK 컬럼 제한, 제약 이름 추출 형식, multipart 413 처리. 모두 Phase 0과 1-B 테스트에서 드러난다.
- R3 밴 정확도가 1위 기준 17~20/30이다. 확인 화면 품질이 데이터 품질을 좌우한다. 잘라낸 아이콘을 나란히 보여주고 상위 3개 칩을 두고 강조하는 것으로 대응한다.
- R4 지연은 23~89초이고 무료 등급 429/503이 잦다. 진행 표시, 제한 시간 5분, 실패하면 다시 추출로 대응한다.
- R5 1팀/2팀 자동 판별에는 수동 수정 수단이 없다. 대규모 교환 등으로 판별이 틀리면 진영 경고로만 드러난다.
- R6 이미지 해시는 브라우저가 붙여넣기 이미지를 다시 인코딩하면 달라질 수 있다 [확인 필요]. 1차 방어일 뿐이고 최종은 지문이다.
- R7 저장되지 않은 작업의 이미지는 `data/images`에 계속 쌓인다(정리 없음). 닉네임이 들어 있으니 git·백업 범위를 주의해야 한다.
- R8 캡처한 사람이 탑·정글·서폿일 때의 행 배치는 아직 검증하지 않았다(유저플로우 §11-9). 확인 화면에서 맞바꿀 수 있고, 새 샘플로 확인한다.
- R9 Data Dragon id가 바뀐 전례가 있다(대소문자 변경 등). 바뀌면 마이그레이션 UPDATE가 필요하다(ADR 008). 패치 직후 새 챔피언이 Data Dragon에 아직 없으면 고를 수 없다.
- R10 시작 시 복구와 단일 스레드 실행기는 인스턴스 1대가 전제다. 여러 대로 늘리면 다시 설계해야 한다.
- R11 무료 등급 Gemini에 친구 닉네임이 담긴 이미지를 보낸다. 무료 등급 입력이 Google 제품 개선에 쓰일 수 있다는 약관 [확인 필요].
- R12 `ingest`가 `session`을 쓰고 `registration`이 둘을 모두 쓰는 패키지 간 양방향 의존(클래스 순환은 없음).

### 사용자 확인 질문 (추천안 포함)
1. **수동 입력(유저플로우 §5.6)을 M4에 넣을까요?** 추천: **M4에서는 빼고**, AI 실패 시에는 "다시 추출"만 제공한다. 저장 API는 `extractionJobId`를 나중에 null로 열 수 있게 설계해 두고, 수동 입력 화면은 M6(세트 수정과 같은 화면)에서 만든다.
2. **세트 번호를 서버가 자동(유효 세트 최댓값 + 1)으로 정해도 될까요?** 유저플로우 §5.2에는 "담당자 지정"으로 되어 있다. 추천: **자동으로 정하고 화면에 표시**한다. 순서가 뒤바뀐 업로드는 막는다(피어리스·직전 세트 판별이 앞 세트가 있다는 전제에 기대므로). 번호가 잘못되면 M6의 삭제·수정으로 고친다.
3. **밴 10칸을 AI 1순위로 미리 채울까요, 칸마다 담당자가 직접 고르게 할까요?** 추천: **1순위로 미리 채우고** 잘라낸 원본 아이콘 옆에 상위 3개 후보를 보여주며, 차이가 작은 칸을 강조한다. 강제 확인 클릭은 없다. 직접 고르게 하면 매 세트 10번을 클릭해야 한다.
4. **경고(합계·진영 불일치)가 있을 때 저장 직전에 한 번 더 확인을 받을까요?** 추천: **추가 확인 없이** 확인 화면 상단에 경고를 계속 보여주고 저장 버튼 하나로 저장한다(2026-10-16 "경고는 저장을 막지 않음" 결정과 일치).
5. **1팀/2팀 자동 판별이 틀렸을 때 담당자가 뒤집는 버튼이 필요할까요?** 추천: **M4에서는 넣지 않는다.** 판별 근거(겹친 인원)와 진영 경고를 보여주고, 실사용에서 틀리는 일이 확인되면 AGENTS 개선 후보로 올린다.
6. **원본 이미지 보관(유저플로우 §11-4)**: 추천: 저장된 세트의 원본은 계속 보관하고, 저장하지 않은 작업 이미지는 7일 뒤 삭제한다. 삭제 구현은 M7(S3 수명 주기 규칙)에서 하고, M4는 로컬에 쌓아 둔다.
7. **(정보·확인)** 무료 등급 Gemini로 친구 닉네임이 담긴 결과창을 보내는 것(R11). 추천: M0처럼 계속 쓴다(실명은 보내지 않음). 꺼림칙하면 M7 전에 유료 등급(데이터 미사용 약관)으로 전환을 검토한다.

## 참고 파일
- `C:\Users\ASUS\Desktop\지민\프로젝트\개인 프로젝트\lol-inhouse\tools\extraction-lab\src\main\java\com\lolinhouse\ingest\{GeminiExtractor,IconMatcher,DataDragon,Retry,Boxes,ScoreboardExtractor}.java`(이식 대상)
- `C:\Users\ASUS\Desktop\지민\프로젝트\개인 프로젝트\lol-inhouse\backend\src\main\java\com\lolinhouse\domain\set\*`, `...\domain\player\NicknameMatcher.java`
- `C:\Users\ASUS\Desktop\지민\프로젝트\개인 프로젝트\lol-inhouse\backend\src\main\java\com\lolinhouse\session\{GameSet,GameSetRepository,DuelRepository,InhouseSessionService}.java`, `...\player\PlayerService.java`, `...\common\web\ApiExceptionHandler.java`, `...\resources\application.yml`
- `C:\Users\ASUS\Desktop\지민\프로젝트\개인 프로젝트\lol-inhouse\frontend\src\{api\client.ts,api\types.ts,routes.tsx,pages\admin\SessionDetailPage.tsx}`

참고(검토 중 발견, M4 범위 밖): 프런트 `vite.config.ts`의 `'/api': 'http://localhost:8080'` 프록시는 multipart도 그대로 전달하므로 바꿀 필요가 없다. `SessionDetailPage`에는 대결별 세트 목록이 없다. 업로드 화면의 `registeredSets`가 M5 전까지 그 역할을 대신한다.

## 10. 사용자 결정 (2026-10-17, 구현은 이 절이 위 내용보다 우선)

- **Q1 수동 입력**: M4에서 뺀다. AI가 실패하면 "다시 추출"만 제공한다. 저장 API의 `extractionJobId`는 나중에 null을 허용할 수 있게 열어 두고, 수동 입력 화면은 M6(세트 수정과 같은 화면)에서 만든다.
- **Q2 세트 번호**: 서버가 자동으로 정한다(유효 세트 최댓값 + 1). 업로드 화면에 "N세트로 등록"을 표시한다. 유저플로우 §5.2의 "담당자 지정"을 대체한다.
- **Q3 밴 10칸**: AI 1순위로 미리 채운다. 잘라낸 원본 아이콘 옆에 상위 3개 후보를 보여주고, 차이가 작은 칸은 강조한다. 강제 확인 클릭은 없다.
- **Q4 경고 시 추가 확인**: 추가 확인 없이 저장한다(추천안 채택, 2026-10-16 결정과 일치).
- **Q5 1팀/2팀 뒤집기 버튼**: M4에서는 넣지 않는다(추천안 채택). 틀리는 일이 확인되면 AGENTS 개선 후보로 올린다.
- **Q6 원본 이미지 보관**: 저장된 세트의 원본은 계속 보관한다. 저장하지 않은 작업 이미지는 7일 뒤 삭제하되, 삭제 구현은 M7(S3 수명 주기 규칙)에서 한다(추천안 채택).
- **Q7 Gemini 무료 등급**: 계속 쓴다(실명은 보내지 않음). 꺼림칙해지면 M7 전에 유료 등급 전환을 검토한다.

## 11. 팀 변경 규칙과 Codex(Astra) 설계 리뷰 반영 (2026-10-17, 구현은 이 절이 최우선)

### 11.1 팀 변경·피어리스 규칙 (사용자 결정)
실제 내전에서 "2판 하고 팀을 바꿔 2판 더"처럼 중간에 팀이 바뀌는 일이 있다. 규칙:
- **피어리스를 초기화하면 항상 새 대결**(세트 점수 0:0). 같은 팀 안에서 탑·미드가 라인을 서로 바꾸는 경우도 피어리스 초기화 → 새 대결.
- **맞라인 교환**(같은 포지션끼리 팀을 맞바꿈)은 피어리스를 유지하면 같은 대결을 계속한다(점수 이어감, `team_swap` 기록은 M6). 초기화하면 새 대결.
- **새 대결의 피어리스 방식은 만들 때 담당자가 고른다**: `RESET`(제한 없음으로 시작) 또는 `CONTINUE`(이어가기). 이어가기의 제한 범위는 **마지막 초기화 이후 전부** = 같은 내전 묶음에서 이 대결부터 seq를 거슬러 올라가 처음 만나는 `RESET` 대결까지의 유효 세트 픽 전체. 묶음의 첫 대결은 항상 `RESET`(CONTINUE 요청은 400 `FEARLESS_CONTINUE_WITHOUT_PREVIOUS`).

스키마(V3에 함께 넣는다. V2는 수정하지 않음):
```sql
ALTER TABLE duel
  ADD COLUMN fearless_mode VARCHAR(8) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'RESET',
  ADD CONSTRAINT ck_duel_fearless_mode CHECK (fearless_mode IN ('RESET','CONTINUE'));
```
(기존 행을 채우기 위한 DEFAULT. 앱은 항상 값을 명시해 쓴다.) `Duel.of(sessionId, seq, fearlessMode)`, enum `FearlessMode`(VARCHAR 매핑).

API 변경:
- `POST /api/admin/inhouse-sessions/{sessionId}/duels` 본문(선택) `{fearlessMode: "RESET"|"CONTINUE"}`, 생략 시 `RESET`. 응답 `DuelResponse`에 `fearlessMode` 추가. 프런트 "새 대결" 버튼은 둘 중 하나를 고르게 한다(첫 대결이면 RESET만).
- `registration-context`의 `restrictedChampions`는 위 이어가기 규칙으로 계산(`RegistrationContextService`가 대결 체인을 따라 올라감).

**팀 변경 감지(`TeamChangeDetector`, 순수 함수, TDD)** — 같은 대결의 직전 유효 세트와 이번 세트(선수 확정 후)를 비교:

| 분류 | 조건 | 화면 |
|---|---|---|
| `NONE` | 두 팀의 선수 집합이 직전과 같고(어느 쪽이 1팀이든) 각 선수의 포지션도 같음 | 표시 없음 |
| `SAME_LANE_SWAP` | 10명이 같고 포지션도 같지만, 같은 포지션끼리 팀이 맞바뀐 쌍이 1개 이상 | 노란 안내 "맞라인 교환 n건 감지 — 피어리스를 유지하면 이 대결로 저장, 초기화했다면 새 대결로" |
| `POSITION_CHANGE` | 팀 구성은 같은데 포지션이 바뀐 선수가 있음(예: 탑↔미드) | 경고 + "새 대결로 등록" 버튼 |
| `RESHUFFLE` | 그 밖(라인이 다른 교환, 2명 이상 이동, 새 사람 투입 등) | 경고 + "새 대결로 등록" 버튼 |

- 판별 보류(선수 미확정)면 감지도 보류. 1세트는 비교하지 않는다(새 대결 자체가 신호).
- 경고는 저장을 막지 않는다(같은 대결로 저장 가능). 단 `RESHUFFLE`이면 1팀/2팀 판별(겹침 3명 기준)이 의미가 없으므로 판별 결과 옆에 "팀이 바뀌어 판별이 불확실" 표시.
- "새 대결로 등록": 저장 요청에 `newDuel: {fearlessMode}`를 담아 보낸다. 서버는 같은 저장 트랜잭션에서 ①원래 대결의 묶음 행을 잠그고 ②`seq = max+1` 대결 생성 ③작업의 `duel_id`를 새 대결로 옮기고 ④새 대결 1세트로 평가·저장한다. 평가는 새 대결 기준으로 다시 하므로(setNo=1, 블루=1팀, 피어리스는 선택한 방식) 프런트는 `check`도 `newDuel`을 담아 미리 보여준다. 응답에 `duelId`(새 대결)를 돌려준다.

### 11.2 Codex(Astra) 설계 리뷰 반영
1. **(High) 미확정과 신규 생성 구분**: `RowInput`에 `resolution: "UNRESOLVED"|"EXISTING"|"NEW"` 추가. `EXISTING`이면 `playerId` 필수, `NEW`면 `playerId`는 null이고 `nickname`으로 미등록 선수 생성, `UNRESOLVED`는 저장 거부(422 위반 "선수를 골라야 합니다"). 기본값: EXACT/AUTO → EXISTING, CANDIDATES·잘린 NEW → UNRESOLVED, 잘리지 않은 NEW → NEW. 테스트: 후보 상태 그대로 저장 → 거부, 후보 거부 후 NEW 선택 → 생성.
2. **(High) 작업 전체 마감 시각 하나**: RUNNING 선점 시각 + 5분을 `deadline`으로 정하고 카탈로그 로딩·추출·매칭 전체에 전달한다. 요청마다 SDK 제한 = `min(설정값 90초, 남은 시간)`, 남은 시간이 5초 미만이면 시도하지 않고 `EXTRACTION_TIMEOUT`. 완료 시에도 마감 초과면 TIMEOUT. HTTP 응답 없는 SDK 시간 초과는 `EXTRACTION_TIMEOUT`(재시도 대상). 카탈로그는 앱 기동 후 백그라운드에서 미리 데워 둔다(`ApplicationReadyEvent`, 실패해도 기동은 성공).
3. **(High) 대기열·재사용**: 실행기 대기열을 2로 줄인다. `QUEUED`도 만료 규칙을 둔다(생성 후 10분이 지나도 시작 안 했으면 조회 시 `FAILED(EXPIRED)`, 조건부 UPDATE). 재사용은 "만료되지 않은 QUEUED·RUNNING 또는 저장 안 된 SUCCEEDED"만. 프런트의 "다시 추출"은 `POST …/extractions?retry=true`로 보내 재사용을 건너뛰고 새 작업을 만든다(그 대결의 같은 해시 미저장 작업 중 QUEUED/RUNNING은 `FAILED(SUPERSEDED)`). 폴링 중단(6분) 후 버튼은 "상태 다시 확인"과 "다시 추출" 둘로 구분. dispatch 실패 후 상태 기록까지 실패한 QUEUED는 만료 규칙이 정리한다.
4. **(Medium) 원자적 전이**: 선점·성공·실패를 각각 짧은 트랜잭션의 조건부 UPDATE(`WHERE id=? AND status='RUNNING'`)로 하고 갱신 행 수가 0이면 결과를 버린다(로그만). runner 전체를 트랜잭션으로 감싸지 않는다. 테스트: TIMEOUT 처리 후 늦은 성공이 덮어쓰지 않음, 복구와 완료 경쟁(latch).
5. **(Medium) 같은 이미지 동시 업로드**: 재사용 조회 + 작업 INSERT를 짧은 트랜잭션에서 `DuelRepository.findByIdForUpdate`로 대결 행을 잠근 뒤 한다. 파일 저장(`ImageStore.put`)은 잠금 전에(해시 키라 멱등), Gemini 호출은 커밋 후. 테스트: 같은 이미지 두 요청 동시 → 작업 1개.
6. **(Medium) 중복 지문 우선**: 평가 순서를 ①형태·파싱 검증(시간·날짜·숫자) ②지문 계산 ③**중복 지문이면 즉시 409 `DUPLICATE_SET`**(피어리스·규칙 오류보다 먼저) ④도메인 규칙 오류(422)로 한다. `check`에서는 `duplicateSet`을 채우고 errors는 계속 계산해 보여준다. 테스트: 같은 대결·다른 대결에 같은 경기 다른 캡처 저장 → 409.
7. **(Medium) 프런트 오류 계약**: `ApiError`가 `violations`, `warnings`, `existingSetId`, `existingDuelId`, `existingSetNo`, `savedSetId`를 보존하도록 `client.ts`·`types.ts`를 확장(작업자 D 범위에 명시). check 응답의 `errors`와 422의 `violations`를 같은 화면 모델(`ReviewIssues`)로 변환. 오류 JSON → `ApiError` → 화면 표시 테스트.
8. **(Medium) 예외 던지는 도메인 함수 보호**: `SetEvaluator`는 시간(`mm:ss`, 0초 거부)·날짜를 먼저 검증해 위반으로 만들고, 유효할 때만 `SetFingerprint.of`를 부른다. 평가용 밴 배열은 null 허용으로 따로 만들고, `BanOrder.build`는 최종 저장에서만(그때는 10칸 확정) 호출한다. 테스트: "00:00", "5:7", 빈 밴 → 422/위반이지 500 아님.
9. **(Medium) 로그 정책**: `logging.level.org.hibernate.orm.jdbc.error: OFF`(Hibernate가 SQL 오류 원문 — 닉네임 키 포함 — 을 남기지 않게). `ApiExceptionHandler`의 500 로그와 추출 실패 로그는 예외 클래스·오류 코드·제약 이름만 남기고 메시지·원인 체인 메시지는 남기지 않는다(스택 프레임은 남겨도 됨: 메시지 없이 직접 포맷). 테스트: 별칭 충돌을 유도하고 로그 캡처(`OutputCaptureExtension`)에 닉네임이 없는지 확인.
10. **(Medium) 병렬 작업 계약**: Phase 0에 `RetryingExtractor`의 결과 타입(`ExtractionOutcome(scoreboard, attempts)`), `Deadline`, `DataDragonSource`, `ChampionCatalog`/`CatalogSnapshot`/`IconMatch` 결과 타입을 인터페이스·record로 확정한다. 테스트 이미지·캐시 경로는 `layout.buildDirectory`에서 파생(`build.gradle` test `systemProperty`로 전달). 공통 가짜(`FakeScoreboardExtractor` 등)는 테스트마다 시나리오를 설정하고 `@AfterEach`에서 초기화하는 규칙.
- 추가: §9의 "순서가 뒤바뀐 업로드는 막는다"는 표현은 틀렸다. 자동 번호는 순서 뒤바뀜을 막지 못한다 → "세트는 순서대로 올린다(화면에 N세트로 표시해 담당자가 확인)"로 정정. 잘못 올린 세트는 M6 수정·삭제로 고친다.

### 11.3 [확인 필요] 판정 (Codex가 JAR로 확인)
- `result_json`: `String` + `@JdbcTypeCode(SqlTypes.JSON)`은 이중 인코딩하지 않는다 → JSON 컬럼 유지. validate·왕복은 Phase 0 테스트로 확인.
- SDK 요청 timeout은 OkHttp `callTimeout`(호출 전체)으로 들어간다.
- Spring 7 기본 413 처리기가 있고 상태 이름은 `CONTENT_TOO_LARGE` → `IMAGE_TOO_LARGE`로 내려면 `handleMaxUploadSizeExceededException` override.
- multipart 키는 `spring.servlet.multipart.max-file-size`/`max-request-size`. `server.tomcat.max-swallow-size` 기본 2MB → 12MB로 올리고 실제 Tomcat 경로에서 413 확인.
- 제약 이름은 1062 메시지의 `for key '…'` 그대로(테이블 접두사 포함 가능) → `endsWith` 비교 유지.
- jsdom 29에는 `ClipboardEvent`·`DataTransfer`가 없다 → paste 처리 함수를 분리해 시험.
- Gradle 9.8.1 `layout.buildDirectory.set(...)` 사용 가능.
- Jackson 2(SDK)/3(Boot) 공존: Phase 0에서 SDK 추가 후 컨텍스트 기동 + SDK 직렬화 단위 테스트로 확인.

## 12. Phase 0 결과 (2026-10-17, 공통 기반 작업자) — A·B·C·D는 작업 전에 이 절부터 읽는다

`./gradlew test` 425개 통과(기존 401 + 신규 24), `npm run build` 통과. 아래 계약은 컴파일·테스트로 고정됐다. **계약을 바꿔야 하면 혼자 고치지 말고 리드에게 알린다.**

### 12.1 [확인 필요] 판정

| 항목 | 판정 |
|---|---|
| V3 적용, `ck_extraction_job_saved`(FK 컬럼 CHECK, 오류 3823) | **통과.** FK가 `RESTRICT`라 CHECK에 그대로 둘 수 있다. 서비스 규칙으로 옮길 필요 없음 |
| `result_json`: `String` + `@JdbcTypeCode(SqlTypes.JSON)` | **통과.** Hibernate `validate` 통과, 저장 후 `JSON_TYPE(result_json) = 'OBJECT'`(이중 인코딩 없음), 한글 닉네임 포함 왕복 동일. 조건부 UPDATE(`succeed`)의 `:resultJson` 바인딩도 같은 결과 |
| Jackson 2(google-genai) / Jackson 3(Boot) 공존 | **통과.** SDK 추가 후 모든 `@IntegrationTest` 컨텍스트 기동 성공. SDK `GenerateContentConfig.toJson()`·`Client.builder()` 동작(`IngestFoundationTest`) |
| Gradle 9.8.1 `layout.buildDirectory.set(...)` | **통과.** `-PbuildDirName=build-a`로 `backend/build-a/`가 생기고 `.gitignore`(`build-*/`)에 걸린다 |
| `MySQL JSON` + 한글 | 통과(위 왕복 테스트) |
| 합성 결과창이 실제 매처로 맞는가 | 실험 코드 `IconMatcher`(PICK/BAN)로 확인: 픽 10/10, 밴 10/10 1순위 정답(최소 margin 픽 0.16, 밴 0.042). A는 이식 후 이 합성 이미지로 같은 단정을 걸 수 있다 |

[확인 필요] 중 다음은 Phase 0 범위 밖이라 그대로 남는다: 제약 이름 `endsWith`(C), multipart 413·`max-swallow-size` 실제 Tomcat 경로(B), SDK 요청 시간 초과의 실제 동작(A), jsdom paste(D).

### 12.2 설정·빌드

- `application.yml`: `spring.servlet.multipart.max-file-size=10MB`/`max-request-size=11MB`, `server.tomcat.max-swallow-size=12MB`, `app.gemini.{model, request-timeout=90s, max-attempts=4, job-deadline=5m}`, `app.image-store.local-dir`, `app.ddragon.{cache-dir, refresh-interval=12h}`, `app.extraction.{max-image-bytes, max-image-side=4096, stale-after=10m, queued-expire-after=10m, uncertain-margin=1.0}`, `logging.level.org.hibernate.orm.jdbc.error="OFF"`. `uncertain-margin`은 보정 전 임시값(1.0 = 모든 칸 강조)이다.
- API 키: yml에 없다. 코드는 `@Value("${GEMINI_API_KEY:}")`.
- `build.gradle`: `com.google.genai:google-genai:1.78.0`. 병렬 작업은 `./gradlew test -PbuildDirName=build-a`(작업자마다 다른 이름, `build-` 접두사). test 작업이 시스템 속성 `app.test.work-dir`(= 빌드 디렉터리 밑 `test-work`)를 전달한다.
- 테스트 컨텍스트의 `app.image-store.local-dir`/`app.ddragon.cache-dir`은 `TestIngestConfiguration`의 `DynamicPropertyRegistrar`가 `work-dir` 밑으로 덮어쓴다. 실제 `data/`를 건드리지 않는다.
- `.env.example`에 `IMAGE_STORE_DIR`, `DDRAGON_CACHE_DIR`(주석 처리된 선택 항목: 값을 비운 채 두면 기본값을 덮어쓰기 때문), `.gitignore`에 `/data/`, `build-*/`.

### 12.3 스키마 (`V3__extraction_job.sql`)

§1 DDL 그대로 + §11.1 `duel.fearless_mode`(`VARCHAR(8) ascii_bin NOT NULL DEFAULT 'RESET'` + `ck_duel_fearless_mode`). `EXPIRED`·`SUPERSEDED`는 status가 아니라 `error_code` 값이다(status는 QUEUED/RUNNING/SUCCEEDED/FAILED).

### 12.4 계약 목록과 시그니처 (모두 `com.lolinhouse.` 아래)

**session** (C가 주로 사용)
- `FearlessMode { RESET, CONTINUE }`. `Duel.of(Long sessionId, int seq, FearlessMode)`, `duel.fearlessMode()`. 기존 호출부(`InhouseSessionService.createDuel`, M3 테스트)는 `RESET`을 넘긴다. **API 동작은 안 바뀌었다**(요청 본문 `fearlessMode`, 응답 필드, 첫 대결 CONTINUE 400은 C 몫).
- `DuelRepository.findByIdForUpdate(Long)`(PESSIMISTIC_WRITE, 트랜잭션 안에서만).
- `InhouseSessionRepository.findByIdForUpdate(Long)`(PESSIMISTIC_WRITE) — §11.1 "새 대결로 등록"의 ①번(묶음 행 잠금)을 위해 **설계에 없던 메서드를 추가**했다.
- `GameSetRepository`: `findFirstByImageHashAndDeletedAtIsNull(String)`, `findByFingerprintAndDeletedAtIsNull(String)`, `findFirstByDuelIdAndDeletedAtIsNullOrderBySetNoDesc(Long)`(모두 `Optional<GameSet>`), `int findMaxActiveSetNo(Long duelId)`(유효 세트 없으면 0), `List<String> findActivePickChampionKeys(Long duelId)`, `List<String> findActivePickChampionKeysByDuelIds(Collection<Long>)`(이어가기 체인용), `List<Long> findTeam1PlayerIds(Long gameSetId)`. 기존 `findByDuelIdAndDeletedAtIsNullOrderBySetNoAsc` 유지.

**ingest.job** (B가 만들고 C가 읽음)
- `ExtractionJobStatus { QUEUED, RUNNING, SUCCEEDED, FAILED }`.
- `ExtractionErrorCode`: §5 표 10개 + `EXTRACTION_NOT_CONFIGURED`(키 없음용으로 추가) + `EXPIRED` + `SUPERSEDED`. 이름 그대로 `error_code`에 저장.
- `ExtractionJob`: `static queued(Long duelId, Side topSide, Position capturerPosition, String imageHash, String imageKey)`, `markRunning(Instant)`, `succeed(String resultJson, int attemptCount, Instant)`, `fail(ExtractionErrorCode, int attemptCount, Instant)`, `linkGameSet(Long)`, `moveToDuel(Long)`(새 대결로 옮길 때), 접근자 `id() duelId() status() topSide() capturerPosition() imageHash() imageKey() resultJson() errorCode() attemptCount() gameSetId() createdAt() startedAt() finishedAt() updatedAt()`. 잘못된 전이는 `IllegalStateException`. **경쟁이 있는 전이는 엔티티 메서드가 아니라 리포지토리 조건부 UPDATE로 한다.**
- `ExtractionJobRepository`(모든 UPDATE는 `WHERE status=…` 조건, 갱신 행 수를 돌려줌):
  - `findByIdForUpdate(Long)` — 잠금 순서는 대결 → 작업
  - `findReusableCandidates(Long duelId, String imageHash, Instant queuedCreatedNotBefore, Instant runningStartedNotBefore)` → `List`, 최신(id 큰 것) 먼저, 첫 번째 사용. 저장 안 된 SUCCEEDED, 만료 전 QUEUED, 오래되지 않은 RUNNING
  - `int claimRunning(Long id, Instant now)` QUEUED→RUNNING
  - `int succeed(Long id, String resultJson, int attemptCount, Instant now)` RUNNING→SUCCEEDED. 0이면 결과를 버린다
  - `int fail(Long id, ExtractionErrorCode, int attemptCount, Instant now)` QUEUED|RUNNING→FAILED (BUSY도 이것)
  - `int expireStaleRunning(Long id, Instant startedBefore, Instant now)` → `EXTRACTION_TIMEOUT`
  - `int expireQueued(Long id, Instant createdBefore, Instant now)` → `EXPIRED`
  - `int supersede(Long duelId, String imageHash, Instant now)` 저장 안 된 QUEUED|RUNNING → `SUPERSEDED`
  - `int failAllUnfinishedAsInterrupted(Instant now)` 복구
- `ExtractionResult`(스키마 v1 record): `(int schemaVersion, String model, String dataDragonVersion, List<Attempt> attempts, TopResult topResult, String shotDate /*ISO*/, String duration, Team top, Team bottom)`; `Team(kills, deaths, assists, gold, List<Row> rows, List<Ban> bans)`; `Row(nickname, kills, deaths, assists, cs, gold, List<Integer> portraitBox, IconMatch champion)`; `Ban(List<Integer> box, IconMatch champion)`. `CURRENT_SCHEMA_VERSION = 1`. 직렬화는 Jackson 3 `JsonMapper`(`tools.jackson.databind.json.JsonMapper`). 챔피언 칸은 `champion.IconMatch`를 그대로 쓴다(JSON의 `{candidates[{id, score}], margin, cropFailed}`와 같은 모양). §1 목록과 다른 점: `attempts[]`에 `errorType`(예외 클래스 단순 이름, 메시지 아님)이 하나 더 있다.

**ingest.extract** (A가 구현, B·C가 사용)
- `ScoreboardExtractor.extract(byte[] image, String mimeType, Duration requestTimeout) throws ExtractionException` → `ExtractedScoreboard`. **설계의 2인자에서 `requestTimeout`을 더했다**: 요청마다 `min(설정, 남은 시간)`을 SDK 제한으로 넘기려면 필요하다. 재시도는 하지 않는다.
- **`ResilientExtractor.extract(byte[] image, String mimeType, Deadline deadline) throws ExtractionException` → `ExtractionOutcome`** — 설계에 없던 인터페이스다. A의 `RetryingExtractor`가 이것을 **구현**하고(ScoreboardExtractor를 감쌈), B의 `ExtractionRunner`는 이 인터페이스에만 의존한다. 그래야 B가 A의 구현 없이 `FakeScoreboardExtractor`로 끝낼 수 있다.
- `ExtractionOutcome(ExtractedScoreboard scoreboard, List<Attempt> attempts)`, `Attempt(int number, Integer httpStatus, String errorType, long latencyMs, long backoffMs)`.
- `ExtractedScoreboard(TopResult topResult /*WIN|LOSS*/, LocalDate shotDate, String duration, ExtractedTeam top, ExtractedTeam bottom)`; `ExtractedTeam(kills, deaths, assists, gold, List<ExtractedRow> rows, List<Box> banBoxes)`; `ExtractedRow(nickname, kills, deaths, assists, cs, gold, Box portraitBox)`; `Box(int ymin, int xmin, int ymax, int xmax)` + `toList()`, `toArray()`(= `Boxes.pixels`가 받는 `int[]`), `Box.fromList(List<Integer>)`. 범위·면적 검증은 A의 Validator 몫(record는 값만 담음).
- `ExtractionException`(검사 예외): `(ExtractionErrorCode, Integer httpStatus, boolean transientError, String message)`, `(ExtractionErrorCode, String message)`; `errorCode() httpStatus() transientError() attempts()`; `withAttempts(List<Attempt>)`는 시도 기록을 붙인 복사본. `attempts().size()`가 실패 작업의 `attempt_count`.
- `Deadline`: `Deadline.of(Clock, Instant startedAt, Duration total)`, `at() remaining()`(음수 없음) `isExpired() canStartRequest()`(남은 시간 ≥ 5초) `requestTimeout(Duration configured)`(= min).
- 패키지 순환 주의: `ingest.extract`가 `ingest.job.ExtractionErrorCode`를 쓰고, `ingest.job.ExtractionResult`가 `ingest.extract.Attempt`·`ExtractedScoreboard.TopResult`를 쓴다(클래스 순환은 없음).

**ingest.image** (B): `InspectedImage(byte[] bytes, String mimeType, String hash, int width, int height)`, `ImageStore { String put(String hash, String mimeType, byte[] bytes); byte[] get(String key); }` — 잘못된 키는 `IllegalArgumentException`, IO 실패는 `UncheckedIOException`.

**champion** (A가 구현, B·C가 사용)
- `Champion(String id, String key, String name)`.
- `ChampionCatalog { Optional<CatalogSnapshot> current(); }` — 한 작업/요청 안에서 한 번만 부르고 그 스냅샷을 끝까지 쓴다.
- `CatalogSnapshot(String version, List<Champion> champions, ChampionIconMatcher matcher)` + `contains(String id)`.
- **`ChampionIconMatcher { IconMatch top(BufferedImage crop, boolean ban); }`** — 설계의 `IconMatcher`가 스냅샷에 들어갈 자리를 인터페이스로 뺐다. A의 이식 클래스 `IconMatcher`(PICK/BAN)는 이것을 **구현**한다(클래스 이름은 설계 그대로).
- `IconMatch(List<Candidate> candidates /*최대 3, 점수 내림차순*/, double margin, boolean cropFailed)`, `IconMatch.Candidate(String id, double score)`, `IconMatch.failedCrop()`(후보 비움, cropFailed=true).
- `DataDragonSource { byte[] fetch(String path) throws IOException; }` — 경로는 기준 주소 이하(`/api/versions.json`, `/cdn/{v}/data/ko_KR/champion.json`, `/cdn/{v}/img/champion/{id}.png`).

**common.config**: `AsyncConfig`(`@EnableAsync`, 빈 이름 `extractionExecutor` = 상수 `AsyncConfig.EXTRACTION_EXECUTOR`, core 1/max 1/**queue 2**, 접두사 `extraction-`, 종료 대기 30초). 넘치면 `TaskRejectedException`.

### 12.5 테스트 기반 (`backend/src/test/java/com/lolinhouse/ingest/`, `@IntegrationTest`에 이미 `@Import`됨)

- `FakeScoreboardExtractor`(`ScoreboardExtractor`와 `ResilientExtractor` 둘 다 구현, `@Primary` 빈): `thenReturn(ExtractedScoreboard)`, `thenThrow(ExtractionException)`를 차례로 쌓으면 호출마다 한 단계씩 소비하고 바닥나면 마지막 단계를 반복한다. 아무 시나리오도 없이 부르면 `IllegalStateException`. `beforeEachCall(Runnable)`(래치로 경쟁 만들기), `calls()`, `lastRequestTimeout()`, **`reset()`**. `ResilientExtractor`로 불리면 성공은 `Attempt` 1개짜리 `ExtractionOutcome`, 실패는 `withAttempts`로 시도 1개가 붙은 예외.
- `FakeDataDragonSource`(`@Primary`): 챔피언 N개(기본 20, id `Champion00`~, 키 `1`~, 이름 `챔피언00`~), 버전 `16.20.1`. `setOffline(boolean)`, `fetchCount()`, `reset()`. 정적 `iconImage(int)`·`championId(int)`.
- `SyntheticScoreboardImage.defaultImage()`/`of(topPicks, bottomPicks, topBans, bottomBans)`: 1600x1000 PNG, `png()`, `portraitBox(top,row)`, `banBox(top,slot)`, `pickIndex/banIndex`, `scoreboard()`(같은 박스를 가진 `ExtractedScoreboard`: 위쪽 승리, 2026-10-12, 25:42, 닉네임 `합성선수T0..4`/`합성선수B0..4`). 기본 배치는 위쪽 픽 0~4, 아래쪽 픽 5~9, 위쪽 밴 10~14, 아래쪽 밴 15~19.
- `ExtractionResultFixture.sample()`/`from(image)`: 합성 결과창 기준 `ExtractionResult`(챔피언 1순위 = 그려 넣은 챔피언, 점수 0.9/0.5/0.4). C의 "작업 행 직접 INSERT" 픽스처, B의 결과 형식 단정 기준.
- `TestIngestConfiguration`: 위 두 가짜 빈 + 경로 덮어쓰기. **규칙: 가짜 빈은 컨텍스트 캐시 때문에 모든 통합 테스트가 하나를 공유한다. 건드린 테스트는 `@AfterEach`에서 `reset()`.**
- `ExtractionJobMappingTest`(@PersistenceTest)·`IngestFoundationTest`가 위 계약의 동작 예시다.

### 12.6 작업자별 바로 쓸 것

- **A**: `ScoreboardExtractor`(구현: Gemini·Disabled), `ResilientExtractor`(구현: `RetryingExtractor`), `ChampionIconMatcher`(구현: `IconMatcher`), `ChampionCatalog`, `DataDragonSource`(구현: HTTP), `Champion`·`CatalogSnapshot`·`IconMatch`. 테스트 자원: `FakeDataDragonSource`, `SyntheticScoreboardImage`, `FakeScoreboardExtractor`(안쪽 추출기), `Deadline`.
- **B**: `ExtractionJob`·`ExtractionJobRepository`(조건부 UPDATE 전부)·`ExtractionResult`·`ExtractionErrorCode`, `ResilientExtractor`(가짜로 테스트)·`ChampionCatalog`·`ChampionIconMatcher`·`IconMatch.failedCrop()`, `ImageStore`·`InspectedImage`, `AsyncConfig`, `DuelRepository.findByIdForUpdate`, `GameSetRepository.findFirstByImageHashAndDeletedAtIsNull`. 테스트 자원: `FakeScoreboardExtractor`·`FakeDataDragonSource`·`SyntheticScoreboardImage`·`ExtractionResultFixture`.
- **C**: `ExtractionResult`(읽기)·`ExtractionJob.linkGameSet/moveToDuel`·`ExtractionJobRepository.findByIdForUpdate`, `FearlessMode`·`Duel.of(…, mode)`, `DuelRepository`·`InhouseSessionRepository.findByIdForUpdate`, `GameSetRepository`의 신규 조회 6개, `CatalogSnapshot.contains`. 테스트 자원: `ExtractionResultFixture.sample()`로 작업 행 INSERT(`json.writeValueAsString`).
- **D**: `frontend/src/api/registrationTypes.ts`(§4.1 + `resolution` + `newDuel`, §5 응답, `Evaluation.teamChange`, `RegistrationProblemExtras`). `types.ts`의 `DuelSummary`에 `fearlessMode?`만 추가했다. `ApiError`/`ProblemBody` 확장(`RegistrationProblemExtras` 합치기)과 `errorMessages.ts`는 D 몫.

### 12.7 결정 한 줄 (리드가 되돌려도 되는 것)

`ResilientExtractor`·`ChampionIconMatcher` 인터페이스 추가, `ScoreboardExtractor`에 `requestTimeout` 인자, `Attempt.errorType`, `EXTRACTION_NOT_CONFIGURED` 코드, `InhouseSessionRepository.findByIdForUpdate`, `Evaluation.duplicateSet`의 모양을 `{setId, duelId, setNo}`로 가정한 것(C가 확정하면 `registrationTypes.ts`를 맞춘다), `Evaluation.detection.overlap`을 `number | null`로 둔 것(PENDING/FIRST_SET일 때 null).

## 13. 2026-10-20 결정 (재개 전 메모)
- 친구 아티팩트("League of Legends Team Maker")를 검토했다. 앱 안 밴픽으로 챔피언을 기록하는 방식이지만, **사용자는 결과창 아이콘 대조 방식을 유지하기로 결정** → 이 설계(§1~§12)는 그대로 유효하다.
- 화면·문서의 팀 표기는 **A팀/B팀**(TEAM1=A팀, TEAM2=B팀). 이 문서의 "1팀/2팀"은 A팀/B팀으로 읽는다. 재개 시 `DuelTeam.label`과 프런트 문구를 바꾼다(내부 값·DB 값은 유지).
- 대결 판수는 제한하지 않는다(3판/5판 시리즈 없음).
- 티어리스트·자동 팀 짜기는 M4.5로 분리(implementation-plan 마일스톤 표).
- 재개 일정: 2026-10-22(수). A·B·C·D 작업자가 남긴 미완성 파일 상태부터 확인한다.

