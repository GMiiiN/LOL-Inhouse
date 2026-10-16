# 01. 저장소 골격: 폴더 구조, 비밀값(.env), 로컬 DB

> 마일스톤 M1. 백엔드·프런트 골격은 [02](02-backend-skeleton.md), [03](03-frontend-skeleton.md)에서 다룬다.

## 1. 무엇을 만들었나

GitHub 저장소 `GMiiiN/LOL-Inhouse`를 로컬 폴더에 연결하고, 앞으로 모든 코드가 들어갈 **모노레포 골격**과 공통 설정을 만들었다.
비밀값을 담는 `.env`, 그 견본 `.env.example`, git 제외 규칙 `.gitignore`, 줄바꿈 규칙 `.gitattributes`, 로컬 MySQL을 띄우는 `infra/docker-compose.yml`, 프로젝트 규칙 `CLAUDE.md`.

## 2. 왜 이 구조인가

### 모노레포 (backend + frontend + infra를 한 저장소에)
- **선택**: 한 저장소에 `backend/`, `frontend/`, `infra/`, `tools/`, `docs/`.
- **이유**: 혼자 개발하고, 화면과 API를 같이 바꾸는 일이 많다. 한 저장소면 PR 하나에 "API 변경 + 화면 변경 + 문서"가 같이 들어가 리뷰·되돌리기가 쉽다. CI도 한 곳에서 관리한다.
- **버린 대안**: 저장소 2개(백엔드/프런트 분리). 팀이 나뉘어 있거나 배포 주기가 다를 때 유리하지만, 1인 프로젝트에선 버전 맞추기 비용만 늘어난다.

### 비밀값은 `.env` 한 곳에
- **선택**: 루트 `.env` 파일 하나에 API 키·DB 비밀번호·담당자 계정을 모으고 git에서 제외한다. 견본 `.env.example`만 커밋한다.
- **이유**: 키가 코드나 커밋 기록에 한 번이라도 들어가면 지워도 기록에 남는다(공개 저장소면 사실상 유출). `.env.example`이 있으면 "어떤 값이 필요한지"는 공유하면서 실제 값은 숨길 수 있다.
- **같은 파일을 여러 곳이 읽는다**: docker compose(`--env-file .env`), Spring(`spring.config.import`로 읽음, 02 노트), 실험 도구. 값이 한 곳에만 있어야 "DB 비밀번호를 바꿨는데 한쪽만 바뀌는" 실수가 없다.
- **운영(AWS)에서는**: `.env` 파일 대신 서버 환경변수/시크릿 저장소를 쓴다(M7). Spring은 환경변수도 같은 이름으로 읽으므로 코드는 그대로다.

### 결과창 샘플은 `samples/`(git 제외)
- 스크린샷에 친구들의 게임 닉네임이 있다. 저장소가 공개일 수 있으므로 커밋하지 않는다. 실험 도구는 이 폴더를 읽는다.
- `samples/ground-truth.json`: 샘플 3장의 정답지(닉네임·실제 포지션·K/D/A·CS·골드). AI 추출 정확도를 숫자로 재는 기준이다(M0).

### 로컬 DB는 Docker Compose
- **이유**: PC에 MySQL을 직접 설치하면 버전·설정이 사람마다 다르고 지우기도 번거롭다. 컨테이너면 `up`/`down` 한 줄이고, 운영(EC2)에서도 같은 방식(M7)을 쓴다.
- `mysql:8.4`: 8.x 장기지원(LTS) 계열.
- `utf8mb4`: 한글·일본어·한자 닉네임과 이모지까지 저장하려면 utf8mb4가 필요하다(MySQL의 `utf8`은 3바이트까지만 저장해 일부 문자가 깨진다).
- `TZ: Asia/Seoul`: 내전 시작일 묶음이 한국 날짜 기준이라 DB 시간대를 맞춘다.
- `volumes: mysql-data`: 컨테이너를 지워도 데이터가 남도록 이름 붙은 볼륨에 저장한다.
- `healthcheck`: DB가 "켜졌다"와 "접속 받을 준비가 됐다"는 다르다. 나중에 앱 컨테이너가 DB가 준비된 뒤에 뜨도록(depends_on: condition) 쓰려고 미리 넣었다.
  - **정정(Codex 리뷰)**: 처음엔 `mysqladmin ping`을 썼는데, 이 명령은 비밀번호가 틀려도 "살아 있음"으로 성공하고, 컨테이너 첫 기동 때 잠깐 뜨는 **초기화용 임시 서버**에도 응답한다. 그래서 "healthy"인데 앱 계정·DB가 아직 준비 안 된 순간이 생길 수 있다. 지금은 **앱 계정으로 TCP(127.0.0.1) 접속해 대상 DB에 `SELECT 1`**이 성공해야 healthy가 된다. 앱이 실제로 하는 일과 같은 것을 검사하는 셈이다.
  - compose 파일의 `$$MYSQL_USER`처럼 `$`를 두 번 쓴 것은 compose가 파일을 읽을 때 변수를 미리 치환하지 않고, **컨테이너 안의 셸**이 자기 환경변수로 치환하게 하려는 이스케이프다.
- `ports: "127.0.0.1:3307:3306"`: 앞에 `127.0.0.1`을 붙이면 이 PC 자신만 접속할 수 있다. 붙이지 않으면 모든 네트워크 인터페이스(0.0.0.0)에 열려서, 방화벽이 허용하는 경우 같은 와이파이의 다른 기기가 개발 DB에 접속을 시도할 수 있다(Codex 리뷰로 수정).

### `.gitattributes`로 줄바꿈 LF 통일
- Windows는 줄바꿈이 CRLF, Linux(CI·서버)는 LF다. 셸 스크립트·`gradlew`가 CRLF로 커밋되면 Linux에서 `/bin/sh^M: bad interpreter` 같은 오류가 난다. 저장소에는 LF로 저장하도록 강제한다.

## 3. 흐름

### 로컬 DB를 띄울 때
1. 개발자가 저장소 루트에서 `docker compose -f infra/docker-compose.yml --env-file .env up -d` 실행
2. compose가 `.env`를 읽어 `${MYSQL_DATABASE}` 같은 자리에 값을 채움
3. Docker가 `mysql:8.4` 이미지를 받아 컨테이너 생성 → 최초 1회 DB `lol_inhouse`와 계정 `lol_app` 생성
4. 호스트 3307 포트(`MYSQL_HOST_PORT`) → 컨테이너 3306으로 연결
5. 백엔드(Spring)가 `.env`의 `DB_URL`(`jdbc:mysql://localhost:3307/lol_inhouse`)로 접속

### 비밀값이 쓰이는 곳
`.env` → (compose) MySQL 초기화 / (Spring) DataSource·Gemini 클라이언트·담당자 계정 생성 / (실험 도구) Gemini 호출

## 4. 파일 지도

| 파일 | 역할 | 커밋 여부 |
|---|---|---|
| `.env` | 실제 비밀값 | 제외 |
| `.env.example` | 필요한 값 목록과 설명 | 커밋 |
| `.gitignore` | git 제외 규칙(.env, samples, 빌드 산출물, node_modules) | 커밋 |
| `.gitattributes` | 줄바꿈 LF 통일, 이미지 바이너리 지정 | 커밋 |
| `CLAUDE.md` | 프로젝트 규칙(기준 문서 순서, 네이밍, 학습 기록 의무) | 커밋 |
| `infra/docker-compose.yml` | 로컬 MySQL 컨테이너 정의 | 커밋 |
| `samples/` | 결과창 샘플과 정답지 | 제외 |
| `docs/study/` | 학습 노트(이 파일) | 커밋 |
| `docs/adr/` | 설계 결정 기록 | 커밋 |

## 5. 변수 사전 (`.env`)

| 키 | 내용 | 읽는 곳 |
|---|---|---|
| `GEMINI_API_KEY` | Gemini API 키(사용자가 직접 입력) | 백엔드 추출 모듈, 실험 도구 |
| `GEMINI_MODEL` | 사용할 Gemini 모델 이름. 실험 결과에 따라 코드 수정 없이 바꾸려고 설정으로 뺐다 | 같은 곳 |
| `MYSQL_DATABASE` | 만들 DB 이름 `lol_inhouse` | compose |
| `MYSQL_USER` / `MYSQL_PASSWORD` | 앱이 쓰는 DB 계정(로컬 전용 임의값) | compose, Spring DataSource |
| `MYSQL_ROOT_PASSWORD` | DB 관리자 계정. 앱은 쓰지 않는다(최소 권한) | compose |
| `MYSQL_HOST_PORT` | PC 쪽에서 열 포트(3307). 컨테이너 안은 항상 3306 | compose |
| `DB_URL` | 앱이 접속할 JDBC 주소. 포트가 `MYSQL_HOST_PORT`와 같아야 한다 | Spring DataSource |
| `ADMIN_USERNAME` / `ADMIN_PASSWORD` | 담당자 로그인 계정. 앱이 처음 뜰 때 계정이 없으면 이 값으로 만든다(M3) | Spring |

앱이 root 대신 별도 계정(`lol_app`)을 쓰는 이유: 앱 코드에 문제가 생겨도 다른 DB나 사용자 관리까지 건드릴 수 없게 권한을 좁히는 것(최소 권한 원칙).

## 6. 개념

- **환경변수와 12-Factor 설정**: 설정(특히 비밀값)을 코드가 아니라 실행 환경에서 주입하면, 같은 코드가 로컬·테스트·운영에서 값만 바꿔 돈다.
- **Docker 볼륨**: 컨테이너 안의 파일은 컨테이너를 지우면 사라진다. 볼륨은 컨테이너 밖에 따로 보관되는 저장 공간이다.
- **모노레포**: 여러 프로젝트를 한 저장소에서 관리하는 방식. 폴더별로 빌드 도구는 따로 쓴다(Gradle / npm).

## 7. 함정·디버깅

- Docker Desktop이 꺼져 있으면 `docker compose`와 Testcontainers 테스트가 `failed to connect to the docker API ... dockerDesktopLinuxEngine` 오류로 실패한다. → Docker Desktop을 켜고 다시 실행.
- `.env` 값에 따옴표·공백을 넣으면 compose와 Spring이 다르게 해석할 수 있다. `키=값` 형태로 따옴표 없이 쓴다.
- **실제로 겪음**: 처음엔 3306으로 열었는데 `ports are not available ... bind: Only one usage of each socket address` 오류. 원인은 PC에 설치된 MySQL 서비스(`mysqld.exe`)가 이미 3306을 쓰고 있었던 것(`netstat -ano | grep :3306`으로 PID 확인). 조치: 기존 MySQL은 그대로 두고 compose 포트를 `${MYSQL_HOST_PORT:-3307}:3306`으로 바꾸고 `DB_URL`도 3307로 맞췄다. 결과: 두 MySQL이 충돌 없이 공존.
- `docker compose ps`처럼 `--env-file`을 안 붙인 명령은 "variable is not set" 경고를 낸다. 값이 필요 없는 명령이라 동작에는 영향이 없다.

## 8. 점검 질문

1. `.env`를 커밋하면 안 되는 이유와, 실수로 커밋했을 때 해야 할 일은? → 기록에 남으므로 키를 **폐기·재발급**하는 것이 먼저다(기록 삭제만으로는 부족).
2. MySQL에서 `utf8` 대신 `utf8mb4`를 쓰는 이유는? → §2 참고.
3. 앱이 root 계정을 쓰지 않는 이유는? → §5 최소 권한.
4. healthcheck가 있는 것과 없는 것의 차이는? → §2 Docker Compose.
5. `.gitattributes`가 없으면 어떤 문제가 생길 수 있나? → §2 줄바꿈.
