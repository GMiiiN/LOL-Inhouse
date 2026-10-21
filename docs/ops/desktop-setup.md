# 데스크탑 운영 설치 안내서

개발은 노트북, 실행은 데스크탑입니다. 데스크탑에는 **Git, Docker Desktop, Tailscale(터널)**만 설치합니다(Java·Node 설치 불필요). 설계 배경: `docs/design/m55-design.md`

> **경고**: 결과창 이미지(`data\images`)와 백업 폴더(`backups\`)에는 친구들의 닉네임·실명이 들어 있습니다. 메신저·클라우드 공유 폴더·공개 저장소에 올리거나 남에게 보내지 마세요. `.env`(비밀번호·API 키)도 마찬가지입니다.

모든 명령은 PowerShell에서 실행합니다. 저장소 폴더로 이동한 상태(`cd` 후)를 기준으로 합니다.

## 1. 설치

- [ ] Git for Windows 설치 (https://git-scm.com/download/win, 기본 옵션)
- [ ] Docker Desktop 설치 (https://www.docker.com/products/docker-desktop/). 설치 중 **WSL 2** 사용 옵션을 켜 두고, 설치 후 재부팅
- [ ] Docker Desktop 실행 → 왼쪽 아래가 초록색("Engine running")이 될 때까지 기다리기
- [ ] Docker Desktop 설정 > General > **Start Docker Desktop when you sign in to your computer** 체크(PC를 켜면 자동 실행)

확인:

```powershell
git --version
docker --version
docker compose version
```

## 2. 저장소 받기

```powershell
cd $HOME\Desktop
git clone https://github.com/GMiiiN/LOL-Inhouse.git lol-inhouse
cd lol-inhouse
```

- 확인 전 버전을 받는 경우(main에 합치기 전): `git checkout m55-desktop-ops`

## 3. `.env` 작성

`.env`는 비밀번호·키가 들어 있어 저장소에 없습니다. 견본을 복사해서 채웁니다.

```powershell
Copy-Item .env.example .env
notepad .env
```

| 값 | 넣을 내용 |
|---|---|
| `GEMINI_API_KEY` | Gemini API 키(노트북 `.env`에 있는 것) |
| `GEMINI_MODEL` | 견본 그대로 |
| `MYSQL_DATABASE` | 견본 그대로(`lol_inhouse`) |
| `MYSQL_USER` | 견본 그대로(`lol_app`) |
| `MYSQL_PASSWORD` | **강한 비밀번호**(길게, 노트북과 다르게). 처음 DB를 만들 때만 적용됨 |
| `MYSQL_ROOT_PASSWORD` | 또 다른 **강한 비밀번호**. 백업이 이 값을 씀 |
| `ADMIN_USERNAME` / `ADMIN_PASSWORD` | 담당자 로그인 계정. **인터넷에 공개되므로 강하게**(12자 이상) |
| `COOKIE_SECURE` | **`true`** (터널의 HTTPS로 접속하므로. 8절 문제 해결 참고) |
| `WEB_PORT` | 필요할 때만 주석을 풀어 다른 포트로 변경(기본 8088). 줄만 두고 값을 비우면 안 됨 |

- `DB_URL`, `MYSQL_HOST_PORT`는 데스크탑에서 쓰이지 않으니 신경 쓰지 않아도 됩니다(compose가 컨테이너 안 주소로 덮어씀).
- **데스크탑 DB는 새로 시작합니다.** 노트북 DB 내용은 옮기지 않습니다(선수 정보 등은 화면에서 다시 입력하거나 가져오기 도구 사용).
- `MYSQL_PASSWORD`·`MYSQL_ROOT_PASSWORD`는 DB가 처음 만들어질 때 한 번만 반영됩니다. 나중에 `.env`만 바꾸면 DB 비밀번호가 안 바뀌어 접속 오류가 납니다(바꾸려면 9절의 "처음부터 다시").

## 4. 실행

```powershell
docker compose -f infra/compose.desktop.yml --env-file .env up -d --build
```

처음에는 이미지 빌드로 5~10분 걸립니다. 끝나면:

```powershell
docker compose -f infra/compose.desktop.yml --env-file .env ps
```

세 서비스(mysql, backend, web)가 모두 `running`/`healthy`면 성공입니다.

## 5. 접속 확인

- [ ] 브라우저에서 http://localhost:8088 접속 → 홈 화면이 보임
- [ ] 내전 기록 화면에서 새로고침(F5)해도 화면이 유지됨
- [ ] 담당자 로그인은 **터널 주소(https)** 로 접속해서 확인합니다. `COOKIE_SECURE=true`이면 http://localhost:8088 에서는 로그인이 안 됩니다(정상). 로컬에서 로그인까지 시험하려면 잠깐 `COOKIE_SECURE=false`로 바꾸고 `up -d`를 다시 실행했다가, 끝나면 `true`로 되돌립니다.

## 6. 터널 연결: Tailscale Funnel (인터넷에서 접속하게 하기)

터널은 **Tailscale Funnel**을 씁니다(도메인 불필요, 무료, 주소 고정). 터널의 대상은 **`http://localhost:8088`** 입니다. 나중에 도메인을 사서 Cloudflare Tunnel로 바꿔도 대상만 같으면 앱 설정은 그대로입니다.

1. [ ] Tailscale 실행 → 로그인(본인 계정, 직접)
2. [ ] 브라우저로 Tailscale 관리 페이지(https://login.tailscale.com/admin/dns) → **MagicDNS** 켜기, **HTTPS Certificates** 켜기
3. [ ] 4절의 앱이 켜져 있는 상태에서 PowerShell:

```powershell
tailscale funnel --bg 8088
```

- 처음이면 "Funnel을 켜도 될지" 허용 링크가 출력됩니다. 링크를 열어 허용한 뒤 같은 명령을 다시 실행합니다.
- `--bg`를 붙이면 끌 때까지 유지되고, PC를 다시 켜도 자동으로 다시 열립니다.
- 위 명령이 안 되면 `tailscale funnel --bg http://127.0.0.1:8088` 로 시도합니다.

```powershell
# 공개 주소 확인 (https://기기이름.….ts.net 형태)
tailscale funnel status

# 공개 중지
tailscale funnel --https=443 off
```

연결 후 확인:

- [ ] 폰(와이파이 끄고 LTE)으로 `tailscale funnel status`에 나온 https 주소 접속 → 홈 화면
- [ ] 같은 주소에서 담당자 로그인 성공
- [ ] Tailscale 설정에서 **Run at startup(PC 시작 시 실행)** 켜기
- [ ] **실제 접속 주소 확인**: 폰으로 접속한 직후 아래 명령의 마지막 줄 맨 앞 주소가 **통신사 공인 IP**(127.0.0.1이나 172.…·192.168.… 가 아님)인지 확인. 아니면 친구들이 한 사람으로 묶여 팀 짜기 호출 제한에 같이 걸리니 개발자에게 알려 주세요.

```powershell
docker compose -f infra/compose.desktop.yml --env-file .env logs --tail 5 web
```

## 7. 백업

```powershell
# 수동 백업 1회 (backups\날짜-시각\ 에 db.sql, images.zip 생성)
powershell -NoProfile -ExecutionPolicy Bypass -File infra\backup.ps1

# 매일 04:00 자동 백업 등록 (현재 로그인한 사용자 기준, 1회만)
powershell -NoProfile -ExecutionPolicy Bypass -File infra\register-backup-task.ps1
```

- [ ] 수동 백업 후 `backups\` 안에 폴더와 파일이 생겼는지 확인
- [ ] 작업 스케줄러(시작 메뉴에서 "작업 스케줄러")에 `LOL-Inhouse-Backup`이 있는지 확인
- 14일이 지난 백업은 자동 삭제됩니다. 04:00에 PC가 꺼져 있으면 켜진 뒤 실행되고, **로그인한 상태여야** 실행됩니다.
- 백업은 같은 PC 안에 있어서 디스크가 고장 나면 같이 사라집니다. 가끔 `backups\` 최신 폴더를 **본인의 외장 디스크**에 복사해 두세요(남에게 공유 금지).

**복원 시험(처음 한 번은 꼭 해 보세요)**:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File infra\restore.ps1 backups\<폴더이름>
```

"정말 덮어쓸까요? (yes 입력)"에 `yes`를 입력하면 현재 DB와 이미지가 백업 내용으로 바뀝니다. 기존 `data\images`는 `data\images.before-restore-날짜`로 옮겨 두니, 문제없으면 직접 지우세요.

복원이 중간에 실패하면 스크립트가 무엇이 복원됐는지 안내하고 **앱(web, backend)을 멈춘 채로 둡니다**(반쯤 복원된 데이터에 새 기록이 쌓이는 것을 막기 위해). 안내대로 원인을 해결한 뒤 같은 백업으로 다시 실행하세요. 백업도 마찬가지로 어느 단계든 실패하면 "백업 실패"를 출력하고 종료 코드 1로 끝나며, 그 경우 오래된 백업은 지우지 않습니다.

## 8. 업데이트

노트북에서 개발·머지한 새 버전을 받을 때:

```powershell
cd $HOME\Desktop\lol-inhouse
git pull
docker compose -f infra/compose.desktop.yml --env-file .env up -d --build
```

DB 구조 변경은 앱이 시작할 때 자동 적용됩니다. 업데이트 전에 수동 백업을 한 번 해 두면 안전합니다.

## 9. 끄기·로그·문제 해결

```powershell
# 끄기(데이터는 유지)
docker compose -f infra/compose.desktop.yml --env-file .env down

# 다시 켜기
docker compose -f infra/compose.desktop.yml --env-file .env up -d

# 로그 보기(마지막 100줄, 실시간은 -f 추가)
docker compose -f infra/compose.desktop.yml --env-file .env logs --tail 100 backend
```

| 증상 | 확인할 것 |
|---|---|
| `port is already allocated` / 8088 사용 중 | 다른 프로그램이 8088을 씀. `.env`에서 `# WEB_PORT=8088` 주석을 풀고 다른 번호(예: 8089)로 바꾼 뒤 `up -d`. 터널 대상 포트도 같이 변경 |
| `error during connect` / `docker daemon is not running` | Docker Desktop이 꺼져 있음. 실행하고 Engine running을 기다린 뒤 다시 |
| backend가 계속 재시작·unhealthy | `logs backend`로 오류 확인. 대부분 `.env` 값 누락(`ADMIN_USERNAME`/`ADMIN_PASSWORD` 비어 있음) 또는 DB 비밀번호 불일치 |
| 로그인이 안 됨 | `COOKIE_SECURE=true`인데 `http://`(localhost 등)로 접속한 경우가 가장 흔함. **https 터널 주소**로 접속. 로컬 시험은 잠깐 `false`로 |
| 터널 주소에서 로그인은 되는데 곧 풀림 | 세션이 2시간(마지막 요청 기준) 지나면 만료되는 정상 동작 |
| DB 비밀번호를 바꾸고 싶음 / DB를 처음부터 다시 | **데이터가 모두 지워집니다.** 먼저 백업 후 `docker compose -f infra/compose.desktop.yml --env-file .env down -v` → `.env` 수정 → `up -d --build` |
| 이미지 업로드가 413 | 이미지가 10MB를 넘음. 용량을 줄여서 다시 |

> 실수로 `down -v`를 실행하면 DB 볼륨이 삭제됩니다. 일반 종료에는 `-v`를 붙이지 마세요.
