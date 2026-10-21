# M5.5 설계: 데스크탑 운영(Docker 묶음·터널·실제 접속 주소·백업)

- 날짜: 2026-10-21, 브랜치 `m55-desktop-ops`
- 기준: `docs/implementation-plan.md` M5.5 행, AGENTS.md
- 원칙: **개발은 노트북, 실행은 데스크탑.** 데스크탑에는 Git·Docker Desktop·터널 프로그램만 설치한다. Java·Node·Gradle은 설치하지 않는다(이미지 안에서 빌드).

## 0. 결정 요약

| # | 결정 | 이유 |
|---|---|---|
| D1 | 운영용 compose 파일 `infra/compose.desktop.yml`을 따로 둔다. 개발용 `infra/docker-compose.yml`(MySQL만)은 그대로 | 개발 흐름(Vite + bootRun)을 깨지 않는다 |
| D2 | 서비스 3개: `mysql`(포트 공개 없음), `backend`(포트 공개 없음), `web`(nginx: 프런트 정적 파일 + `/api` 전달, **`127.0.0.1:${WEB_PORT:-8088}`만** 공개) | DB·백엔드는 PC 밖은 물론 PC 안에서도 직접 접근 불가. 터널은 같은 PC의 `localhost:8088`로만 붙는다 |
| D3 | 이미지는 데스크탑에서 `docker compose build`로 만든다(멀티 스테이지: Gradle·JDK 21 → JRE 21, Node → nginx). 레지스트리는 쓰지 않는다 | 계정·비용 없음. `git pull` 후 다시 빌드하면 업데이트 |
| D4 | 데이터: MySQL은 이름 있는 볼륨, 결과창 이미지·Data Dragon 캐시는 저장소 루트 `data/`(git 제외)를 바인드 마운트 | 백업 스크립트가 `data/images`를 바로 압축할 수 있다 |
| D5 | **실제 접속 주소**: nginx `real_ip` 모듈로 `X-Forwarded-For`를 오른쪽부터 읽고(`real_ip_recursive on`), 사설·루프백 대역만 신뢰. nginx는 백엔드로 보낼 때 `X-Forwarded-For`를 **자기 판단값 하나로 덮어쓴다**. 백엔드는 `server.forward-headers-strategy=native`(환경변수로 켬, 개발 기본은 끔). Tomcat은 내부 프록시(사설 대역)인 nginx의 헤더만 믿는다 | 클라이언트가 보낸 가짜 `X-Forwarded-For`는 터널이 오른쪽에 붙인 실제 주소보다 왼쪽이라 무시된다. 백엔드는 포트가 없어 nginx를 거치지 않고 헤더를 넣을 수 없다. 터널 종류(Cloudflare·Tailscale 등)와 무관하게 같은 규칙 |
| D6 | `X-Forwarded-Proto`: 터널이 보낸 값을 nginx가 그대로 전달(없으면 `$scheme`). `COOKIE_SECURE=true`는 데스크탑 `.env`에서 켠다 | 세션·CSRF 쿠키 Secure. HTTPS는 터널에서 끝난다 |
| D7 | 로그인 실패 로그의 주소도 같은 `getRemoteAddr()`라 자동으로 실제 주소가 된다 | 코드 변경 없음 |
| D8 | 백업: `infra/backup.ps1`(컨테이너 안 `mysqldump --single-transaction` → `backups/YYYYMMDD-HHmm/db.sql` + `data/images` 압축, 14일 보관), `infra/restore.ps1`(확인 질문 후 복원), `infra/register-backup-task.ps1`(Windows 작업 스케줄러 매일 04:00). 비밀번호는 호스트 명령줄에 쓰지 않고 컨테이너 환경변수로 읽는다. `backups/`는 git 제외 | 친구 실명·닉네임이 들어 있다 |
| D9 | 터널 프로그램은 compose 밖(데스크탑에 설치)에 둔다. 종류는 사용자가 정한다(§3) | 터널마다 로그인·설정 방식이 달라 이미지에 넣으면 비밀값이 늘어난다 |
| D10 | 설치 안내서 `docs/ops/desktop-setup.md`(체크리스트: 설치 → `.env` 작성 → 실행 → 접속 확인 → 터널 → 백업 등록 → 복원 시험 → 업데이트 방법) | 사용자가 혼자 따라 할 수 있어야 한다 |

## 1. 환경변수 (데스크탑 `.env`에 추가·변경)
- `COOKIE_SECURE=true`
- `WEB_PORT=8088`(선택, 터널이 붙는 로컬 포트)
- `DB_URL`, `IMAGE_STORE_DIR`, `DDRAGON_CACHE_DIR`, `FORWARD_HEADERS_STRATEGY`는 compose가 컨테이너 안 값으로 덮어쓴다(사용자가 쓰지 않는다).
- 나머지(`MYSQL_*`, `ADMIN_*`, `GEMINI_*`)는 기존 `.env.example` 그대로. 관리자 비밀번호는 강하게.

## 2. 테스트·검증
- 백엔드: `forward-headers-strategy=native`에서 신뢰 프록시가 보낸 `X-Forwarded-For`가 `getRemoteAddr()`가 되는지, 신뢰하지 않는 주소가 보낸 헤더는 무시되는지(실제 포트 통합 테스트). 기본값(끔)에서는 헤더를 무시하는지.
- nginx: 설정 문법(`nginx -t`), SPA 새로고침(`/records` 직접 접속 → index.html), `/api` 전달, 가짜 `X-Forwarded-For`를 넣은 요청이 무시되는지(노트북에서 compose로 실제 확인).
- 노트북에서 `docker compose -f infra/compose.desktop.yml --env-file .env up -d --build` 종단 확인: health, 로그인, 공개 화면, 백업·복원 1회.

## 3. 사용자 결정: 터널(접속 주소)
| 선택지 | 주소 | 비용 | 메모 |
|---|---|---|---|
| Cloudflare Tunnel(이름 있는 터널) + 도메인 구매 | 내 도메인 | 도메인 연 1~2만 원 정도 | 가장 안정적. AWS로 옮겨도 같은 주소 |
| Tailscale Funnel | `기기이름.tailnet.ts.net` 고정 | 무료(개인 플랜) | 도메인 없이 고정 주소. 대역폭 상한 비공개 |
| Cloudflare Quick Tunnel | 실행할 때마다 바뀌는 `trycloudflare.com` | 무료 | 시험용만 |
- **결정(2026-10-21 사용자)**: Tailscale Funnel로 시작. 추천 근거: 도메인 없이 시작하려면 Tailscale Funnel, 계속 쓸 거면 도메인 + Cloudflare. 어느 쪽이든 D5 덕분에 앱 설정은 같다(터널을 `localhost:8088`에 연결).
- [확인 필요] 각 터널이 보내는 `X-Forwarded-For`·`X-Forwarded-Proto`는 터널을 정한 뒤 실제 요청으로 확인한다.
