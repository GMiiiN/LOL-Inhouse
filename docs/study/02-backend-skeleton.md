# 02. 백엔드 골격: Spring Boot 4.1.1, 설정 외부화, Actuator, Testcontainers

> 마일스톤 M1. 저장소·.env·로컬 DB는 [01](01-repository-skeleton.md), 프런트는 [03](03-frontend-skeleton.md). 버전 결정 배경은 [ADR 001](../adr/001-spring-boot-4.md).

## 1. 무엇을 만들었나

도메인 코드가 하나도 없는 **실행 가능한 Spring Boot 백엔드 뼈대**(`backend/`).
앱을 띄우면 `.env`의 DB 정보로 MySQL에 접속하고, Flyway가 마이그레이션을 돌리고, JPA가 스키마를 검증하고, `/actuator/health`가 200 `UP`을 돌려준다.
Testcontainers로 진짜 MySQL을 띄워 이 흐름 전체를 검증하는 테스트 1개(`ApplicationContextTest`)가 있다.

실제로 확인한 결과(2026-10-16):
- `./gradlew test` 통과 (Docker Desktop 실행 중, Testcontainers가 `mysql:8.4`를 받아 띄움).
- `./gradlew bootRun`으로 로컬 compose MySQL(3307)에 붙여 `/actuator/health` = `UP`, `/actuator/health/readiness` = `UP` 확인. 그 외 경로(`/actuator/env`, `/api/x`)는 403.

## 2. 왜 이 구조인가

### Spring Initializr로 생성
- **선택**: `https://start.spring.io/starter.zip`을 curl로 호출(Gradle Groovy, Java 21, Boot 4.1.1, 패키지 `com.lolinhouse`).
- **이유**: 공식 도구가 만든 `build.gradle`·Gradle wrapper(`gradlew`)가 가장 표준적이라 문서·예제와 맞는다. 손으로 쓰면 BOM 버전 정렬 같은 곳에서 실수하기 쉽다.
- **Gradle wrapper**: `gradlew`가 프로젝트가 정한 Gradle(9.8.1)을 자동으로 받아 쓴다. 팀원/CI/내 PC가 같은 빌드 도구 버전을 쓰게 된다.
- **Groovy DSL**: 계획서(implementation-plan)의 결정. Kotlin DSL보다 강의·예제가 많다.

### Boot 3.5 대신 4.1.1
Initializr 목록에 3.5가 없어 4.1.1(정식 최신)로 정했다(근거는 ADR 001). Boot 3 강의로 공부한 사람이 생성된 프로젝트에서 직접 확인할 수 있는 차이만 적는다.

| 항목 | Boot 3 (강의) | 이 프로젝트(생성 결과에서 확인) |
|---|---|---|
| 스타터 이름 | `spring-boot-starter-web` | `spring-boot-starter-webmvc` (Initializr에서 "web"을 고르면 이렇게 들어온다) |
| Flyway | `flyway-core` 의존성 | `spring-boot-starter-flyway` 스타터가 따로 있다. Boot 4는 기능별로 모듈이 쪼개져 자동설정도 스타터가 있어야 켜진다 |
| 테스트 스타터 | `spring-boot-starter-test` 하나 | 기능별 테스트 스타터(`...-webmvc-test`, `...-data-jpa-test`, `...-flyway-test` 등)가 따로 생성됨 |
| MockMvc 어노테이션 패키지 | `org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc` | `org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc` (이 프로젝트 테스트에서 컴파일로 확인) |
| Jackson | Jackson 2 (`com.fasterxml.jackson`) | Jackson 3 (`tools.jackson.core:jackson-databind:3.1.5`). 패키지가 `tools.jackson`으로 바뀌었다. 설정 키 `spring.jackson.time-zone`은 그대로 존재 |
| Jakarta 계열 | Jakarta EE 10 | Tomcat 11.0.24(서블릿 6.1, Jakarta EE 11 계열), Jakarta Persistence 3.2.0, Hibernate 7.4.5, Spring Framework 7.0.9, Spring Security 7.1.1 |
| Testcontainers | 1.x (`org.testcontainers.containers.MySQLContainer`) | 2.0.5 (`org.testcontainers.mysql.MySQLContainer`, 모듈 `testcontainers-mysql`) |

`javax.*`가 아니라 `jakarta.*`인 것은 Boot 3부터 같다. 위 표에 없는 차이(예: 제거된 API)는 확인하지 못했으므로 단정하지 않는다. 막히면 공식 마이그레이션 가이드를 본다.

**MySQL용 Flyway 모듈**: Flyway 10부터 DB별 지원이 `flyway-mysql` 모듈로 분리됐다. 생성된 `build.gradle`에 `org.flywaydb:flyway-mysql`이 이미 들어 있어 추가 작업이 필요 없었다(`flyway-core`만 있으면 "Unsupported Database: MySQL 8.x" 오류가 난다).

### `application.yml` 하나로 설정, 비밀값은 `.env`
- 생성된 `application.properties`를 `application.yml`로 교체했다(계층 구조가 보여서 읽기 쉽다).
- 비밀값(DB 주소·계정)은 yml에 쓰지 않고 `${DB_URL}` 같은 **자리표시자**로 두고 값은 `.env`/환경변수에서 받는다. 코드는 로컬이든 운영이든 그대로다.
- **버린 대안**: `application-local.yml`에 값을 직접 적기 → 비밀번호가 파일로 남고 실수로 커밋될 위험.

### `ddl-auto: validate` + Flyway
- 테이블은 **Flyway SQL 파일**(버전 관리되는 마이그레이션)이 만들고, Hibernate는 "엔티티와 실제 테이블이 맞는지 검사"만 한다.
- **버린 대안**: `ddl-auto: update` → 엔티티를 바꾸면 Hibernate가 알아서 테이블을 바꾼다. 편하지만 무슨 변경이 언제 적용됐는지 기록이 없고, 컬럼 삭제·이름 변경은 못 하며, 운영 DB를 예상치 못하게 바꿀 수 있다. 생성 컬럼·UNIQUE 같은 DB 기능(계획서의 지문 중복 차단)도 SQL로 직접 써야 한다.

### 테스트: MockMvc + Testcontainers
- 실제 MySQL(컨테이너)을 쓴다. H2 같은 인메모리 DB는 MySQL과 SQL 방언·생성 컬럼·UNIQUE 동작이 달라 "테스트는 통과하는데 운영에서 깨지는" 일이 생긴다. 계획서도 지문 UNIQUE를 DB가 막도록 설계했으므로 진짜 MySQL이 필요하다.
- HTTP 호출은 `MockMvc`를 골랐다. 이유: (1) 포트를 열지 않아 빠르고 포트 충돌이 없다. (2) 서블릿 필터(Spring Security 필터 체인 포함)를 거치므로 "인증 없이 health 접근 가능"까지 검증된다. (3) Boot 4에서도 `@AutoConfigureMockMvc`는 그대로 쓰인다(패키지만 바뀜).
- **버린 대안**: `@SpringBootTest(webEnvironment = RANDOM_PORT)` + 실제 HTTP 클라이언트. 서버 직렬화·실제 포트까지 보는 E2E에 가깝다. 지금은 과하다. 나중에 배포 스모크가 필요하면 추가한다.
- 컨테이너 이미지는 생성기 기본값 `mysql:latest` 대신 **`mysql:8.4`로 고정**했다. 로컬 compose(`mysql:8.4`)와 같은 버전이어야 "테스트와 로컬/운영이 다르다"는 문제가 없다. `latest`는 어느 날 8.4→9.x로 바뀌어 테스트가 갑자기 깨질 수 있다.

## 3. 흐름

### 앱 시작 (`./gradlew bootRun` 또는 `java -jar`)

1. `BackendApplication.main` → `SpringApplication.run`. 스프링이 **Environment**(설정 값 모음)를 만든다.
2. `application.yml`을 읽는다. 여기에 `spring.config.import: optional:file:../.env[.properties]`가 있어, 현재 작업 디렉터리(`backend/`)의 한 칸 위 `.env`를 `.properties` 형식으로 추가로 읽어 Environment에 합친다. 파일이 없으면 `optional:` 덕분에 조용히 넘어간다(CI·운영).
3. 환경변수(OS)도 Environment에 들어 있다. `${DB_URL}` 같은 자리표시자는 이때 `.env` 또는 OS 환경변수 값으로 치환된다(둘 다 있으면 OS 환경변수가 우선 → 운영에서 `.env` 없이 환경변수만 줘도 동작).
4. `DataSource` 자동 설정: `spring.datasource.url/username/password`로 HikariCP 커넥션 풀 생성.
5. **Flyway** 실행: `flyway_schema_history` 테이블이 없으면 만들고, `classpath:db/migration`의 `V1__baseline.sql`을 적용한다. 이미 적용된 버전은 건너뛴다(체크섬 비교).
6. **JPA(Hibernate)** 초기화: `ddl-auto: validate`이므로 엔티티와 테이블을 비교만 한다. 아직 엔티티가 없어 검사할 것이 없다. Flyway가 JPA보다 먼저 끝나도록 Boot가 순서를 보장한다.
7. **Spring Security**: `SecurityConfig`의 `SecurityFilterChain`이 등록된다.
8. **Actuator** 엔드포인트 등록(`health`, `info`만). Tomcat 기동(8080).
9. 요청 `GET /actuator/health`: 보안 필터 체인(허용 규칙 통과) → HealthEndpoint → 각 HealthIndicator(DB 연결 확인 포함) 종합 → `{"status":"UP"}`.

종료 시(SIGTERM): `server.shutdown: graceful`이 새 요청을 받지 않고 처리 중인 요청을 끝낸 뒤 내려간다.

### 테스트 실행 (`./gradlew test`)
`ApplicationContextTest` → `@Import(TestcontainersConfiguration)` → `MySQLContainer` 빈 생성(Docker로 `mysql:8.4` 기동, 약 20초) → `@ServiceConnection`이 컨테이너의 접속 정보를 DataSource에 자동 연결(yml의 `${DB_URL}` 값보다 우선) → 위 4~8단계 동일 → MockMvc로 `GET /actuator/health` → 200 + `status=UP` 검증.

## 4. 파일·클래스·메서드 지도

| 파일 | 역할 |
|---|---|
| `backend/build.gradle` | 의존성·Java 21 toolchain. Initializr 생성물 그대로 |
| `backend/settings.gradle` | 프로젝트 이름 `backend` |
| `backend/gradlew`, `gradlew.bat`, `gradle/wrapper/*` | Gradle wrapper(빌드 도구 버전 고정) |
| `backend/.gitignore`, `.gitattributes` | 생성물. `build/`, `.gradle/` 제외, `gradlew`는 LF 고정 |
| `backend/src/main/java/com/lolinhouse/BackendApplication.java` | `main` 진입점 + `@SpringBootApplication`(컴포넌트 스캔 기준 패키지 `com.lolinhouse`). 생성물 그대로 |
| `backend/src/main/java/com/lolinhouse/common/config/SecurityConfig.java` | `securityFilterChain(HttpSecurity)`: 어떤 URL을 열고 닫을지 정의. 앱 시작 때 한 번 호출되어 빈으로 등록 |
| `backend/src/main/resources/application.yml` | 모든 설정(5장 사전) |
| `backend/src/main/resources/db/migration/V1__baseline.sql` | 주석뿐인 첫 마이그레이션. 실제 스키마는 M3에서 `V2__...`부터 |
| `backend/src/test/java/com/lolinhouse/ApplicationContextTest.java` | health 스모크 테스트 |
| `backend/src/test/java/com/lolinhouse/TestcontainersConfiguration.java` | `@ServiceConnection` MySQL 컨테이너 빈 (Initializr 생성물, 이미지 태그만 `8.4`로 고정) |
| `backend/src/test/java/com/lolinhouse/TestBackendApplication.java` | 생성물. IDE에서 `main`을 실행하면 컨테이너 MySQL로 앱을 띄우는 개발 보조 진입점(`SpringApplication.from(...).with(...)`). 지금은 쓰지 않지만 Initializr 표준이라 남겨 두었다 |
| `backend/HELP.md` | 생성물 안내문. `.gitignore`에 들어 있어 커밋되지 않는다 |

삭제한 파일: `application.properties`(yml로 대체), 생성된 `BackendApplicationTests`(`ApplicationContextTest`로 대체).

## 5. 변수·설정값 사전 (`application.yml`)

| 키 | 값 | 무엇을 하나 / 누가 읽나 |
|---|---|---|
| `spring.application.name` | `backend` | 앱 이름. 로그의 `[backend]`, 이후 메트릭 태그 등에 쓰임 |
| `spring.config.import` | `optional:file:../.env[.properties]` | 설정을 추가로 가져올 위치. Spring Boot의 ConfigData 로더가 읽는다. `optional:` = 없어도 오류 아님, `file:` = 파일시스템 경로(작업 디렉터리 기준), `[.properties]` = 확장자 없는 `.env`를 properties 형식(`KEY=값`)으로 해석하라는 힌트 |
| `spring.datasource.url` | `${DB_URL}` | JDBC 접속 주소. `.env`의 `DB_URL`. DataSourceAutoConfiguration(Hikari)이 읽음 |
| `spring.datasource.username` | `${MYSQL_USER}` | DB 계정 |
| `spring.datasource.password` | `${MYSQL_PASSWORD}` | DB 비밀번호 |
| `spring.jpa.open-in-view` | `false` | 아래 6장 참고 |
| `spring.jpa.hibernate.ddl-auto` | `validate` | 아래 6장 참고 |
| `spring.jpa.properties.hibernate.jdbc.time_zone` | `Asia/Seoul` | Hibernate가 `Instant`/`LocalDateTime` 등을 DB에 쓰고 읽을 때 쓰는 JDBC 시간대. 엔티티가 생기는 M3부터 실제 효과가 있다 |
| `spring.flyway.enabled` | `true` | Flyway 자동 실행 여부(기본값이 true이지만 의도를 드러내려고 명시). 읽는 곳: Boot의 FlywayAutoConfiguration |
| `spring.jackson.time-zone` | `Asia/Seoul` | JSON으로 날짜·시간을 직렬화할 때 기본 시간대. Boot 4.1.1의 `spring-boot-jackson` 메타데이터에 이 키가 있음을 확인 |
| `server.shutdown` | `graceful` | 종료 방식(6장) |
| `management.endpoints.web.exposure.include` | `health,info` | HTTP로 노출할 Actuator 엔드포인트 목록. 나머지(`env`, `beans` 등 민감 정보)는 노출하지 않음 |
| `management.endpoint.health.probes.enabled` | `true` | `/actuator/health/liveness`, `/readiness` 활성화 |

`.env`에서 이 백엔드가 쓰는 변수: `DB_URL`, `MYSQL_USER`, `MYSQL_PASSWORD`. (나머지 `GEMINI_*`, `ADMIN_*`는 이후 마일스톤에서 읽는다.)

주의: `.env`의 `MYSQL_PASSWORD`는 `${MYSQL_PASSWORD}`로 Environment에 들어오지만, `spring.config.import`로 가져온 파일의 키는 **프로퍼티 이름 그대로**(`MYSQL_PASSWORD`) 들어온다. `${...}` 치환은 Environment 전체에서 찾으므로 문제없다.

## 6. 개념 설명

### 설정 외부화와 `.env` 트릭
Spring은 설정을 여러 곳(yml, 환경변수, 명령줄 등)에서 모아 한 Environment로 합친다. 원래 Spring은 `.env` 파일을 모른다(그건 docker compose나 Node의 관례). `spring.config.import`는 "이 위치도 설정 파일로 읽어라"는 지시다. `[.properties]`를 붙이면 확장자가 없는 `.env`를 properties 형식으로 파싱한다. 그래서 같은 `.env` 하나를 docker compose, Spring, 실험 도구가 모두 읽는다. 경로가 `../.env`인 이유는 Gradle(`bootRun`, `test`)이 `backend/`를 작업 디렉터리로 실행하기 때문이다. IDE에서 저장소 루트를 작업 디렉터리로 실행하면 경로가 어긋나 `.env`가 읽히지 않을 수 있다(함정 참고).

### Actuator와 헬스 프로브
Actuator는 운영 중인 앱의 상태를 HTTP로 보여 주는 모듈. `health`는 앱과 DB 등 의존 대상이 살아 있는지 알려 준다. 프로브(probe)는 쿠버네티스/로드밸런서/모니터링이 주기적으로 찔러 보는 주소다.
- **liveness** (`/actuator/health/liveness`): "프로세스가 살아 있나". 실패하면 재시작 대상. DB가 잠시 죽었다고 앱을 재시작하면 안 되므로 DB 상태는 여기 포함하지 않는다.
- **readiness** (`/actuator/health/readiness`): "지금 요청을 받을 준비가 됐나". 종료 중이거나 기동 중이면 DOWN/OUT_OF_SERVICE → 트래픽을 보내지 않는다.
- `/actuator/health`는 기본적으로 `status`만 보여 준다(상세 정보 `show-details`는 꺼져 있어 DB 종류 등이 외부에 노출되지 않는다).
- 노출을 `health,info`로 제한한 이유: `env`, `beans`, `heapdump` 같은 엔드포인트는 비밀값·내부 구조를 드러낸다.

### 우아한 종료(graceful shutdown)
`immediate`는 종료 신호를 받으면 처리 중이던 요청도 끊는다. `graceful`은 새 요청은 거절하고 진행 중인 요청이 끝나길 기다렸다가(기본 최대 30초) 내려간다. 배포 때 요청이 중간에 끊겨 에러가 나는 것을 줄인다. Docker `stop`이 SIGTERM을 보내므로 컨테이너 배포(M7)와 맞는다.
**정정(Codex 리뷰)**: 처음에 "기본값은 immediate"라고 적었으나 틀렸다. Boot 4.1.1의 설정 메타데이터(`spring-boot-web-server-4.1.1.jar`)를 확인하면 `server.shutdown` 기본값은 이미 `graceful`이다(Boot 3.4부터 바뀐 것으로 알려짐). `application.yml`에 적어 둔 `graceful`은 동작을 바꾸려는 게 아니라 **의도를 드러내려고 명시**한 것이다.

### open-in-view = false
기본값(true)이면 HTTP 요청이 시작해서 끝날 때까지 JPA 영속성 컨텍스트(EntityManager)와 DB 커넥션이 열려 있다. 그러면 컨트롤러·뷰 단계에서 지연 로딩이 "우연히" 동작하는데, 쿼리가 어디서 나가는지 숨고 JSON 변환 도중에 쿼리가 터지며(N+1) 커넥션을 오래 잡는다. `false`면 트랜잭션(서비스) 안에서만 지연 로딩이 되고, 밖에서 접근하면 `LazyInitializationException`이 나서 문제를 일찍 알 수 있다. 서비스에서 필요한 데이터를 DTO로 다 만들어 내보내는 습관이 생긴다.

### ddl-auto: validate vs update
- `none`: Hibernate가 스키마를 건드리지 않음.
- `validate`: 시작할 때 엔티티와 테이블(컬럼 이름·타입)이 맞는지 검사, 안 맞으면 앱이 기동 실패. 스키마 변경은 Flyway가 담당.
- `update`: 부족한 테이블/컬럼을 자동 추가. 기록이 없고 위험해 운영 부적합.
- `create`/`create-drop`: 시작 때 스키마를 지우고 새로 만듦. 데이터 소실.
validate를 쓰면 "엔티티는 고쳤는데 마이그레이션을 빼먹음"을 테스트/기동 단계에서 바로 잡는다.

### Flyway
`db/migration/V{번호}__{설명}.sql` 파일을 번호 순서로 한 번씩만 실행하고, 실행 기록과 파일 체크섬을 `flyway_schema_history` 테이블에 남긴다. **이미 적용된 파일을 수정하면 체크섬 불일치로 기동 실패**한다. 그래서 스키마를 바꾸려면 새 번호 파일을 추가한다. 이 프로젝트의 `V1__baseline.sql`은 주석만 있으며 "Flyway가 연결·기록까지 정상 동작한다"를 보여 주는 용도다.

### Testcontainers와 @ServiceConnection
Testcontainers는 테스트 중에 Docker로 진짜 DB를 띄웠다 지운다. `@ServiceConnection`을 컨테이너 빈에 붙이면 Boot가 컨테이너의 임의 포트·계정을 읽어 `JdbcConnectionDetails`로 등록하고 DataSource가 그것을 쓴다. 예전 방식(`@DynamicPropertySource`로 url/username/password를 일일이 주입)을 대체한다. yml의 `${DB_URL}` 값은 이 연결 정보에 밀려 사용되지 않는다. Docker가 꺼져 있으면 컨테이너를 못 띄워 테스트가 실패한다.

### Spring Security 필터 체인 기본 동작
Security를 의존성에 넣기만 해도 모든 요청이 서블릿 필터 체인(`SecurityFilterChain`)을 먼저 지난다. 설정이 없으면 Boot 기본값은 "모든 요청 인증 필요 + 폼 로그인/Basic 인증 + 시작할 때 임시 비밀번호를 로그에 출력 + CSRF 보호".
- 이 프로젝트는 `SecurityFilterChain` 빈을 직접 정의해 기본 체인을 대체했다. `authorizeHttpRequests`에서 URL 규칙을 위에서부터 순서대로 평가한다: `/actuator/health/**`, `/actuator/info`는 `permitAll`, 나머지 `anyRequest().denyAll()`.
- 로그인 방식(formLogin/httpBasic)을 켜지 않았으므로 인증 수단이 없다. 거부된 요청은 403이다(직접 확인: `/actuator/env`, `/api/x` → 403).
- **CSRF**는 기본값(켜짐)이다. POST/PUT/DELETE는 CSRF 토큰이 없으면 막힌다. 세션 쿠키 로그인을 쓰는 M3에서 SPA와의 토큰 처리 방식을 정한다.
- 시작 로그에 `Using generated security password: ...`가 찍힌다. 로그인 수단이 없어 쓸 곳이 없는 임시 계정이며, 담당자 계정을 만드는 M3에서 사라진다. (운영 로그에 비밀번호가 남는 형태이므로 M3에서 반드시 정리.)

### 자리표시자 `${...}`의 동작 (확인한 것)
`.env`가 없는 환경(예: CI)에서 `DB_URL`이 정의되지 않은 채 테스트를 돌리면 어떻게 될지는 **이 환경에서 직접 확인하지 못했다**(내 PC에는 `.env`가 있다). Testcontainers가 연결 정보를 덮어쓰므로 문제없을 것으로 예상하지만, M1의 GitHub Actions 설정 때 CI에서 실제로 확인해야 한다.

## 7. 함정과 디버깅

1. **Initializr 요청 500 오류**: `bootVersion=4.1.1.RELEASE`로 요청하면 `Bom 'spring-boot-dependencies:4.1.1.RELEASE' could not be resolved` 500 응답이 온다. 메타데이터의 id는 `4.1.1.RELEASE`로 표시되지만 실제 요청에는 `bootVersion=4.1.1`을 써야 했다. 그리고 실패해도 응답 본문(JSON 에러)이 `.zip` 파일로 저장되므로 `unzip`이 "End-of-central-directory signature not found"로 실패한다. 파일을 열어 보면 원인을 알 수 있다.
2. **`mysql:latest`**: 생성된 Testcontainers 설정이 `latest`라서 compose의 8.4와 어긋난다. `8.4`로 고정했다.
3. **로컬 MySQL 포트**: PC에 설치된 MySQL 서비스가 3306을 점유해 compose MySQL은 호스트 포트 3307로 열려 있다(`.env`의 `DB_URL`이 3307). `bootRun`이 접속에 실패하면 이 포트부터 확인한다.
4. **임시 비밀번호 로그**: `Using generated security password`는 오류가 아니라 Boot의 기본 사용자 자동 설정 경고다(6장 "Spring Security 필터 체인" 참고).
5. **bootRun이 로컬 DB를 바꾼다**: `bootRun`을 한 번 실행하면 로컬 compose DB(`lol_inhouse`)에 `flyway_schema_history`가 생기고 V1이 기록된다. 의도된 동작이다. 되돌리려면 `docker compose ... down -v`로 볼륨을 지운다.
6. **첫 `./gradlew` 실행이 오래 걸림**: Gradle 배포판과 의존성을 처음 받느라 몇 분이 걸린다(이번엔 3분 반). 이후는 빠르다. Testcontainers 테스트도 첫 실행은 `mysql:8.4` 이미지 pull 때문에 느리다(약 80초).
7. **IDE에서 `.env`가 안 읽힘**: `../.env`는 작업 디렉터리가 `backend/`일 때만 맞다. IDE 실행 설정의 Working directory를 `backend`로 맞추거나, 환경변수를 직접 지정한다. 증상은 DB 접속 실패(인증 오류 또는 URL 해석 오류)로 나타날 것이다(이 증상은 직접 재현하지 않았다).
8. **확인하지 않은 것**: Flyway는 마이그레이션 파일이 하나도 없을 때(`db/migration` 폴더가 없는 경우)에도 기동이 되는지 시험하지 않았다. 요구사항에 따라 처음부터 `V1__baseline.sql`을 넣었다.

## 8. 스스로 점검할 질문

1. `.env`는 Spring이 원래 모르는 파일인데 어떻게 읽히나? 경로가 `../.env`인 이유와 `optional:`, `[.properties]`의 의미는? (→ 3장 흐름 2번, 5장 사전, 6장 "설정 외부화")
2. `.env`와 OS 환경변수에 같은 키가 있으면 어느 쪽이 이기나? 운영에서 이 성질이 왜 유용한가? (→ 3장 흐름 3번)
3. 앱 시작 시 DataSource, Flyway, JPA validate의 순서와 이유는? 마이그레이션을 빼먹으면 어디서 어떻게 실패하나? (→ 3장, 6장 validate)
4. `ddl-auto: update` 대신 `validate` + Flyway를 쓰는 이유 세 가지는? (→ 2장, 6장)
5. `open-in-view: false`일 때 컨트롤러에서 지연 로딩 연관을 읽으면 어떤 일이 생기고, 그게 왜 오히려 좋은가? (→ 6장)
6. liveness와 readiness의 차이는? DB 장애 시 어느 쪽이 DOWN이 되어야 하나? (→ 6장 Actuator)
7. `server.shutdown`을 `immediate`로 두면 배포 중 어떤 문제가 생기나? Boot 4의 기본값은? (→ 6장, 기본값은 graceful)
8. Actuator 엔드포인트를 `health,info`로만 노출한 이유는? (→ 6장)
9. 테스트에 H2가 아니라 Testcontainers MySQL을 쓰는 이유와, `@ServiceConnection`이 하는 일은? yml의 `${DB_URL}`은 테스트에서 어떻게 되나? (→ 2장, 6장)
10. 이 테스트가 MockMvc인 이유와 `RANDOM_PORT`와의 차이는? (→ 2장)
11. Spring Security 의존성만 추가했을 때 기본 동작은? 우리 `SecurityFilterChain`은 거기서 무엇을 바꿨고, 거부된 요청은 왜 401이 아니라 403인가? (→ 6장 보안. 힌트: 인증 진입점/로그인 방식이 설정되지 않음)
12. Boot 3 강의 코드를 Boot 4 프로젝트에 옮길 때 가장 먼저 부딪힐 차이 세 가지는? (→ 2장 표)
