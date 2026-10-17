# LOL-Inhouse 프로젝트 규칙

친구 내전(롤 5:5 커스텀) 결과창 스크린샷을 AI로 읽어 전적·랭킹·밴 순서를 기록하는 웹 서비스.

## 기준 문서 (충돌 시 위가 우선)
1. `docs/유저플로우.md` — 최신 결정이 가장 많이 반영됨
2. `docs/LOLInhouse_기능명세서_2026-10-15.md`, `docs/LOLInhouse_PRD_2026-10-15.md`
3. `docs/implementation-plan.md` — 스택·마일스톤·작업 방식
4. `docs/handoff.md` — 초기 배경(데이터 모델 초안 §7은 폐기, implementation-plan의 모델을 따름)

## 구조
- `backend/` Java 21, Spring Boot 4.1.1(ADR 001), Gradle(Groovy). 패키지 루트 `com.lolinhouse`
- `frontend/` React + TypeScript + Vite
- `infra/` docker compose, 배포·백업 스크립트
- `tools/extraction-lab/` AI 추출 실험(M0)
- `docs/study/` 학습 노트(**로컬 전용, git 제외**), `docs/adr/` 설계 결정 기록

## 네이밍
- 변수·메서드 camelCase, 클래스 PascalCase, 상수 SCREAMING_SNAKE_CASE
- DB 테이블·컬럼 snake_case, REST URI·CSS 클래스 kebab-case

## 학습 기록 (필수)
사용자는 이 코드를 나중에 직접 공부해 설명해야 한다. 작업 단위가 끝날 때마다 `docs/study/NN-주제.md`를 작성한다.
`docs/study/`는 `.gitignore`로 저장소에서 제외한다(포트폴리오 저장소에 학습용 메모를 넣지 않음, 2026-10-17 사용자 결정). 커밋에 넣지 않는다.
형식: 무엇을 / 왜 이 구조인가(대안 포함) / 흐름("어디서→어디로, 어떤 데이터가, 누구에게, 그다음 무엇이") / 파일·클래스·메서드 지도 / 변수·필드·설정값 사전 / 개념 설명 / 함정·디버깅 / 점검 질문.
코드 주석은 클래스·공개 메서드에 "역할", 규칙이 숨은 줄에만 한 줄. 뻔한 줄에는 달지 않는다. 주석·커밋 대상 문서에 `docs/study` 경로를 적지 않는다(저장소에 없는 파일). ADR·유저플로우 참조는 괜찮다.
되돌리기 어려운 결정은 `docs/adr/NNN-제목.md`(배경·결정·대안·결과).

## 원칙
- 요청 범위만 구현. 근거 없는 기술(Redis·큐·QueryDSL 등)은 넣지 않는다.
- 도메인 규칙(포지션 배정, 밴 순서, 세트 지문, 1팀/2팀 판별, 닉네임 매칭, 통계)은 테스트 먼저.
- 비밀값은 `.env`(git 제외)와 환경변수로만. 코드·문서·로그에 키를 남기지 않는다.
- 결과창 샘플 이미지는 친구 닉네임이 있어 `samples/`(git 제외)에만 둔다.
- 커밋·푸시는 사용자 승인 후.

## 자주 쓰는 명령
- 로컬 DB: `docker compose -f infra/docker-compose.yml --env-file .env up -d`
- 백엔드 테스트: `cd backend && ./gradlew test`
- 프런트 개발 서버: `cd frontend && npm run dev`
