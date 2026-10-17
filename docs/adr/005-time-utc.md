# ADR 005. 시각은 UTC로 저장하고, 날짜는 변환 없이 저장한다

- 상태: 확정 (M3, 2026-10-16)
- 관련: `docs/design/m3-design.md` §2·§9-7, 학습 노트 [08](../study/08-persistence-schema.md), `application.yml`, `V2__core_schema.sql`
- 바뀐 점: M1에서 `hibernate.jdbc.time_zone: Asia/Seoul`이었던 것을 UTC로 바꾼다.

## 배경

시간이 닿는 곳이 여럿이고 각자 기본 시간대가 다르다.
- 개발 PC의 JVM(서울), CI 러너의 JVM(UTC), 운영 서버(AWS, 보통 UTC)
- MySQL 서버의 OS 시간대, MySQL 세션 시간대
- JDBC 드라이버(기본값이 JVM 시간대를 따름), Hibernate(`jdbc.time_zone`)

어디서 한 번이라도 어긋나면 "서울 00:30에 만든 기록이 전날로 보이거나 9시간 밀리는" 오류가 조용히 생긴다. 한편 결과창 날짜·내전 시작일은 "한국 사람이 달력에서 본 날짜"이고 시간대 개념이 아니다.

## 결정

1. **시각**(`created_at`, `updated_at`, `deleted_at`)은 `DATETIME(6)`에 **UTC 벽시계 값**으로 저장하고, 자바에서는 `Instant`로 다룬다.
2. **날짜**(`start_date`, `shot_date`)는 `DATE`/`LocalDate`로, 어떤 변환도 하지 않는다.
3. 드라이버·세션·Hibernate 모두 UTC로 맞춘다.
   - Hikari `data-source-properties`: `connectionTimeZone: UTC`, `forceConnectionTimeZoneToSession: true` (JDBC URL 출처와 무관하게 적용되며, 테스트에서 Testcontainers `@ServiceConnection` 환경에서도 유지됨을 확인)
   - `spring.jpa.properties.hibernate.jdbc.time_zone: UTC`
4. 시각 값은 앱이 `@PrePersist`/`@PreUpdate`에서 `Instant.now().truncatedTo(MICROS)`로 채운다. DB `DEFAULT CURRENT_TIMESTAMP`는 쓰지 않는다.
5. JVM 기본 시간대에 의존하는 코드는 금지한다. 서울 시간이 필요하면 `ZoneId.of("Asia/Seoul")`을 명시한다. 표시 변환은 가장자리(프런트 또는 응답 직전)에서만 한다.
6. 테스트로 고정한다: `TimeZoneRoundTripTest`. JVM 시간대는 `build.gradle`의 `TEST_TZ`(기본 `Asia/Seoul`, CI는 `UTC`), MySQL 컨테이너 OS는 `TZ=Asia/Seoul`로 일부러 서로 어긋나게 둔다.

## 대안

| 대안 | 버린 이유 |
|---|---|
| `TIMESTAMP` 타입 | 2038년 한계. 저장 시 세션 시간대에서 UTC로, 조회 시 다시 세션 시간대로 이중 변환이 일어나 설정 하나가 틀리면 값이 어긋남 |
| 모든 곳을 서울 시간(`Asia/Seoul`)으로 | 서울은 서머타임이 없어 단순해 보이지만, 서버·CI가 UTC인 환경과 어긋나며 "JVM 기본 시간대를 따르는" 부분이 숨어 있으면 환경마다 결과가 달라짐. 변환 지점이 여러 곳 |
| `OffsetDateTime`/`ZonedDateTime` 저장 | MySQL `DATETIME`에는 오프셋 정보를 저장할 수 없어 어차피 UTC 변환이 필요. 타입만 복잡해짐 |
| 날짜도 `Instant`/UTC 자정으로 저장 | 서울 날짜와 UTC 날짜가 하루 어긋나는 경계 문제를 스스로 만든다 |
| 시간대를 JDBC URL(`connectionTimeZone=UTC`)에만 지정 | 테스트·운영에서 URL을 다른 곳(Testcontainers 등)이 만들면 설정이 빠짐. Hikari `data-source-properties`는 URL과 무관하게 적용 |

## 결과 · 한계

- 장점: 변환 지점이 "드라이버/세션/Hibernate가 모두 UTC"로 한 군데이고, 어느 PC·서버에서 실행해도 DB 원문이 같다. JVM을 서울·UTC·로스앤젤레스로 바꿔 테스트가 모두 통과한다.
- 한계: DB 원문(예: 운영 DB를 SQL로 직접 볼 때)은 UTC라 서울 시간과 9시간 차이가 난다. 사람이 직접 볼 때 주의.
- 한계: `Instant` 왕복 테스트는 설정을 지워도 통과했다(Hibernate 7이 `Instant`를 UTC 기준으로 바인딩하는 것으로 보이나 원인을 확정하지 못함). 설정 누락은 세션 시간대 테스트(`connectionSessionTimeZoneIsActuallyUtc`, `hikariKeeps...`)가 잡는다.
- 미해결: `spring.jackson.time-zone: Asia/Seoul`(M1 설정)은 그대로다. API 응답의 `Instant`가 UTC ISO(`...Z`)로 나가는지는 API 작업에서 확인한다.
- 되돌리기: 이미 저장된 시각 값의 의미(UTC)가 정해지므로 나중에 시간대를 바꾸면 기존 데이터를 변환해야 한다. 그래서 ADR로 남긴다.
