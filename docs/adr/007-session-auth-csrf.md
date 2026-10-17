# ADR 007. 담당자 인증은 서버 세션 + CSRF 쿠키/헤더, 로그인은 formLogin + JSON 응답

- 상태: 확정 (M3, 2026-10-16)
- 관련: `docs/design/m3-design.md` §4·§9(D6~D8), 학습 노트 [09](../study/09-security-auth.md), 코드 `common/config/SecurityConfig`, `auth/*`

## 배경

담당자(운영자)가 스크린샷을 올리고 선수·내전을 관리한다. 사용자는 사실상 1명이고, 프런트(React SPA)와 API는 같은 출처에서 서비스한다(운영: CloudFront, 개발: Vite 프록시). 로그인 방식, CSRF 방어, 계정 저장 방식을 정해야 한다.

## 결정

1. **서버 세션**(`JSESSIONID`, HttpOnly, SameSite=Lax, 2시간). JWT를 쓰지 않는다.
2. **`formLogin` + JSON 응답 핸들러**. `POST /api/admin/login`(form-urlencoded), 성공 200 `{username}`, 실패 401 `LOGIN_FAILED`(사용자 없음·비밀번호 틀림 동일). 로그인 페이지는 `/admin/login`(프런트 화면)으로 지정해 기본 HTML 페이지를 끈다.
3. **CSRF는 켜 둔 채** `csrf.spa()`(Security 7) + `XSRF-TOKEN` 쿠키(JS 읽기 가능, SameSite=Lax, Secure=`COOKIE_SECURE`) + `X-XSRF-TOKEN` 헤더. `GET /api/csrf`가 쿠키를 발급한다. CSRF 검사는 인증보다 먼저라서 익명 + 토큰 없음은 403 `CSRF_INVALID`.
4. **인가는 기본 거부**: health/info, `/api/csrf`, 로그인만 공개, `/api/admin/**`는 ADMIN, 나머지 denyAll(공개 조회는 M5에서 연다). 모든 인증·인가 오류는 `application/problem+json`, 리다이렉트 없음.
5. **계정은 `admin_account` 테이블**. 비었을 때만 `app.admin.*`(env)로 BCrypt 계정 한 개를 만든다. 비어 있거나 비밀번호가 UTF-8 72바이트를 넘으면 기동 실패. 길이 하한은 두지 않는다(사용자 결정).

## 대안

| 대안 | 버린 이유 |
|---|---|
| JWT (localStorage 또는 쿠키) | 확장할 서버 군이 없고, 즉시 로그아웃·탈취 대응·XSS 노출을 직접 풀어야 함 |
| 로그인을 직접 만든 컨트롤러로 | 세션 고정 방지·CSRF 토큰 교체·SecurityContext 저장을 직접 구현해야 하고 하나만 빠져도 취약 |
| CSRF 끄기 (SameSite만 의존) | 방어선이 브라우저 한 겹뿐. 비용이 작아 켜 둠 |
| HTTP Basic | 매 요청에 비밀번호 전송, 로그아웃 없음 |
| env 계정을 매번 사용 (DB 없음) | 평문 비밀번호가 운영 env에 계속 남음 |
| 비밀번호 최소 12자 (설계 초안) | 친구끼리 쓰는 사이트라는 사용자 결정으로 하한 폐기. 72바이트 상한만 유지 |

## 결과

- 장점: 보안 핵심(세션 고정, CSRF 교체, 인증 저장)을 프레임워크가 처리, 코드가 작다.
- 감수: 서버 재시작 시 세션이 사라져 재로그인. 서버를 여러 대로 늘리면 세션 공유(또는 JWT 전환)가 필요 — 그때 다시 결정한다. 로그인 속도 제한은 M7에서 결정(M3는 BCrypt 비용 + 동일 응답 + IP만 남기는 WARN 로그).
- 프런트 계약: 시작 시와 로그아웃 뒤 `/api/csrf` 호출, 변경 요청마다 쿠키 → `X-XSRF-TOKEN`, 403 `CSRF_INVALID`면 재발급 후 한 번 재시도, 로그인 직후 쿠키가 교체되므로 토큰은 매번 쿠키에서 읽는다.
- 운영 체크: M7에서 `COOKIE_SECURE=true`.
