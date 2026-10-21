# artifact-import

친구가 만든 티어표 아티팩트의 데이터(JSON)를 관리자 API(`/api/admin/player-profiles`, `/api/admin/score-table`)로 넣는다. Node 18+ 내장 `fetch`만 쓰고 외부 패키지는 없다. 설계: `docs/design/m45-design.md` §10 Q4.

## 입력 형식

```json
{ "players": [{ "name": "실명", "lanes": { "top|jg|mid|adc|sup": { "t": 1~6, "s": "상|중|하" } }, "nicks": ["닉네임"], "bee": true }],
  "score": { "base": {"1":60,...,"6":20}, "sub": {"상":3,"중":0,"하":-3}, "lane": {"top":0,"jg":0,"mid":0,"adc":-4,"sup":-3} } }
```

`bee: true`는 요주 인물(`flagged`)이다(생략하면 false). `example.json`이 가상 이름 예시다.

## 동작

- 실명이 같은 기존 선수는 갱신(라인 티어는 덮어쓰고 아티팩트에 없는 라인은 유지, 별칭은 기존 유지 + 새 닉네임 추가), 없으면 생성한다. 모두 `recordChange:false`라 ▲▼ 변동이 남지 않는다. `flagged`는 신규·갱신 모두 아티팩트 값으로 덮는다(`bee`가 없으면 false로 풀린다).
- 실명이 같은 선수가 없고, 닉네임이 겹치는 **미등록 선수**(세트 등록 때 닉네임으로 생긴 선수)가 정확히 한 명이면 그 선수에 실명·라인·닉네임을 붙여 등록한다(세트 기록 유지). 겹치는 미등록 선수가 둘 이상이면 `MULTIPLE_UNREGISTERED`로 건너뛴다(선수 관리 화면에서 직접 정리).
- 새로 만들 때 라인이 0개거나 닉네임이 0개인 선수는 건너뛴다.
- 닉네임 충돌(409)·검증 오류는 건너뛰고 끝에 코드별 건수와 순번을 보고한다. 이름은 `--verbose`일 때만 출력한다.
- 점수표는 `--with-score`를 줄 때만 덮어쓴다.

## 실행 절차 (데스크탑)

1. 서버와 DB를 켠다(`docs`의 로컬 실행 안내 참고).
2. 아티팩트 데이터 JSON을 **저장소 밖**에 둔다(실명이 있으므로 커밋 금지).
3. 먼저 확인: `node tools/artifact-import/import.js <데이터.json> --dry-run --with-score`
4. 비밀번호는 환경변수로만 넘긴다(인자·파일에 쓰지 않는다).
   - PowerShell: `$env:IMPORT_ADMIN_PASSWORD = Read-Host`  (입력 후 실행)
   - 실행: `node tools/artifact-import/import.js <데이터.json> --server http://localhost:8080 --user <관리자 아이디> --with-score`
5. 끝나면 `$env:IMPORT_ADMIN_PASSWORD = $null`.

옵션: `--server`(기본 `http://localhost:8080`), `--user`(기본 `admin`), `--with-score`, `--dry-run`, `--verbose`.

## 테스트

`cd tools/artifact-import && node --test`

## 경기 가져오기 (`import-matches.js`)

아티팩트에만 남은 경기(경기 시간·결과창 이미지가 없음)를 `POST /api/admin/duels/{id}/sets/import`로 넣는다. 경기 시간은 "모름"(null)으로 저장된다(ADR 014). 입력은 위와 같은 데이터 JSON의 `matches`(`date`, `winner`, `bans`, `players[{pid, team, lane, champ, k, d, a, cs, gold}]`)와 `players`(`id`, `name`, `nicks`)이다. `bans` 배열 순서가 그 진영의 밴 순서(1~5)다.

동작: 로그인 → 선수 연결(아티팩트 선수 → 서버 선수: 실명이 같으면 그 선수, 없으면 닉네임이 정확히 한 서버 선수의 별칭과 겹칠 때만; 못 찾으면 중단) → `--date` 경기를 아티팩트 순서대로 변환·검증(선수 10명 중복 없음, 팀별 라인 5개, 밴 10개) → 내전 묶음(`--session-date`)이 없으면 만들고 있으면 사용 → **새 대결 1개(피어리스 RESET)** 생성 → 세트를 순서대로 저장. 1팀/2팀과 피어리스는 서버가 기존 규칙으로 판정·검증한다.

```
# 먼저 확인(서버 호출 없음, 비밀번호 불필요)
node tools/artifact-import/import-matches.js <데이터.json> --date 2026-10-15 --session-date 2026-10-15 --dry-run
# 실제 실행(비밀번호는 환경변수로만)
node tools/artifact-import/import-matches.js <데이터.json> --date 2026-10-15 --session-date 2026-10-15 --server http://localhost:8080 --user <관리자 아이디>
```

- 이미 같은 날짜 묶음이 있으면 대결·세트 수를 먼저 보여 준다. **재실행하면 새 대결이 추가되고**, 첫 경기가 같은 지문(409 `DUPLICATE_SET`)이면 그 자리에서 중단한다(빈 대결이 남으면 관리 화면에서 지운다).
- 이름·닉네임은 `--verbose`일 때만(연결 실패 선수 안내에) 출력한다. 기본은 건수와 순번이다.
- 옵션: `--server`, `--user`, `--dry-run`, `--verbose`. 순수 변환 함수는 `convert-matches.js`, 테스트는 `convert-matches.test.js`.
