# AGENTS.md — Codex 인수인계 (Claude Code가 작업을 넘길 때 이 파일부터 읽는다)

이 저장소는 Claude Code가 주로 구현하고, Claude의 사용량 한계에 걸리면 Codex가 이어받는다.
**규칙은 `CLAUDE.md`와 같다. 먼저 `CLAUDE.md`를 읽고 따른다.** 이 파일은 "지금 어디까지 왔고 다음에 무엇을 하는가"를 적는 곳이며, 단계가 끝날 때마다 갱신한다.

## 1. 읽는 순서
1. `CLAUDE.md` — 규칙(학습 노트 필수, 네이밍, 비밀값, 커밋은 사용자 승인 후)
2. `docs/implementation-plan.md` — 스택·데이터 모델·도메인 규칙·마일스톤
3. `docs/유저플로우.md` — 기능 동작의 최종 기준
4. 이 파일 §3(현재 상태)와 §4(다음 할 일)
5. `docs/study/README.md` — 지금까지의 학습 노트 목차

## 2. 작업 방식 (Claude와 동일하게 유지)
- 사용자는 코드를 직접 짜지 않고 나중에 공부한다. **모든 작업 단위마다 `docs/study/NN-주제.md` 학습 노트를 같은 변경에 포함**한다(형식은 CLAUDE.md). 노트 없이 "완료"로 보고하지 않는다.
- 되돌리기 어려운 결정은 `docs/adr/NNN-제목.md`.
- 작업 전 사용자에게 무엇을 왜 하는지 짧게 설명하고, 끝나면 노트 위치를 알린다. 한국어 존댓말, 간결하게.
- 도메인 규칙은 테스트 먼저(TDD). 완료 주장 전에 아래 §5 검증 명령을 실제로 돌려 출력으로 확인한다.
- 범위 밖 기능·근거 없는 기술(Redis, 큐, QueryDSL, Lombok 등)을 넣지 않는다.
- 비밀값(`.env`)을 출력·커밋하지 않는다. `samples/`(친구 닉네임 포함)는 커밋하지 않는다.
- git: 작업 브랜치에서만. **커밋·푸시·PR은 사용자 승인 후.**

## 3. 현재 상태 (마지막 갱신: 2026-10-16, Claude)

| 마일스톤 | 상태 |
|---|---|
| M1 저장소 골격 | 완료(검증됨). 브랜치 `m1-skeleton`, **아직 커밋 없음 — 사용자 승인 대기** |
| M0 AI 추출 실험 | 진행 중(Codex, `tools/extraction-lab/`, 노트 05·ADR 003 예정) |
| M2~M8 | 시작 전 |

M1에서 끝난 것:
- 루트: `.env`(사용자가 Gemini 키·담당자 계정 입력 완료), `.env.example`, `.gitignore`, `.gitattributes`, `CLAUDE.md`, `infra/docker-compose.yml`(MySQL 8.4, 호스트 포트 **3307** — PC의 기존 MySQL이 3306 사용 중)
- `backend/`: Spring Boot 4.1.1 / Gradle 9.8.1 / Java 21. Actuator(health·info), Flyway(V1 baseline), JPA validate, 임시 SecurityConfig(health만 허용, 나머지 denyAll), Testcontainers 테스트 1개
- `frontend/`: Vite 8 / React 19 / TS 6, React Router 7, TanStack Query 5, 다크·라이트 테마 토큰, 12개 라우트 자리, Vitest 4개 통과
- `.github/workflows/ci.yml`: 백엔드 test, 프런트 lint·test·build
- 학습 노트 01~03, ADR 001~002
- `samples/1~3.png` + `samples/ground-truth.json`(정답지)

M1에서 남은 것:
- 사용자 승인 받아 첫 커밋·푸시·PR (`.env` 없는 환경 테스트는 통과 확인, 노트 04 작성 완료)

## 4. 다음 할 일 (순서대로)

1. **M1 마무리**: 위 "남은 것".
2. **M0 AI 추출 실험** (`tools/extraction-lab` 또는 backend 내 실험용 Gradle 태스크):
   - Gemini(모델명은 `.env`의 `GEMINI_MODEL`)에 결과창 1장을 보내 구조화 JSON(닉네임, K/D/A, CS, 골드, 날짜, 경기 시간, 상단 승패, 각 초상화·밴 아이콘의 위치 박스)을 받는다.
   - 박스로 잘라 Data Dragon 공식 챔피언 아이콘과 이미지 유사도 비교 → 챔피언 키.
   - `samples/ground-truth.json`과 비교해 필드별 정확도·지연을 리포트(`tools/extraction-lab/out/`, git 제외)로 출력.
   - 결과 요약 수치와 판단을 학습 노트와 ADR로 남긴다. 결과창 레이아웃 사실은 `docs/유저플로우.md` §5.2 참고.
3. **M2 도메인 규칙**(순수 Java, TDD): 포지션 배정, 밴 순서(B1 R1 B2 R2 B3 R3 B4 R4 B5 R5), 세트 지문, 1팀/2팀 판별, 닉네임 매칭(잘린 닉네임 앞부분 ~70%), 통계, 저장 검증. 상세는 `docs/implementation-plan.md` "핵심 도메인 규칙".
4. 이후 M3~M8은 `docs/implementation-plan.md` 마일스톤 표 순서.

## 5. 검증 명령
- DB: `docker compose -f infra/docker-compose.yml --env-file .env up -d` (Docker Desktop 필요)
- 백엔드: `cd backend && ./gradlew test`
- 프런트: `cd frontend && npm run lint && npm test && npm run build`

## 6. 팀 구성 방안 (작업을 병렬로 나눌 때)
Claude Code에서는 아래처럼 나눠 왔다. Codex도 같은 경계로 작업을 쪼개면 서로 파일이 겹치지 않는다.

| 역할 | 맡는 파일 | 비고 |
|---|---|---|
| 리드(조율·검증) | 루트 설정, `docs/`, `AGENTS.md` 갱신, 최종 검증 | 다른 역할 결과를 직접 돌려 확인 |
| 백엔드 구현 | `backend/` | 도메인은 테스트 먼저 |
| 프런트 구현 | `frontend/` | 와이어프레임 기준(아래 링크) |
| 추출 실험·파이프라인 | `tools/extraction-lab/`, `backend/.../ingest` | Gemini·아이콘 매칭 |
| 리뷰어 | 읽기 전용 | 변경마다 버그·보안·학습 노트 누락 점검 |

- 같은 파일을 두 작업자가 동시에 고치지 않는다.
- 각 작업자는 자기 영역의 학습 노트를 직접 쓴다.
- 와이어프레임: https://claude.ai/artifact/RntyJ6inys4piZAUHx1rHq (사용자 계정에서만 열림. 핵심: 다크/라이트 토큰은 `frontend/src/styles/theme.css`, 세트 한 줄+펼치기, 등록(진영·포지션→Ctrl+V)→확인(라인 순서 정렬) 2단계)

## 7. 알려진 이슈
- 백엔드 시작 로그에 `Using generated security password`가 찍힌다(Boot 기본 사용자). M3에서 담당자 계정 구현 시 제거.
- JDBC URL에 시간대 파라미터 미설정. M3에서 엔티티가 생기면 확인.
