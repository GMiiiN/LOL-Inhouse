# M3 설계: 영속성, 담당자 로그인, 선수/별칭, 내전 묶음/대결 API

> 작성: architecture-planner(Opus), 2026-10-16. 리뷰 전 초안. 리뷰 반영 내역은 문서 끝 "리뷰 반영"에 추가한다.
> 버전에 따라 동작이 다를 수 있는 부분은 **[확인 필요]**로 표시했고, 대부분 Phase 0~1 테스트로 판가름난다.

기존 계획에서 바꾼 점:
- `player`에 `is_registered`, `display_name` 컬럼을 두지 않고 `game_nickname`을 둔다(등록 여부·표시 이름은 계산).
- `game_set`에 `top_team`과 팀별 표시 합계 8개 컬럼을 추가한다.
- `hibernate.jdbc.time_zone`을 Asia/Seoul에서 UTC로 바꾼다.

## 0. 결정 요약

| # | 결정 | 이유 |
|---|---|---|
| D1 | 열거형 값은 `VARCHAR … CHARACTER SET ascii COLLATE ascii_bin` + `CHECK`. MySQL `ENUM` 타입은 쓰지 않음 | ENUM은 정의 순서로 정렬되고 값 추가에 ALTER가 필요하며 Hibernate 매핑 차이 위험. 기본 콜레이션(ai_ci)은 대소문자를 무시해 CHECK가 `'top'`도 통과시키므로 `ascii_bin`으로 막음 |
| D2 | 시각은 `DATETIME(6)` UTC + Java `Instant`. 날짜는 `DATE` + `LocalDate`. 드라이버·Hibernate 시간대 모두 UTC | 변환 지점이 하나, 2038년 한계 없음, JVM 기본 시간대 영향 없음 |
| D3 | 유효 세트 안에서만 UNIQUE는 STORED 생성 컬럼(`active_fingerprint`, `active_set_no`)에 UNIQUE | 동시 저장·복구 충돌을 DB가 막음. UNIQUE 인덱스는 NULL끼리 충돌하지 않음 |
| D4 | `extraction_job`은 M4로 미룸(V3) | 컬럼이 M4 파이프라인 설계에 달려 있음 |
| D5 | 초기화 = 같은 내전 묶음에 새 대결 생성. 별도 엔드포인트·컬럼 없음 | 기존 대결을 바꾸지 않으므로 "중간 취소로 기존 대결이 초기화"되는 문제가 구조적으로 없음 |
| D6 | 로그인은 `formLogin` + JSON 응답 핸들러, 본문은 form-urlencoded | 세션 고정 방지·CSRF 토큰 교체·인증 저장을 프레임워크가 처리 |
| D7 | CSRF는 `XSRF-TOKEN` 쿠키(JS 읽기 가능) + `X-XSRF-TOKEN` 헤더 | SPA 표준 패턴 |
| D8 | 담당자 계정은 `admin_account` 테이블. 비었을 때만 env로 한 번 생성 | 생성 후 운영 env에서 평문 비밀번호 제거 가능 |
| D9 | 연관관계는 GameSet 집합(세트·세트 선수·밴) 안에서만 매핑, 나머지는 `Long` id | 지연 로딩·N+1 함정 감소 |
| D10 | 새 의존성 없음 | 현재 `build.gradle`로 충분 |

## 1. 스키마 (`V2__core_schema.sql`)

### 1.1 공통 규칙
- 모든 테이블에 `ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci` 명시(Testcontainers는 compose 서버 옵션 없이 뜸).
- PK는 `BIGINT AUTO_INCREMENT` 대리키. 복합 PK 미사용.
- 제약 이름 `uk_<테이블>_<의미>`, `fk_…`, `ck_…`, `ix_…`. MySQL은 CHECK·FK 이름이 스키마 전체에서 유일해야 함.
- `ON DELETE`: 집합 내부 자식 `CASCADE`, 독립 엔티티 참조 `RESTRICT`. 앱 하드 삭제 대상은 별칭뿐.
- CHECK는 MySQL 8.0.16+에서 강제. 식이 NULL이면 통과.
- DB `DEFAULT CURRENT_TIMESTAMP` 없음. 시각은 앱만 씀.
- 열거형 값은 Java enum 상수 이름 그대로 저장(상수 이름 변경 시 데이터 불일치 주의).

### 1.2 DDL

```sql
-- 핵심 스키마. 설명: ADR 005·006

CREATE TABLE admin_account (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  username      VARCHAR(50)  NOT NULL,
  password_hash VARCHAR(100) NOT NULL,                 -- BCrypt(60자) + 여유
  created_at    DATETIME(6)  NOT NULL,                 -- UTC
  PRIMARY KEY (id),
  UNIQUE KEY uk_admin_account_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE player (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  game_nickname VARCHAR(100) NOT NULL,                 -- 대표 닉네임(normalize 표기). 미등록이면 표시 이름
  real_name     VARCHAR(50)  NULL,                     -- NULL = 미등록
  main_position VARCHAR(10)  CHARACTER SET ascii COLLATE ascii_bin NULL,
  created_at    DATETIME(6)  NOT NULL,
  updated_at    DATETIME(6)  NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT ck_player_main_position CHECK (main_position IN ('TOP','JUNGLE','MID','ADC','SUPPORT')),
  CONSTRAINT ck_player_real_name CHECK (real_name IS NULL OR CHAR_LENGTH(TRIM(real_name)) > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE player_alias (
  id           BIGINT       NOT NULL AUTO_INCREMENT,
  player_id    BIGINT       NOT NULL,
  nickname     VARCHAR(100) NOT NULL,                  -- NicknameNormalizer.normalize (대소문자 유지, 화면용)
  nickname_key VARCHAR(100) COLLATE utf8mb4_0900_bin NOT NULL, -- comparisonKey. 바이너리 비교 = Java equals
  created_at   DATETIME(6)  NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_player_alias_nickname_key (nickname_key),
  KEY ix_player_alias_player_id (player_id),
  CONSTRAINT fk_player_alias_player FOREIGN KEY (player_id) REFERENCES player (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE inhouse_session (
  id         BIGINT      NOT NULL AUTO_INCREMENT,
  start_date DATE        NOT NULL,                     -- 내전 시작일(한국 날짜, 변환 없음)
  created_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_inhouse_session_start_date (start_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE duel (
  id         BIGINT      NOT NULL AUTO_INCREMENT,
  session_id BIGINT      NOT NULL,
  seq        INT         NOT NULL,                     -- 묶음 안 "대결 N"
  created_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_duel_session_seq (session_id, seq),
  CONSTRAINT fk_duel_session FOREIGN KEY (session_id) REFERENCES inhouse_session (id) ON DELETE RESTRICT,
  CONSTRAINT ck_duel_seq CHECK (seq >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE game_set (
  id                      BIGINT       NOT NULL AUTO_INCREMENT,
  duel_id                 BIGINT       NOT NULL,
  set_no                  INT          NOT NULL,
  team1_side              VARCHAR(4)   CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  winner_team             VARCHAR(5)   CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  top_team                VARCHAR(5)   CHARACTER SET ascii COLLATE ascii_bin NULL, -- 결과창 위쪽(캡처한 사람) 팀. 수동 입력이면 NULL
  shot_date               DATE         NOT NULL,
  duration_sec            INT          NOT NULL,
  team1_displayed_kills   INT NOT NULL, team1_displayed_deaths INT NOT NULL,
  team1_displayed_assists INT NOT NULL, team1_displayed_gold   INT NOT NULL,
  team2_displayed_kills   INT NOT NULL, team2_displayed_deaths INT NOT NULL,
  team2_displayed_assists INT NOT NULL, team2_displayed_gold   INT NOT NULL,
  fingerprint             VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL, -- SetFingerprint.canonical
  image_hash              VARCHAR(64)  CHARACTER SET ascii COLLATE ascii_bin NULL,     -- SHA-256 hex
  image_key               VARCHAR(255) NULL,                                           -- S3 키(M4)
  deleted_at              DATETIME(6)  NULL,                                           -- 소프트 삭제(M6)
  created_at              DATETIME(6)  NOT NULL,
  updated_at              DATETIME(6)  NOT NULL,
  active_fingerprint      VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin
                          GENERATED ALWAYS AS (IF(deleted_at IS NULL, fingerprint, NULL)) STORED,
  active_set_no           INT GENERATED ALWAYS AS (IF(deleted_at IS NULL, set_no, NULL)) STORED,
  PRIMARY KEY (id),
  UNIQUE KEY uk_game_set_active_fingerprint (active_fingerprint),
  UNIQUE KEY uk_game_set_duel_active_set_no (duel_id, active_set_no),
  KEY ix_game_set_image_hash (image_hash),
  CONSTRAINT fk_game_set_duel FOREIGN KEY (duel_id) REFERENCES duel (id) ON DELETE RESTRICT,
  CONSTRAINT ck_game_set_set_no      CHECK (set_no >= 1),
  CONSTRAINT ck_game_set_team1_side  CHECK (team1_side IN ('BLUE','RED')),
  CONSTRAINT ck_game_set_winner_team CHECK (winner_team IN ('TEAM1','TEAM2')),
  CONSTRAINT ck_game_set_top_team    CHECK (top_team IN ('TEAM1','TEAM2')),
  CONSTRAINT ck_game_set_duration    CHECK (duration_sec > 0),
  CONSTRAINT ck_game_set_totals CHECK (team1_displayed_kills >= 0 AND team1_displayed_deaths >= 0
      AND team1_displayed_assists >= 0 AND team1_displayed_gold >= 0 AND team2_displayed_kills >= 0
      AND team2_displayed_deaths >= 0 AND team2_displayed_assists >= 0 AND team2_displayed_gold >= 0),
  CONSTRAINT ck_game_set_image CHECK ((image_hash IS NULL) = (image_key IS NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE game_set_player (
  id           BIGINT      NOT NULL AUTO_INCREMENT,
  game_set_id  BIGINT      NOT NULL,
  player_id    BIGINT      NOT NULL,
  team         VARCHAR(5)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  position     VARCHAR(10) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  screen_row   INT         NULL,
  champion_key VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  kills INT NOT NULL, deaths INT NOT NULL, assists INT NOT NULL, cs INT NOT NULL, gold INT NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_game_set_player_player     (game_set_id, player_id),
  UNIQUE KEY uk_game_set_player_position   (game_set_id, team, position),
  UNIQUE KEY uk_game_set_player_screen_row (game_set_id, team, screen_row),
  KEY ix_game_set_player_player_id (player_id),
  CONSTRAINT fk_game_set_player_game_set FOREIGN KEY (game_set_id) REFERENCES game_set (id) ON DELETE CASCADE,
  CONSTRAINT fk_game_set_player_player   FOREIGN KEY (player_id)   REFERENCES player (id)   ON DELETE RESTRICT,
  CONSTRAINT ck_game_set_player_team       CHECK (team IN ('TEAM1','TEAM2')),
  CONSTRAINT ck_game_set_player_position   CHECK (position IN ('TOP','JUNGLE','MID','ADC','SUPPORT')),
  CONSTRAINT ck_game_set_player_screen_row CHECK (screen_row BETWEEN 1 AND 5),
  CONSTRAINT ck_game_set_player_stats CHECK (kills >= 0 AND deaths >= 0 AND assists >= 0 AND cs >= 0 AND gold >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE game_set_ban (
  id           BIGINT      NOT NULL AUTO_INCREMENT,
  game_set_id  BIGINT      NOT NULL,
  turn         INT         NOT NULL,                 -- 1~10 = B1 R1 B2 R2 B3 R3 B4 R4 B5 R5
  side         VARCHAR(4)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  champion_key VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_game_set_ban_turn (game_set_id, turn),
  CONSTRAINT fk_game_set_ban_game_set FOREIGN KEY (game_set_id) REFERENCES game_set (id) ON DELETE CASCADE,
  CONSTRAINT ck_game_set_ban_turn CHECK (turn BETWEEN 1 AND 10),
  CONSTRAINT ck_game_set_ban_side CHECK ((turn % 2 = 1 AND side = 'BLUE') OR (turn % 2 = 0 AND side = 'RED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- M6에서 사용. 지금은 자리만 만든다
CREATE TABLE team_swap (
  id              BIGINT      NOT NULL AUTO_INCREMENT,
  duel_id         BIGINT      NOT NULL,
  before_set_no   INT         NOT NULL,
  position        VARCHAR(10) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  team1_player_id BIGINT      NOT NULL,
  team2_player_id BIGINT      NOT NULL,
  created_at      DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  KEY ix_team_swap_duel (duel_id, before_set_no),
  CONSTRAINT fk_team_swap_duel    FOREIGN KEY (duel_id)         REFERENCES duel (id)   ON DELETE RESTRICT,
  CONSTRAINT fk_team_swap_player1 FOREIGN KEY (team1_player_id) REFERENCES player (id) ON DELETE RESTRICT,
  CONSTRAINT fk_team_swap_player2 FOREIGN KEY (team2_player_id) REFERENCES player (id) ON DELETE RESTRICT,
  CONSTRAINT ck_team_swap_before_set_no CHECK (before_set_no >= 2),
  CONSTRAINT ck_team_swap_position CHECK (position IN ('TOP','JUNGLE','MID','ADC','SUPPORT')),
  CONSTRAINT ck_team_swap_players CHECK (team1_player_id <> team2_player_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- M6 변경 이력. FK 없음(대상이 바뀌어도 이력은 남김). 세부 필드 미확정이라 JSON 스냅샷
CREATE TABLE audit_log (
  id          BIGINT      NOT NULL AUTO_INCREMENT,
  target_type VARCHAR(30) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  target_id   BIGINT      NOT NULL,
  action      VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  actor       VARCHAR(50) NOT NULL,
  before_data JSON NULL,
  after_data  JSON NULL,
  created_at  DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  KEY ix_audit_log_target (target_type, target_id, created_at),
  CONSTRAINT ck_audit_log_action CHECK (action IN ('CREATE','UPDATE','DELETE','RESTORE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
```

### 1.3 설계 메모
- **player**: 등록 여부는 `real_name IS NOT NULL`, 표시 이름은 `COALESCE(real_name, game_nickname)`로 계산(같은 사실을 두 곳에 저장하지 않음). `game_nickname`은 생성 시 첫 별칭의 normalize 결과.
- **player_alias**: `nickname_key`는 Java `NicknameNormalizer.comparisonKey`로만 만든다(MySQL은 NFKC 불가). 콜레이션은 `utf8mb4_0900_bin`(ai_ci면 é/e·전각/반각이 같아져 Java와 DB 판단이 어긋남, `0900_bin`은 NO PAD). NFKC 확장 대비 컬럼 100자, 입력 50자 이하.
- **game_set**: `set_no`는 담당자 지정, `active_set_no`로 유효 세트 안 UNIQUE(삭제 후 번호 틈은 M6에서). `top_team`은 `defaultTopSide`에 필요. 캡처한 사람 포지션은 `top_team`의 `screen_row = 1` 선수로 알 수 있음. 팀별 표시 합계 8개는 지문 입력·합계 경고 재검사용. `fingerprint`는 Java가 계산해 저장(SQL로 두 번 구현하지 않음). 승패는 `team = winner_team`. `image_hash`는 UNIQUE가 아닌 일반 인덱스(1차 빠른 검사, 최종 중복 방지는 지문).
- **유효 세트 안 UNIQUE (MySQL 8.4)**: InnoDB UNIQUE는 NULL을 서로 다른 값으로 봄 → 삭제 세트는 충돌 없음. 복구 시 생성 컬럼 재계산으로 오류 1062 = 복구 충돌 감지(기본 409). STORED 선택(조회·디버깅 용이). 생성 컬럼은 엔티티에 매핑하지 않음(직접 쓰기 오류 3105).
- **DB가 보장하지 않는 것**: 선수 10명·밴 10개(행 개수), 팀 간 규칙 → `SetDraftValidator`와 서비스.

## 2. 시간 처리
- 타입: `start_date`, `shot_date` = `DATE`/`LocalDate`. `created_at`, `updated_at`, `deleted_at` = `DATETIME(6)` UTC/`Instant`. `TIMESTAMP`는 2038 한계·세션 시간대 이중 변환으로 버림.
- 설정: Hikari `data-source-properties`에 `connectionTimeZone=UTC`, `forceConnectionTimeZoneToSession=true`(URL 출처와 무관하게 적용) [확인 필요: 대문자 맵 키 표기, ServiceConnection DataSource에도 적용되는지]. `hibernate.jdbc.time_zone=UTC`. 시간대 테이블 의존 [확인 필요].
- 규칙: JVM 기본 시간대 사용 금지(필요하면 `ZoneId.of("Asia/Seoul")` 명시), `@PastOrPresent` 미사용, `@PrePersist`/`@PreUpdate`에서 `Instant.now().truncatedTo(MICROS)`.
- 왕복 테스트 `TimeZoneRoundTripTest`: Gradle test에 `user.timezone=Asia/Seoul`, Testcontainers `TZ=Asia/Seoul`. ① `@@session.time_zone`이 UTC ② `LocalDate` 왕복과 원본 문자열 ③ `Instant` 왕복과 원본 문자열. LocalDate 하루 밀림 시 대응 순서 [확인 필요].

## 3. JPA 매핑
- 패키지: `domain/`(M2), `common/config`, `common/web`, `auth/`, `player/`, `session/`.
- 엔티티 규칙: Lombok 없음, `protected` 기본 생성자 + 정적 팩토리, 의도를 드러내는 메서드, equals/hashCode 재정의 없음, `@Table`/`@Column` 이름 명시, `IDENTITY`, `@Enumerated(STRING)` [확인 필요: Hibernate 7이 MySQL에서 네이티브 ENUM을 기대해 validate 실패 시 `@JdbcTypeCode(SqlTypes.VARCHAR)` 또는 AttributeConverter], 생성 컬럼 미매핑.
- 연관관계: GameSet → GameSetPlayer/GameSetBan 양방향(`cascade=ALL, orphanRemoval=true`, 자식 `LAZY`). 그 외(GameSetPlayer→Player, PlayerAlias→Player, Duel→Session, GameSet→Duel, TeamSwap→Duel)는 `Long` id. 밴 `@OrderBy("turn")`, 선수는 Java에서 `Position` 순 정렬.
- 주의(M4~M6): 두 List 동시 fetch join → `MultipleBagFetchException`. `orphanRemoval` 교체 시 insert가 delete보다 먼저 → UNIQUE 위반, 제자리 수정 또는 삭제 후 flush.
- 엔티티 공개 API: `Player.registered/unregistered`, `name()`, `displayName()`, `isRegistered()`, `changeRealName`, `changeMainPosition`; `PlayerAlias.of(playerId, raw)`(키는 여기서만 계산), `toKnownAlias`; `InhouseSession.of`, `Duel.of`; `GameSet` 팩토리·`addPlayer`·`addBan`·`markDeleted`·`restore`·지문 재계산. Repository 메서드는 §7 작업자 A가 구현.
- 변환: 엔티티는 도메인을 참조, 도메인은 엔티티를 모름. id 변환은 `String.valueOf`/`Long.valueOf`.
- 검증 위치: 요청 DTO(Bean Validation, 형태) / 도메인(규칙) / 서비스(404, 여러 행 규칙, DB 위반→409) / DB(최종 보장). 서비스 `@Transactional`, 조회 `readOnly`, OSIV 꺼짐이라 DTO 변환은 서비스 안에서.

## 4. 보안 (Spring Security 7)
- 인가(위에서부터): health·info `permitAll` → `GET /api/csrf` `permitAll` → `/api/admin/login` `permitAll` → `/api/admin/**` `hasRole("ADMIN")` → `DispatcherType.ERROR` `permitAll` → 나머지 `denyAll`(공개 GET은 M5).
- `formLogin`: `loginProcessingUrl("/api/admin/login")`, `loginPage("/admin/login")`(기본 HTML 로그인 페이지 끔), 성공·실패 `AuthJsonResponder`. `logout`: POST `/api/admin/logout`, 204. `exceptionHandling` JSON, 리다이렉트 없음. `requestCache(disable)`. `httpBasic` 끔.
- 세션: 기본 `changeSessionId`(세션 고정 방지), `server.servlet.session.timeout: 2h`.
- CSRF: `csrf(c -> c.spa())` [확인 필요], 없으면 `CookieCsrfTokenRepository.withHttpOnlyFalse()` + `SpaCsrfTokenRequestHandler`. SameSite=Lax, Secure=`${COOKIE_SECURE}`. `GET /api/csrf`가 토큰을 호출해 쿠키 발급(204). 프런트는 시작·로그아웃 후 `/api/csrf` 호출, 변경 요청마다 쿠키 → `X-XSRF-TOKEN`, 403 `CSRF_INVALID`면 재발급 후 1회 재시도.
- 쿠키: JSESSIONID HttpOnly, SameSite=Lax, Secure `${COOKIE_SECURE:false}`(M7에 true).
- CORS 불필요(개발: Vite 프록시 `changeOrigin` 기본 false, 운영: CloudFront 같은 출처). 프록시에 `changeOrigin: true` 금지.
- 계정 초기화(D8): `app.admin.username/password`를 `@Value`로(record `toString` 노출 방지). `AdminAccountInitializer`(ApplicationRunner): 테이블이 비었을 때만 생성, 값이 비었거나 12자 미만이거나 UTF-8 72바이트 초과면 기동 실패(값은 로그에 넣지 않음), 이미 있으면 INFO 로그. `BCryptPasswordEncoder`, `AdminUserDetailsService`(ROLE_ADMIN) → Boot 기본 사용자 비활성 [확인 필요].
- 오류 응답(`application/problem+json`): 미인증 401 `UNAUTHORIZED`, 로그인 실패 401 `LOGIN_FAILED`(사용자 없음·비밀번호 틀림 동일), CSRF 403 `CSRF_INVALID`, 그 외 403 `FORBIDDEN`, 로그인 성공 200 `{username}`. CSRF 검사가 인증보다 먼저라 익명 + CSRF 없음 POST는 403.
- 무차별 대입: M3는 BCrypt 비용·동일 응답·실패 WARN 로그(IP만). IP별 제한은 M7(§8 Q7).

## 5. API (M3)
- 공통: ProblemDetail(RFC 9457) + `code`(필요 시 `errors`, `conflicts`). `ApiExceptionHandler`(`@RestControllerAdvice`, `ResponseEntityExceptionHandler` 상속): 400 `VALIDATION_FAILED`(+`errors[{field,message}]`), 400 `MALFORMED_REQUEST`, `ApiException`(notFound/conflict/badRequest), 500 `INTERNAL_ERROR`. URI kebab-case, 묶음은 `inhouse-sessions`. 시각 UTC ISO, 날짜 `"2026-10-15"`, enum 상수 이름. 201에 `Location`.
- 인증: `GET /api/csrf` 204, `POST /api/admin/login`(form-urlencoded + `X-XSRF-TOKEN`) 200/401/403, `POST /api/admin/logout` 204, `GET /api/admin/me` 200/401.
- 선수·별칭: `GET /api/admin/players`, `GET /api/admin/players/{id}`, `POST /api/admin/players`(`realName` 필수 50자, `mainPosition`, `nicknames` 1~10개 각 50자 이하, 요청 내 중복 400 `DUPLICATE_NICKNAME_IN_REQUEST`, 기존 충돌 409 `ALIAS_CONFLICT`+`conflicts`), `PUT /api/admin/players/{id}`(전체 치환, 미등록에 실명 → 등록, 등록 선수 실명 null → 400 `REAL_NAME_CANNOT_BE_CLEARED`), `POST /api/admin/players/{id}/aliases` 201/404/409, `DELETE /api/admin/players/{id}/aliases/{aliasId}` 204/404/409 `LAST_ALIAS`.
- 별칭 충돌: 사전 조회로 친절한 409, 동시 요청은 `saveAndFlush`의 `DataIntegrityViolationException`을 409로 변환 후 다시 던짐. 마지막 별칭 삭제 검사는 비원자적이나 담당자 1명이라 허용.
- **별칭 저장 규칙(리드 결정 2026-10-16)**: 별칭은 담당자의 명시적 행동으로만 만든다(선수 생성, 별칭 추가, M4에서 후보 선택·전체 닉네임 입력·NEW_UNREGISTERED). `ONE_CHAR_AUTO`·`TRUNCATED_AUTO`로 자동 연결된 읽은 글자는 저장하지 않는다. M4 세트 저장 요청에 행마다 "별칭으로 추가" 여부를 명시(기본 false, 자동 연결 행은 항상 false). 서버는 MatchType으로 추측하지 않는다. `PlayerService.addAlias`를 API와 M4가 공유.
- 내전 묶음·대결: `POST /api/admin/inhouse-sessions`(`startDate` 필수, 201 / 409 `SESSION_DATE_CONFLICT`+`existingSessionId`), `GET /api/admin/inhouse-sessions`(시작일 내림차순, `duelCount`), `GET /api/admin/inhouse-sessions/{id}`(대결 목록), `POST /api/admin/inhouse-sessions/{sessionId}/duels`(본문 없음, `seq = max+1`, 동시 요청 409 `DUEL_SEQ_CONFLICT`), `GET /api/admin/duels/{id}`. 초기화도 같은 POST(D5).

## 6. 테스트 계획
- 단위: `PlayerAlias.of` 키 = `comparisonKey`(대소문자·전각·NBSP), 100자 초과 거절, `Player.changeRealName`, `GameSet` 지문 재계산.
- JPA 슬라이스(`@DataJpaTest` + `replace = NONE` + Testcontainers) [확인 필요: Boot 4 패키지]: `SchemaConstraintTest`(JdbcTemplate 직접 INSERT, 위반당 테스트 하나: 별칭 키 중복·바이너리 콜레이션, 시작일 중복, (session, seq) 중복, 유효 지문·set_no 중복 → 삭제 후 재등록 성공 → 복구 실패, CHECK 위반들, 세트 선수 UNIQUE, FK RESTRICT) + 엔티티 경로 확인, `TimeZoneRoundTripTest`, `MigrationTest`.
- 통합: 메타 어노테이션 `@IntegrationTest`로 컨텍스트·컨테이너 재사용. `AuthIntegrationTest`(익명 401 problem+json·리다이렉트 없음, CSRF 유무별 401/403, 로그인 성공·쿠키 속성, 로그인 실패 동일 응답, CSRF 없는 로그인 403, **실제 SPA 흐름**(`/api/csrf` 쿠키 → 헤더로 로그인), 세션 고정, 로그아웃, 기본 로그인 HTML 없음, 기본 사용자 제거, 계정 초기화 멱등). API 테스트(`@WithMockUser` + `csrf()`): §5 모든 상태 코드.
- 환경: test에 `user.timezone=Asia/Seoul`, `TestcontainersConfiguration` public + `TZ`.

## 7. 작업 분할
같은 작업 트리에서 동시 작업 시 "자기 파일을 컴파일이 깨진 상태로 두지 않는다".
- **Phase 0 (A, 먼저)**: `build.gradle`(test `user.timezone`), `application.yml`(§2 시간대, §4 쿠키·세션, `app.admin.*`), `V2__core_schema.sql`, `TestcontainersConfiguration`(public, TZ), `IntegrationTest` 메타 어노테이션, `.env.example`(`COOKIE_SECURE`). 확인: `./gradlew test` 녹색.
- **Phase 1a (A)**: 엔티티·리포지토리(`player/*`, `session/*`). 확인: validate 통과.
- **Phase 1b (A)**: `SchemaConstraintTest`, `TimeZoneRoundTripTest`, `MigrationTest`, 엔티티 단위 테스트, 노트 08, ADR 005(시간 UTC)·006(유효 세트 UNIQUE).
- **Phase 1 (B, Phase 0 이후 A와 병렬)**: `SecurityConfig`(인가 규칙 먼저), `auth/*`, `AuthIntegrationTest`, `AdminAccountInitializerTest`, 노트 09, ADR 007.
- **Phase 2 (C, A의 1a 이후 코딩, B의 SecurityConfig 이후 테스트)**: `common/web/*`, `PlayerService`·컨트롤러·DTO, `InhouseSessionService`·컨트롤러·DTO, API 테스트, 노트 10.
- **리드**: AGENTS.md, 노트 README, implementation-plan 데이터 모델 절, 최종 검증, Codex 리뷰.

## 8. 위험과 사용자 확인 질문
1. 실명 공개(인터넷 공개 페이지에 친구 실명 노출) — 동의를 받을지, 표시 이름을 별명으로 할지.
2. 담당자 계정 저장 방식(DB 1회 생성 추천) + 비밀번호 최소 12자.
3. M3에 프런트(로그인·선수 관리·내전 관리 화면) 포함 여부.
4. 같은 세트 안 챔피언 중복(픽 중복, 밴 중복, 밴한 챔피언 픽) — 차단/경고/무시.
5. 선수 병합(오인식으로 따로 생긴 미등록 선수 합치기) — M6 후보 여부.
6. 잘못 만든 묶음·대결의 삭제/시작일 수정 — 지금 범위 밖 추천.
7. 로그인 속도 제한(M7) — AWS WAF vs 앱 단.
8. (정보) 복구 시 지문 충돌은 409 기본, 세트 삭제 후 번호 틈 처리는 M6.

## 9. 리뷰 반영과 사용자 결정 (2026-10-16, 구현은 이 절이 위 내용보다 우선)

### 사용자 결정
- **Q1 실명**: 등록 선수는 실명 표시. 사이트는 친구들끼리만 사용. 외부 공개 시 "내전 코드"로 접근 제한(AGENTS.md 개선 후보).
- **Q2 비밀번호**: 길이 하한 없음. 비어 있지 않고 UTF-8 72바이트 이하만 검사(BCrypt 한계).
- **Q3 프런트**: M3에 포함(작업자 D: 로그인·선수 관리·내전 묶음/대결 화면).
- **Q4 챔피언 중복**: **픽은 같은 세트 안 중복, 같은 대결 이전 세트와의 중복(피어리스 위반) 모두 저장 불가**(정말 겹치면 멤버 교체 후 초기화 = 새 대결로 등록해야 함). 밴은 같은 세트 안 중복만 저장 불가, 이미 픽된 챔피언을 밴하는 건 허용. → M2 `SetDraftValidator`의 피어리스 위반을 경고에서 **오류**로 바꾼다(이전 "경고만" 결정 대체). 합계 불일치·진영 불일치는 계속 경고.
- **Q5 선수 병합, Q6 묶음·대결 삭제**: 지금 범위 밖(M6 후보).
- **Q7 로그인 속도 제한**: M7에서 결정.

### Codex(Astra) 설계 리뷰 반영
1. **M6 자리·선수 교환**: 제자리 UPDATE는 `(game_set_id, team, position)`·`(game_set_id, player_id)` UNIQUE에 걸린다. 교환은 같은 트랜잭션에서 대상 행 삭제 → `flush` → 재생성으로 하고 실패 시 전체 롤백을 MySQL로 검증한다(M6 설계에 명시).
2. **대표 닉네임 삭제**: `game_nickname`과 같은 별칭을 삭제하면 남은 별칭 중 가장 오래된 것으로 `game_nickname`을 갱신한다(`Player.changeGameNickname`, 서비스가 같은 트랜잭션에서 처리).
3. **마지막 별칭 삭제 경쟁**: 별칭 삭제 시 선수 행을 `PESSIMISTIC_WRITE`로 잠근 뒤 개수 확인·삭제. 동시 삭제 테스트 추가.
4. **수동 입력의 `top_team = NULL`**: 직전 세트에 캡처 정보가 없으면 기본 진영을 제공하지 않고 담당자가 선택. `DuelTeamRules.defaultTopSide`는 2세트 이후 `previousTopTeam == null`이면 예외 대신 `Optional.empty()`. 테스트 추가.
5. **계정 초기화와 기존 테스트**: 테스트 전용 계정 설정을 `@IntegrationTest`에 넣고 기존 `ApplicationContextTest`도 이 어노테이션으로 전환(작업자 B). 계정이 이미 있으면 env 없이 기동되는 테스트 별도.
6. **`ascii_bin` 뒤 공백(PAD SPACE)**: JPA 경로에서는 발생하지 않아 이번엔 CHECK 보강 없이 노트에 주의 사항으로만 기록.
7. **시간 테스트 보강**: `flush` + `clear` 후 재조회, JDBC 원문 대조, 자정 경계·마이크로초 값, 실제 세션 시간대와 Hikari 설정 적용 확인("UTC" 문자열만 고집하지 않음). JVM 시간대 두 조합: `build.gradle` test의 `user.timezone`을 `System.getenv('TEST_TZ') ?: 'Asia/Seoul'`로 하고 CI는 `TEST_TZ=UTC`로 실행(로컬 서울·CI UTC로 두 조합 모두 검증).
8. **enum 매핑 통일**: 처음부터 모든 enum 필드에 `@Enumerated(EnumType.STRING)` + `@JdbcTypeCode(SqlTypes.VARCHAR)`. 모든 enum 저장·재조회 테스트.
9. **SPA 흐름 테스트 확장**: 실제 쿠키로 `/api/csrf` → 로그인 → 관리 POST → 로그아웃 → 이전 세션 거부 → 재로그인까지. `csrf.spa()`는 Security 7에 존재(리뷰어 확인).
10. **M4 입력 모델**: `DraftPlayer`에 CS·화면 행 번호가 없다. M4에서 저장 명령 DTO를 따로 두거나 초안을 확장한다(M4 설계에서 결정, 담당은 M4 작업자).
11. **공통 오류 응답 계약**: Phase 0에서 작업자 A가 `common/web/ProblemResponses`(ProblemDetail + `code` 작성기)를 먼저 만들고, B의 `AuthJsonResponder`와 C의 `ApiExceptionHandler`가 이를 사용.
12. **`team_swap`·`audit_log` DDL은 M6로 미룸**(필드가 미확정. V2에서 제외).

기술 위험(테스트로 바로 드러남): Hibernate 7 STRING enum과 MySQL ENUM 기대, LocalDate 하루 밀림, Hikari 대문자 키 바인딩, 시간대 테이블, `csrf.spa()` 존재, Boot 4 테스트 슬라이스 패키지, Jackson 3 ProblemDetail, BCrypt 72바이트, MySQL DDL 비트랜잭션(적용된 V2는 수정 금지, V3로), M4~M6 함정(orphanRemoval 순서, MultipleBag, 제약 이름 추출, `DraftPlayer`에 `cs` 없음).
