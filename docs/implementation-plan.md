# 롤 내전 전적 사이트 — 구현 계획

> 팀 표기(2026-10-20 결정): 화면·문서 표기는 **A팀/B팀**이다(예전 "1팀/2팀" 결정 대체). 내부 값은 그대로 `TEAM1`=A팀, `TEAM2`=B팀. 대결의 1세트는 블루 팀이 A팀.

## Context

친구 약 19명이 하는 롤 내전의 세트 결과창 스크린샷을 AI로 읽어 전적·랭킹·밴 순서를 기록하는 웹 서비스.
기획(PRD·기능명세서·유저플로우)과 와이어프레임이 확정됐다. 이 계획은 그 산출물을 실제 서비스로 만드는 순서·기술·작업 방식을 정한다.

- 기준 문서: `docs/LOLInhouse_PRD_2026-10-15.md`, `docs/LOLInhouse_기능명세서_2026-10-15.md`, `docs/유저플로우.md` (충돌 시 유저플로우의 최신 결정 우선), `docs/handoff.md`(배경)
- 와이어프레임: https://claude.ai/artifact/RntyJ6inys4piZAUHx1rHq (다크/라이트, 세트 한 줄+펼치기, 등록→확인 2단계)
- 현재 저장소 상태: `lol-inhouse/`에는 `docs/`만 있고 git 저장소가 아니다. 코드는 없다.
- 사용자 결정(2026-10-16): Claude가 구현하고 단계마다 **이유를 설명**, Codex 병행, React SPA + REST, Gemini 무료 등급, AWS 우선(비용이 나면 이전 검토), v1부터 AI 추출, 모바일 미지원.

## 기술 스택

| 영역 | 선택 | 이유 |
|---|---|---|
| 백엔드 | Java 21, Spring Boot 4.1.1, Gradle(Groovy) | 원래 3.5.x였으나 M1 착수 시 Initializr에서 제외되어 4.1.1로 변경(ADR 001) |
| 의존성 | Web, Data JPA, Validation, Security, Actuator, Flyway, MySQL Driver, AWS SDK v2(S3), Google Gen AI Java SDK, Apache Commons Text | 각각 필요 기능에 1:1 대응. Redis·큐·QueryDSL은 근거가 생기기 전까지 제외(인수인계서 §3 원칙) |
| DB | MySQL 8 | 인수인계서 전제. 로컬은 Docker Compose |
| 프런트 | React + TypeScript + Vite, React Router, TanStack Query, CSS 변수 테마(와이어프레임 토큰 그대로) | 사용자 선택. 서버 상태 캐싱은 TanStack Query로 단순화 |
| AI 추출 | Gemini API 무료 등급(구조화 JSON 출력 + 아이콘 위치 박스) | OpenAI API는 무료 등급 없음 확인. Gemini 무료 등급은 한도가 공식 미공개·변동 → 추출기를 인터페이스로 분리해 교체 가능하게 |
| 챔피언 식별 | 결과창 초상화·밴 아이콘을 잘라 Data Dragon 공식 아이콘과 이미지 유사도 비교 → 챔피언 키 | 확정 결정. 비용 0, 결정적 |
| 인증 | Spring Security 세션 쿠키(HttpOnly, SameSite) + 단일 담당자 계정(BCrypt) | SPA와 API를 같은 도메인(CloudFront 경로 라우팅)에 두면 JWT보다 단순·안전 |
| 비동기 | `@Async` + `extraction_job` 테이블 상태 폴링 | 세트당 수 초, 동시 사용자 1명 → 큐 불필요 |
| 배포 | Docker 이미지, GitHub Actions, EC2 1대(Docker Compose: 앱+MySQL), S3(원본 이미지·DB 백업·프런트), CloudFront | 커리어 목표(Docker→AWS→CI/CD)와 직결, 비용 최소. RDS는 크레딧 소진 후 비용이 커서 보류(근거 생기면 이전) |

### Firebase 이전 검토 결과 (사용자 요청)
- Firebase는 Spring Boot를 직접 실행하지 못한다. Hosting이 React 정적 파일을 서빙하고, API는 Hosting 리라이트로 **Cloud Run 컨테이너**에 보내는 구조가 된다(Cloud Run 무료 할당량 있음).
- 문제는 DB다. MySQL을 쓰려면 Cloud SQL(무료 등급 미확인, 유료로 알려짐)이나 외부 DB가 필요하고, Firestore로 바꾸면 데이터 계층을 다시 짜야 한다.
- 결론: "Firebase로 전환"은 사실상 "GCP Cloud Run + 별도 DB"로의 이전이다. 대비책으로 **컨테이너화, 설정 외부화(yml/환경변수), 이미지 저장소 인터페이스 분리**를 처음부터 지켜 이전 비용을 낮춘다. 실제 판단은 AWS 크레딧 만료 1개월 전에 실제 청구액을 보고 한다.
- AWS 신규 계정은 크레딧 기반 무료 플랜(최대 6개월)으로 알려져 있으나 공식 문서로 확인하지 않았다 → 계정은 배포 마일스톤 시작 시 만들고(크레딧 시계를 늦추기 위해), 첫날 AWS Budgets 알람을 건다.

## 저장소 구조

```
lol-inhouse/
  backend/      Spring Boot (패키지: auth, player, session(내전 묶음·대결·세트), ingest(추출), champion, stats, audit, common)
  frontend/     React + Vite
  infra/        docker-compose(로컬·운영), 배포 스크립트, 백업 스크립트
  tools/extraction-lab/   AI 추출 실험 하네스(샘플→정확도 리포트)
  docs/         기존 기획 문서 + adr/(설계 결정 기록) + ops/(운영 일지·런북)
  CLAUDE.md     프로젝트 규칙(네이밍, "변경마다 이유 설명", 테스트 원칙)
```

## 데이터 모델 (인수인계서 §7 대체, M3 V2 스키마로 확정)

실제 DDL: `backend/src/main/resources/db/migration/V2__core_schema.sql`, 설계: `docs/design/m3-design.md`.

- `admin_account`(username unique, password_hash BCrypt) — 비었을 때만 env로 1회 생성
- `player`(game_nickname, real_name null, main_position null) — 등록 여부 = `real_name IS NOT NULL`, 표시 이름 = 실명 없으면 game_nickname(계산, 저장 안 함)
- `player_alias`(player_id, nickname, nickname_key unique `utf8mb4_0900_bin`) — 키는 Java `NicknameNormalizer.comparisonKey`. 자동 연결(한 글자 오인식·잘림)은 별칭으로 저장하지 않음
- `inhouse_session`(start_date unique) → `duel`(session_id, seq, (session_id, seq) unique) → `game_set`(duel_id, set_no, team1_side, winner_team, top_team null, shot_date, duration_sec, 팀별 표시 K/D/A/골드 8개, fingerprint, image_hash, image_key, deleted_at)
- `game_set_player`(game_set_id, player_id, team, position, screen_row, champion_key, kills, deaths, assists, cs, gold)
- `game_set_ban`(game_set_id, turn 1~10, side, champion_key) — turn 홀수 BLUE / 짝수 RED CHECK
- 열거형은 `VARCHAR ascii_bin` + CHECK, 시각은 `DATETIME(6)` UTC(`Instant`), 날짜는 `DATE`(`LocalDate`)
- 지문·세트 번호 중복 차단: STORED 생성 컬럼 `active_fingerprint`, `active_set_no`(삭제되면 NULL)에 UNIQUE → 동시 저장·복구 충돌까지 DB가 막음
- `extraction_job`(M4, V3): 추출 작업 상태·읽은 사실(`result_json`)·이미지 해시/키·저장된 세트 연결. `duel.fearless_mode`(V3): RESET/CONTINUE
- `player_lane_tier`(M4.5, V4): 선수 × 라인 티어(1~6·상중하) + 마지막 변경(▲▼). `score_setting`(V4): 점수표 1행. `player.flagged`(V5): 요주 인물 표시
- 미뤄 둔 테이블: `team_swap`·`audit_log`(M6)
- 점수·피어리스·통계는 저장하지 않고 조회 시 계산(데이터 규모가 작아 정합성 우선)

## 핵심 도메인 규칙 (순수 Java, TDD 대상)

1. 포지션 배정: 위쪽 첫 행=캡처한 사람 포지션, 나머지 행은 그 포지션을 뺀 기본 순서. 아래쪽은 탑→서폿
2. 밴 순서: 진영 선택 + 팀별 위3·아래2 위치 → B1 R1 B2 R2 B3 R3 B4 R4 B5 R5
3. 세트 지문: 날짜 + 경기 시간 + 양 팀 킬 합 + 양 팀 골드 합
4. A팀/B팀 판별: 직전 세트와 선수 겹침 다수. 1세트는 블루=A팀. 진영 기본값: A팀 홀수 세트 블루
5. 닉네임 매칭: 정확 일치 → 잘린 닉네임 앞부분 ~70% → 후보 → 미등록 선수 생성
6. 통계: 누적 KDA=총(K+A)/총D(D=0이면 PERFECT), 세트 승률, 대결 승·무·패, 참여율, 포지션별 집계, 피어리스 누적
7. 저장 검증: 10명 중복 없음, 팀별 포지션 5개, 밴 10개, 지문 미중복

## 추출 파이프라인

`Ctrl+V 이미지` → `POST /api/admin/duels/{id}/extractions`(이미지+진영+캡처한 사람 포지션) → 이미지 해시 중복 검사 → S3 저장 → `extraction_job` 생성 → @Async: Gemini 호출(닉네임·K/D/A·CS·골드·날짜·시간·승패·각 초상화/밴 아이콘 박스, JSON 스키마 강제) → 박스로 잘라 아이콘 매칭 → 도메인 규칙 적용(포지션·밴 순서·선수 매칭·팀 판별·지문) → 초안 저장 → 프런트 폴링 → 확인 화면 → `POST /api/admin/duels/{id}/sets` 최종 검증·저장

## 마일스톤

각 마일스톤은 같은 사이클을 돈다: **계획(writing-plans) → 도메인은 테스트 먼저 → 구현 → 리뷰(Claude code-review + Codex) → 검증 → 사용자 승인 후 커밋/PR**.

| # | 내용 | 완료 기준 |
|---|---|---|
| M0 | AI 추출 실험(`tools/extraction-lab`): 샘플 3장 + 추가 수집분으로 Gemini 추출·아이콘 매칭 정확도, 지연, 무료 한도 체감 측정 | 필드별 정확도 리포트. 기준 미달 항목은 확인 화면 수동 수정 비중을 정해 설계 반영 |
| M1 | 저장소 골격: git, 모노레포, Spring Boot+Actuator+Flyway, Vite, Docker Compose(MySQL), GitHub Actions(빌드+테스트), 프로젝트 CLAUDE.md, ADR 001~ | CI 녹색, 로컬 `docker compose up`으로 앱·DB 기동, health 200 |
| M2 | 도메인 핵심 규칙 1~7 (순수 Java, 단위 테스트, 샘플 3장 수치를 픽스처로) | 유저플로우 §12 시나리오가 테스트로 통과 |
| M3 | 영속성·관리 API: 스키마, 담당자 로그인, 선수·별칭, 내전 묶음·대결 | Testcontainers 통합 테스트 통과 |
| M4 | 추출 파이프라인 + 등록·확인 화면(React) | 실제 결과창 Ctrl+V → 확인 → 저장이 로컬에서 동작 |
| M4.5 | 티어리스트·자동 팀 짜기(친구 아티팩트 "League of Legends Team Maker"의 기능·디자인을 React로 이식, 2026-10-20 결정): 선수별 라인 티어(1~6 + 상·중·하)와 회차별 변동 표시, 점수표(티어·상중하·라인 가감) 설정, 참가자 10명 → 라인 제약 안에서 점수 차가 작은 팀 후보 4개(같은 팀 조건, 라인별 수동 지정), 코인토스 진영. 서버에 티어·점수표·변동 이력 저장(담당자만 수정). **선수 관리 화면은 아티팩트 방식 그대로**(2026-10-20 사용자 결정): 사람 추가/수정 한 화면에서 이름·라인별 티어(1~6 + 상·중·하)·롤 닉네임 연동을 함께 편집. M3 선수 관리 화면을 대체한다. 앱 안 밴픽은 하지 않음(챔피언·밴은 결과창 아이콘 대조 유지) | 티어 수정이 모두에게 바로 반영, 같은 10명으로 팀 후보 계산이 친구 아티팩트와 같은 결과(계산 규칙 테스트) |
| M5 | 공개 조회: 홈, 내전 기록(세트 한 줄+펼치기), 대결·세트 상세, 선수 프로필, 랭킹, CSV | 와이어프레임 화면과 일치, 다크/라이트 |
| M5.5 | 내 PC 운영(2026-10-20 결정 — AWS 배포 전 실사용): **개발은 노트북, 실행은 데스크탑** — 데스크탑에는 Git·Docker Desktop·터널 프로그램만 설치하면 되도록 서버·화면을 Docker 이미지로 묶고 설치 안내서(체크리스트)를 둔다. 데스크탑 `.env`는 사용자가 직접 작성, DB는 새로 시작. 서버·화면을 한 번에 켜는 실행 방법, HTTPS 터널(예: Cloudflare Tunnel)로 사이트만 외부 공개(DB는 PC 내부 전용 유지), `COOKIE_SECURE=true`, 강한 관리자 비밀번호, 매일 DB 백업 파일. **터널 뒤 실제 접속 주소**: 지금 팀 짜기 호출 제한은 `getRemoteAddr()` 기준이라 터널 뒤에서는 모든 사용자가 한 주소로 묶인다 → 신뢰 프록시를 터널로 제한한 `X-Forwarded-For` 처리(헤더 위조 방지)를 함께 설정(2026-10-21 Codex 검토). **접속 주소(도메인)**: M5.5 시작 때 결정 — 추천은 먼저 터널 서비스의 고정 무료 주소로 시험 운영, 계속 쓰면 도메인 구매(사용자가 직접 결제·가입, AWS 이전 후에도 같은 주소 사용) | 친구들이 터널 주소로 접속해 기록·랭킹 조회, 관리자 등록·팀 짜기 동작, 백업 파일 복원 확인 |
| M6 | 수정·삭제·복구·변경 이력, 팀 교환, 피어리스·점수 초기화 | 수정 후 점수·피어리스·통계·CSV 재계산 테스트 |
| M7 | 배포: AWS 계정·Budgets 알람, EC2+Docker Compose, S3+CloudFront, GitHub Actions 배포, CloudWatch 알람, 일일 DB 백업→S3 + **복원 테스트**, 롤백 절차, 시크릿 관리 | 운영 URL에서 등록→조회 성공, 복원 리허설 기록 |
| M8 | 출시·운영: 친구들 사용, 피드백, 운영 일지(원인→조치→결과), 주간 등록 수 | 운영 시작 |

## 개발 방식 · 스킬 · 에이전트

- **설정**: 프로젝트 `.claude/settings.json`에 `"model": "opusplan"` 제안(전역 지침). Codex 리뷰 게이트는 이미 켜져 있음.
- **설명 요구 반영**: 각 작업 전 "무엇을, 왜"를 짧게 설명하고, 되돌리기 어려운 설계 결정은 `docs/adr/NNN-*.md`(배경·선택·대안·결과)로 남김 → 면접 준비 자료로도 사용.
- **계획**: `superpowers:writing-plans`로 마일스톤별 세부 계획, 설계 판단은 `architecture-planner`(opus).
- **구현**: `superpowers:subagent-driven-development` + `implementer`(sonnet). 도메인 로직은 `superpowers:test-driven-development`(또는 사용자 `tdd` 스킬). 단순 탐색·치환은 `quick-tasks`(haiku).
- **Spring 참고 스킬**: `developer-kit-java:spring-boot-project-creator`(M1), `spring-data-jpa`, `spring-boot-rest-api-standards`, `spring-boot-actuator`, `spring-boot-test-patterns`, `devkit.java.write-integration-tests`(Testcontainers), `aws-sdk-java-v2-s3`.
- **Codex 역할**: 응답 종료 시 리뷰 게이트, 까다로운 부분(아이콘 매칭, 지문 동시성)은 `codex:rescue`로 독립 구현·진단을 받아 비교.
- **검증**: `superpowers:verification-before-completion`, 배포 전 `security-review`, 마일스톤 끝에 `code-review`.
- **Git**: GitHub 저장소, 마일스톤별 브랜치·PR, 커밋은 사용자 승인 후에만.

## 학습 기록 (사용자 요청, 2026-10-16)

구현하면서 사용자가 나중에 공부할 수 있도록 모든 설계와 코드를 기록한다. 코드에 설명을 몰아넣으면 읽기 어려워지므로 **설명은 문서에, 코드에는 역할 요약과 문서 링크**를 둔다.

- `docs/study/NN-주제.md`: 마일스톤·기능 단위 학습 노트. 작업이 끝날 때마다 작성한다(로컬 전용, git 제외).
  1. 무엇을 만들었나 (한 줄)
  2. 왜 이 구조인가: 선택지, 고른 이유, 버린 대안과 그 이유
  3. 흐름: "어디서 → 어디로, 어떤 데이터가, 누구에게, 그다음 무엇이" 단계별 (필요하면 시퀀스 다이어그램)
  4. 파일·클래스·메서드 지도: 각 파일의 역할, 주요 메서드가 언제 호출되는지
  5. 변수·필드 사전: 주요 변수/필드/설정값이 무엇을 담고 어디서 쓰이는지
  6. 개념 설명: 처음 나오는 Spring·JPA·React·AWS 개념을 쉬운 말로 (예: 트랜잭션 경계, 영속성 컨텍스트, CORS)
  7. 함정과 디버깅: 실제로 부딪힌 오류와 원인→조치→결과
  8. 스스로 점검할 질문: 면접식 질문과 답의 위치
- `docs/adr/NNN-*.md`: 되돌리기 어려운 설계 결정만 짧게 (배경·결정·대안·결과).
- 코드 주석: 클래스·공개 메서드에 "역할 + 관련 학습 노트 경로"를 적고, 규칙이 숨어 있는 줄(예: 밴 순서 계산, 지문 생성)에는 그 규칙을 한 줄로 적는다. 뻔한 줄에는 달지 않는다.
- 채팅: 각 작업 전에 무엇을 왜 하는지 짧게 설명하고, 작업 후 학습 노트 위치를 알려준다.
- `docs/study/README.md`: 노트 목차와 추천 읽는 순서(로컬 전용).

## 검증 방법

- 단위: 도메인 규칙 1~7, 샘플 3장의 실제 수치(K/D/A·골드·포지션)를 픽스처로 사용
- 통합: Testcontainers MySQL로 저장·지문 UNIQUE·복구 충돌·통계 쿼리
- 추출 품질: `tools/extraction-lab` 리포트(필드별 정확도, 세트당 지연), 결과는 README에 수치로 기록
- 프런트: Vitest + Testing Library로 확인 화면 재계산(진영·포지션 변경 시 정렬·밴 순서)
- 종단: 로컬과 운영에서 실제 결과창으로 등록→확인→저장→공개 화면 반영 수동 점검, 배포 후 `/actuator/health` 스모크

## 사용자 준비물

- 결과창 샘플 추가 수집(최소 10장, 캡처한 사람이 탑·정글·서폿인 판 포함 → 유저플로우 §11-9 검증)
- Gemini API 키(Google AI Studio), GitHub 저장소, (M7에서) AWS 계정
