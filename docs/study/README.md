# 학습 노트 목차

구현하면서 남긴 설명이다. 위에서부터 순서대로 읽으면 프로젝트가 쌓인 순서를 그대로 따라갈 수 있다.
설계 결정의 짧은 기록은 [../adr/](../adr/)에 있다.

| # | 노트 | 마일스톤 | 핵심 주제 |
|---|---|---|---|
| 01 | [저장소 골격](01-repository-skeleton.md) | M1 | 모노레포, .env 비밀값, Docker Compose MySQL, 줄바꿈 |
| 02 | [백엔드 골격](02-backend-skeleton.md) | M1 | Spring Boot 4 시작 흐름, 설정 외부화, Actuator, Testcontainers |
| 03 | [프런트 골격](03-frontend-skeleton.md) | M1 | Vite, React Router, TanStack Query, CSS 변수 테마, 개발 프록시 |
| 04 | [CI](04-ci.md) | M1 | GitHub Actions, 러너, 캐시, .env 없는 환경 |
| 05 | [AI 결과창 추출 실험](05-extraction-lab.md) | M0 | 구조화 JSON, 정규화 박스, 아이콘 매칭, 정확도·백오프, 검증 제한 |
| 06 | [세트 규칙 (포지션·밴·지문·1팀/2팀·피어리스·저장 검증)](06-domain-set-rules.md) | M2 | 순수 도메인, record 값 객체, 팀 순서 무관 지문, 오류 vs 경고, TDD 사이클 |
| 07 | [선수 닉네임 매칭 · 통계](07-domain-player-stats.md) | M2 | NFKC 정규화, 편집 거리, 후보/자동 연결, 누적 KDA·PERFECT, 승률, 공동 순위 랭킹 |

## ADR

| # | 결정 |
|---|---|
| 001 | [Spring Boot 4.1.1 사용](../adr/001-spring-boot-4.md) |
| 002 | [비밀값은 .env, 샘플은 저장소 밖](../adr/002-secrets-and-samples.md) |
| 003 | [구조화 추출과 공식 아이콘 매칭 분리](../adr/003-extraction-approach.md) |
| 004 | [세트 지문은 팀 순서와 무관한 정규 문자열](../adr/004-set-fingerprint.md) |
