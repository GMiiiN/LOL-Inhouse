# M4.5 설계: 티어리스트·점수표·자동 팀 짜기·코인토스·아티팩트식 선수 관리

> 작성: architecture-planner(Opus), 2026-10-20. 리뷰 전 초안이다.
> 버전에 따라 동작이 달라질 수 있는 부분은 **[확인 필요]**로 표시했다.
> 기준: `docs/implementation-plan.md` M4.5 행, `docs/유저플로우.md` §1·§3, `docs/design/m3-design.md`, `docs/design/m4-design.md` §13. 친구 아티팩트 "League of Legends Team Maker"의 `app.js`는 규칙 출처로만 썼다(밴픽·결과 올리기·백업·기록 코드와 실명 데이터는 보지 않았다).

기존 계획에서 바뀌는 점:
- 계획의 "252가지 분할 × 라인 배정"과 "수동 32가지"는 실제 코드 기준으로 **126가지**와 **16가지**다. 아티팩트가 좌우 대칭인 경우를 처음부터 빼고 계산하기 때문이다(§1.5·§1.6).
- 아티팩트의 "회차"는 주인이 "공유하기"를 누른 단위다. 우리는 저장하는 즉시 모두에게 보이므로 **회차를 명시적으로 시작하는 기능**(담당자 버튼)으로 바꾼다(§1.9, Q2).
- 아티팩트의 "이 팀으로 밴픽 시작"(pending)은 앱 안 밴픽용이라 옮기지 않는다. 확정한 팀은 서버에 저장하지 않는다(Q3).

## 0. 결정 요약

| # | 결정 | 이유 |
|---|---|---|
| D1 | 팀 짜기 계산은 **서버의 순수 Java 도메인**(`domain.team`)에서 하고 TDD로 만든다. 결과가 같은지는 아티팩트 원본 JS로 만든 **골든 픽스처**로 보장한다 | 규칙 기준을 서버 하나로 둔다(M4 D11과 같은 원칙). 계산이 DB의 현재 티어·점수표로 이뤄져서 브라우저 캐시가 낡을 일이 없다. 계산량은 최악 약 180만 쌍 비교라 수십 ms다(§3) |
| D2 | 결과가 나온 뒤의 수동 교체·되돌리기·진영 바꾸기·코인토스는 **프런트**에서 한다. 서버는 참가자 10명×5라인의 점수표(`scoreMatrix`, 티어 없는 칸은 6티어 하로 대체)를 함께 보낸다 | 교체할 때마다 서버를 부르지 않아도 된다. 점수 공식(`pts`)은 서버에만 두고, 프런트는 표를 찾아 더하기만 한다 |
| D3 | 무작위 요소 두 가지: ① 후보별 좌우 교체는 서비스가 주입받은 `RandomGenerator` 빈으로 한다. 도메인은 교체 전 결과를 돌려준다. ② 코인은 프런트가 `crypto.getRandomValues`로 던지고, 이 함수를 주입할 수 있게 둔다 | 골든 테스트는 교체 전 결과를 비교한다(JS에서 `Math.random`을 0.99로 고정한 것과 같다). 테스트에서는 고정 난수와 가짜 타이머를 쓴다 |
| D4 | 라인 티어는 `player_lane_tier`(선수×라인 1행)에 둔다. 변동 이력은 append-only `player_lane_tier_change`(회차 번호, 이전 값, 새 값)에 쌓고, ▲▼ 표시는 **조회할 때 이력에서 계산**한다 | 아티팩트 `chg`는 최신 값만 덮어써서 이력이 사라진다. 로그로 두면 이력도 남고, "회차 시작 시점 값"을 따로 복사할 필요가 없다. 데이터가 100행 단위라 계산 비용이 거의 없다 |
| D5 | 회차는 `tier_round`(round_no)로 관리한다. 현재 회차 = 가장 큰 번호. 담당자가 "새 회차 시작"을 누르면 번호가 1 오른다. 이번 회차에 보이는 변동이 하나도 없으면 409 `TIER_ROUND_EMPTY`를 낸다 | 아티팩트에서 "공유하기" 한 번이 회차 하나였고, 변동이 없으면 회차가 오르지 않았다. 그 의미를 그대로 옮긴 것이다 |
| D6 | 점수표는 `score_setting` 단일 행(id=1, 숫자 열 14개)에 둔다. 버전은 두지 않는다. 기본값은 V4 시드와 Java 상수 `ScoreTable.DEFAULT` 두 곳에 있고, 둘이 같은지 테스트로 묶는다 | 계산 결과를 저장하지 않으니 버전이 필요 없다. 열에 타입이 있어 검증이 쉽다 |
| D7 | 점수는 **정수만** 받는다(-999~999). 아티팩트는 소수도 받았다 | 서버와 JS의 합계·정렬이 정확히 같아지려면 정수가 필요하다. 소수를 쓸 이유도 없다(Q8) |
| D8 | 선수 편집은 새 리소스 `/api/admin/player-profiles`로 한다. 요청 하나에 실명·주력 포지션·라인 티어·닉네임 목록을 담고, **한 트랜잭션**에서 처리한다(선수 행 잠금 → 이름 → 별칭 비교·반영 → 라인 티어·변동 로그). 별칭 규칙은 `PlayerService`의 기존 규칙을 그대로 쓴다 | 아티팩트처럼 한 화면에서 "저장" 한 번으로 끝난다. 일부만 저장되는 상태가 생기지 않는다. M3 API(`/api/admin/players/**`)는 M4 등록 화면이 쓰므로 그대로 둔다 |
| D9 | 새 패키지는 `tier`(라인 티어·회차·점수표·티어리스트·프로필)와 `teammaker`(계산 API)다. 의존 방향은 한쪽뿐이다: `teammaker → tier → player`. `PlayerResponse`는 바꾸지 않는다 | `player`가 `tier`를 알게 되면 패키지 순환이 생긴다(M4 R12 재발 방지). M4 등록 코드에 영향이 없다 |
| D10 | 팀 표기: 결과 화면은 아티팩트처럼 **블루 칸/레드 칸** 두 열이다. **블루 칸 = A팀**으로 고정한다(대결 규칙 "1세트는 블루 팀이 A팀"). 코인은 "어느 묶음이 블루(A팀)에 앉을지"를 정한다 | 아티팩트의 "1팀이 레드"를 그대로 옮기면 대결의 A팀 정의와 어긋난다. 이렇게 하면 동작은 아티팩트와 같고 이름만 우리 규칙을 따른다(Q5) |
| D11 | 같은 팀 조건, 참가자 선택, 수동 지정은 아티팩트처럼 **각 브라우저 localStorage**에 둔다. 서버에 저장하지 않는다 | 아티팩트도 "각자 브라우저에만 저장"이었다. 저장소를 늘리지 않는다 |
| D12 | 공개 범위는 Q1을 따른다. 추천은 조회(티어리스트·점수표)와 팀 짜기 계산을 **비로그인 공개**, 수정은 `/api/admin/**`로 두는 것이다. Phase 0 전에 확정해야 한다(경로가 달라짐) | 아티팩트도 "보기 전용" 친구가 티어를 보고 팀을 짰다. 실명 공개는 2026-10-16에 이미 정했다 |
| D13 | 새 의존성은 없다. 골든 픽스처 생성기는 Node 스크립트 하나(외부 패키지 없음)이고 CI에서는 돌리지 않는다. CI는 커밋된 JSON만 읽는다 | 근거 없는 기술 금지. Node는 프런트 때문에 이미 있다 |
| D14 | 응답에 `Instant`를 넣지 않는다(M4 D15와 같음) | `spring.jackson.time-zone` 문제(AGENTS §7)를 새로 만들지 않는다 |

## 1. 아티팩트 규칙 명세 (app.js에서 읽은 그대로, 우리 말로)

### 1.1 상수
- 라인 순서는 탑, 정글, 미드, 원딜, 서폿이다(`LANES`). 이 순서가 계산의 인덱스 0~4이고, 우리 `Position` enum 선언 순서(TOP, JUNGLE, MID, ADC, SUPPORT)와 같다. **이 순서가 동점 처리에 영향을 주므로 바꾸면 안 된다.**
- 티어는 1~6이고 1이 가장 잘하는 단계다. 티어 안 우열은 상·중·하(`SUBS`)다. 우리 enum은 `TierSub { HIGH(상), MID(중), LOW(하) }`로 한다.
- 기본 점수표(`DEFAULT_SCORE`):
  - base: 1=60, 2=52, 3=44, 4=36, 5=28, 6=20
  - sub: 상=+3, 중=0, 하=-3
  - lane: 탑 0, 정글 0, 미드 0, 원딜 -4, 서폿 -3

### 1.2 점수 공식
- `점수(라인 티어 v, 라인 k) = base[v.tier] + sub[v.sub] + lane[k]`이다. 아티팩트는 빈 값을 0으로 쳤지만, 우리 점수표는 항상 14칸이 다 차 있다.
- 기본표로 계산한 예: 1티어 상 탑 = 63, 3티어 중 원딜 = 40, 6티어 하 서폿 = 14.
- 점수 설정 화면의 "티어 × 우열 점수표"는 라인 보정 전 값(`base + sub`)인 6×3 격자다.

### 1.3 라인 티어가 없을 때 (`slotFor`)
- **자동 배정**에서는 티어가 없는 라인에 그 사람을 배치하지 않는다. `laneAssignments`가 그 순열을 버린다.
- **결과를 수동으로 교체할 때만** 티어 없는 라인에 들어갈 수 있다. 이때 점수는 `점수({6, 하}, k)`, 즉 `base[6] + sub[하] + lane[k]`다(기본표로 탑·정글·미드 17, 원딜 13, 서폿 14). 화면에는 "티어 없음"으로 표시한다.

### 1.4 정렬·순위
- `rank(v) = (7 - tier) × 3 + {상:2, 중:1, 하:0}`이다. 1티어 상이 20, 6티어 하가 3이다. **변동 방향을 정할 때만 쓴다.**
- 티어표(보드): 각 칸(티어 t × 라인 k) 안에서 상 → 중 → 하 순, 같으면 이름을 한국어 정렬(`localeCompare(…,'ko')`)한다. 칩에는 이름, 변동 표시, 점수를 보여준다.
- 사람별 보기: 이름의 한국어 정렬. 라인마다 "N티어 ▲ 점수"를 보여주고 상·중·하 글자는 표시하지 않는다. 티어가 없으면 "—"다.
- 자동 모드의 참가자 카드: 이름의 한국어 정렬. 보조 글은 "탑 2티어 · 미드 3티어"처럼 쓴다.
- 수동 모드의 라인별 후보: 그 라인 티어가 있는 사람만, 그 라인 점수 내림차순으로 보여준다. 같은 점수끼리는 원래 목록 순서를 따른다(안정 정렬). 우리는 서버 응답 순서(id 오름차순)를 원래 순서로 쓴다.
- 우리 쪽 한국어 정렬은 `Intl.Collator('ko')`로 한다(`localeCompare(…,'ko')`와 같은 ICU 비교) [확인 필요: Node/jsdom이 full-icu인지. Node 13부터 기본값].

### 1.5 자동 모드 (`makeTeams` auto, `laneAssignments`, `evaluate`, `cost`)
입력은 **순서 있는** 참가자 목록 `ids`다. 아티팩트에서는 클릭한 순서다. 정확히 10명이어야 하고, 아니면 "참가자를 정확히 10명 골라 주세요. 지금 N명이에요."를 낸다.

1. 마스크 `m`을 0부터 1023까지 올라가며 본다. 조건은 `m`의 0번 비트가 1이고 켜진 비트가 정확히 5개인 것이다. 그러면 **참가자 0번은 항상 블루 쪽**이고, 분할은 C(9,4) = **126가지**다. 블루 인덱스 `bi`와 레드 인덱스 `ri`는 각각 오름차순이다.
2. 같은 팀 조건을 만족하지 않으면 건너뛴다(§1.7).
3. `laneAssignments(5명)`: 5명을 5라인에 놓는 120가지 순열 `PERMS`를 **사전식 순서**로 돈다(`[0,1,2,3,4]`부터. 재귀가 남은 원소를 앞에서부터 고르므로 사전식이 된다). `perm[i]`는 팀 안 i번째 사람이 맡는 라인 인덱스다. 누가 그 라인 티어가 없으면 그 순열은 버린다. 남은 배정마다 `slots[라인] = {사람, 라인, 티어, 점수}`와 `sum`을 만든다.
4. 블루나 레드 쪽 배정이 하나도 없으면 그 분할은 건너뛴다.
5. `evaluate(A, B)`: 블루 배정 a(바깥 반복)와 레드 배정 b(안쪽 반복)를 모두 짝지어 본다.
   - `diff = |a.sum - b.sum|`
   - `gap = max over 라인 |a.slots[l].점수 - b.slots[l].점수|`
   - `cost = diff × 3 + gap`
   - `tot = a.sum + b.sum`
   - 최선은 cost가 **더 작거나**, cost가 같고 tot가 **더 큰** 쪽이다. 완전히 같으면 **먼저 찾은 것**을 남긴다. 분할마다 최선 하나만 결과에 넣는다.
6. 결과가 하나도 없으면 "가능한 조합이 없어요. 고른 사람들의 라인이 겹쳐서 양 팀 모두 5개 라인을 채울 수 없거나, 같은 팀 조건 때문에 막혀요."를 낸다.

### 1.6 수동 모드 (라인 직접 지정)
- 라인마다 두 명 `[첫째, 둘째]`가 필요하다. 비어 있으면 "{라인} 라인에 2명을 모두 골라 주세요." 같은 사람이 두 번 나오면 "{이름}님이 두 번 선택됐어요."
- `c`를 1부터 31까지 **홀수만** 본다. 라인 i의 비트가 1이면 첫째가 블루, 0이면 둘째가 블루다. 탑의 비트가 항상 1이라 **16가지**다. 같은 팀 조건을 만족하지 않으면 건너뛴다.
- 각 칸의 점수는 그 사람의 그 라인 점수다. `diff`, `gap`, `cost`는 자동 모드와 같다. `tot`는 모든 경우에 같으므로 정렬은 사실상 cost 다음 `c` 순서다.
- 결과가 없으면 "같은 팀 조건을 만족하는 조합이 없어요. 같은 라인의 두 사람은 같은 팀이 될 수 없어요."

### 1.7 같은 팀 조건 (`okTogether`)
- `[a, b]` 쌍마다 "a가 블루 쪽인지"와 "b가 블루 쪽인지"가 같아야 한다.
- 아티팩트의 결함: 선수가 존재하기만 하면(`byId`) 참가자가 아니어도 검사한다. 참가자 a와 참가자가 아닌 b가 쌍이면 a는 항상 레드 쪽에 놓인다(좌우 교체 전 기준). 화면은 참가자 중에서만 고르게 했지만, 나중에 참가자를 빼도 쌍은 남는다.
- **우리 규칙(의도한 차이):** 프런트는 참가자에서 빠진 사람의 쌍을 자동으로 지운다. 서버는 참가자가 아닌 사람이 들어간 쌍, 자기 자신과의 쌍을 400 `TEAM_PAIR_INVALID`로 거부한다. 중복 쌍(순서만 바뀐 것 포함)은 하나로 합친다. 정상 입력에서는 결과가 아티팩트와 같다.

### 1.8 정렬, 상위 4개, 무작위 좌우 교체
- 결과 목록은 cost 오름차순, 다음 tot 내림차순으로 **안정 정렬**한다(JS `Array.prototype.sort`는 ES2019부터 안정적이고, Java `List.sort`(TimSort)도 안정적이다). 그다음 앞에서 **최대 4개**를 고른다.
- 그 4개 각각을 50% 확률로 블루와 레드째 맞바꾼다(`Math.random() < 0.5`).
- 우리: 도메인은 교체 전 상위 4개를 돌려주고, `TeamMakerService`가 주입받은 `RandomGenerator`로 후보마다 `nextBoolean()`을 한 번씩 써서 교체한다.

### 1.9 결과 화면 동작 (프런트)
- 후보 카드: "방안 N", 수정했으면 "수정됨" 표시, 블루 합계:레드 합계, "점수 차 diff · 최대 라인 격차 gap", 양 팀 이름(라인 순서).
- 판정 문구(선택한 방안):
  - `diff ≤ 3 && gap ≤ 12`: good "점수도 라인 맞대결도 고르게 맞춰졌어요."
  - `diff ≤ 8`: good "괜찮은 밸런스예요."
  - 그 밖: warn "차이가 좀 있어요. 다른 방안이나 참가자 구성을 바꿔 보세요."
- 라인별 줄: 가운데 라인 이름과 차이 g(블루 - 레드)를 보여준다. 0이면 "동점", 양수면 "◀ g", 음수면 "|g| ▶"다.
- **수동 교체(res-swap):** 첫 칸을 누르면 선택된다. 같은 칸을 다시 누르면 선택이 풀린다. 다른 칸을 누르면 두 사람이 자리를 바꾸고, 각자 새 자리 라인으로 `slotFor`를 다시 계산한다(같은 팀 안 라인 교환, 상대 팀과의 교환 모두 가능). 처음 교체할 때 원래 배치(`orig`)를 저장하고 합계·diff·gap·cost를 다시 계산한 뒤 "수정됨"을 표시한다. "처음 방안으로"는 `orig`로 되돌린다. 수정 상태는 방안마다 따로 유지된다.
- 다른 방안을 고르면 선택 칸과 코인 상태를 지운다. "팀 짜기"를 다시 누르면 결과 전체를 다시 만든다.
- **진영 바꾸기:** 블루 칸과 레드 칸을 통째로 맞바꾼다.
- **결과 복사:** 블루팀(점수)과 라인별 이름, 레드팀, 점수 차를 텍스트로 만든다. 우리는 "A팀 · 블루 (N점)" / "B팀 · 레드 (N점)"로 쓴다. 클립보드에 쓰지 못하면 읽기 전용 textarea를 보여준다.
- **코인토스:** 같은 방안에서 처음 던질 때 블루 칸에 있던 묶음을 t1으로 기억한다. 다시 던져도 t1은 그대로다. 면은 난수 1비트로 정한다(1이면 블루). 1.5초 애니메이션이 끝나면 "나온 면 = t1의 진영"이 되도록, 필요하면 칸을 맞바꾼다. `prefers-reduced-motion`이면 즉시 결과를 낸다. 회전 각은 블루 1800도, 레드 1980도다.
  - 우리 문구(D10): "블루가 나왔어요. {t1 이름들}이 블루, 즉 A팀이에요." 블루 칸이 항상 A팀이다.
  - 우리 단순화(의도한 차이): 수동 교체나 진영 바꾸기를 하면 코인 결과를 지운다. 아티팩트는 칸 객체가 같은지로 t1을 찾아서, 교체 뒤에는 동작이 불명확했다.
- 아티팩트는 데이터를 수정할 때마다 결과를 지웠다. 우리 결과는 계산한 시점의 서버 스냅샷이다. 티어가 바뀌어도 자동으로 지우지 않고, 사용자가 다시 계산한다.

### 1.10 티어 변동 회차 (`setPlayerLanes`, `latestRound`, `trend`)
아티팩트 동작:
- 수정 묶음(공유하기 전 초안) 하나가 회차 r = 공유된 round + 1이다.
- 라인마다 "공유된 값 o"와 "새 값 n"을 비교한다.
  - n이 없으면(라인 삭제) 변동 기록을 지운다.
  - o가 없으면(새 라인) 변동이 아니다.
  - `rank(n) - rank(o)`가 0이 아니면 `chg = {방향 ±1, from: o.tier, r}`이다.
  - 0이고 이번 회차 기록이면 지운다(되돌림).
  - 이번 회차에 변동이 하나라도 있으면 round = r로 올린다.
- 표시: `latestRound = 모든 chg의 r 중 최댓값`이다. 그 회차의 chg이면서 현재 라인 티어가 있을 때만 ▲(방향 > 0)나 ▼를 보여준다.
- 툴팁: 이전 티어 ≠ 현재 티어면 "{from}티어 → {현재}티어", 같으면 "같은 티어 안에서 상승/하락"이다.
- 새 선수를 만들 때는 변동이 없다.

우리 구현(D4·D5): 변동 로그에서 계산한다.
- 회차 R, 선수 p, 라인 k마다 `start` = 회차 R의 첫 로그의 이전 값, `end` = 회차 R의 마지막 로그의 새 값이다.
- **보이는 변동** = `start`와 `end`가 모두 있고 `rank`가 다른 것이다.
- `L` = 보이는 변동이 있는 가장 큰 회차다. 표시 대상은 회차 L의 보이는 변동 중 현재 라인 티어가 있는 것이다. 방향은 `rank(end) - rank(start)`의 부호, `fromTier`는 `start.tier`다. 툴팁은 현재 값과 비교한다.
- 일반적인 사용에서는 아티팩트와 표시가 같다. 다음 가장자리 사례에서만 다르다(의도한 차이). 한 회차에서 바뀐 라인이 다음 회차에서 바뀌었다가 되돌려지거나, 삭제됐다가 같은 값으로 다시 추가된 경우다. 이때 아티팩트는 이전 회차 표시를 잃고, 우리는 유지한다.
- 회차 시작은 아티팩트의 "공유하기"에 해당한다. 현재 회차에 보이는 변동이 없으면 409를 낸다. 아티팩트에서도 변동이 없으면 round가 오르지 않았다.

### 1.11 선수 편집기 (`renderEditor` + `save-ed` + `renderNick`)
- 필드: 이름. 우리는 실명이고 아티팩트 최대 16자 대신 기존 규칙 50자를 쓴다. 라인 5개마다 [안 함 / 1~6티어] 선택과 [상·중·하] 선택이 있고, 새 라인의 기본값은 중이다. 닉네임은 쉼표로 구분한다(아티팩트에서는 별도 "닉네임 연동" 탭이었는데, 사용자 결정에 따라 편집기에 합친다).
- 검증 문구:
  - "이름을 입력해 주세요."
  - "같은 이름이 이미 있어요."(우리: 앞뒤 공백을 뺀 실명이 다른 선수와 같으면)
  - "최소 한 라인은 티어를 골라 주세요."(우리: **새로 만들 때만**. 기존 선수는 0개 허용, Q4)
- 도움말: "갈 수 있는 라인만 티어를 고르세요. '안 함'인 라인에는 자동 배정되지 않아요." 닉네임 도움말은 "띄어쓰기와 대소문자는 구분하지 않아요" 대신 우리 규칙(`NicknameNormalizer`)에 맞게 쓴다.
- 삭제 버튼은 두 번 눌러 확인한다("삭제" → "정말 삭제"). Q6에 따라 넣거나 뺀다.

### 1.12 범위 밖 (옮기지 않음)
앱 안 밴픽, 결과 올리기·지난 경기 입력, 전적 탭(M5), 백업·기록 코드, **티어 조정 추천(`computeRecs`·`since`)**은 옮기지 않는다. 추천은 경기 기록 통계가 필요하므로 M5 이후 AGENTS 개선 후보로 올린다(Q11).

## 2. 스키마 `V4__tier_team_maker.sql`

M3 §1.1 공통 규칙을 따른다: InnoDB/utf8mb4_0900_ai_ci 명시, 열거형은 `VARCHAR ascii_bin` + CHECK, 시각은 `DATETIME(6)` UTC, 제약 이름 `uk_/fk_/ck_/ix_`, DB DEFAULT 시각 없음. V1~V3는 수정하지 않는다.

```sql
-- 선수별 라인 티어(현재 값). 설명: docs/design/m45-design.md §2
CREATE TABLE player_lane_tier (
  id         BIGINT      NOT NULL AUTO_INCREMENT,
  player_id  BIGINT      NOT NULL,
  position   VARCHAR(10) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  tier       INT         NOT NULL,                          -- 1(최상)~6
  sub        VARCHAR(4)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL,  -- HIGH/MID/LOW = 상/중/하
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_player_lane_tier_player_position (player_id, position),
  CONSTRAINT fk_player_lane_tier_player FOREIGN KEY (player_id) REFERENCES player (id) ON DELETE CASCADE,
  CONSTRAINT ck_player_lane_tier_position CHECK (position IN ('TOP','JUNGLE','MID','ADC','SUPPORT')),
  CONSTRAINT ck_player_lane_tier_tier CHECK (tier BETWEEN 1 AND 6),
  CONSTRAINT ck_player_lane_tier_sub  CHECK (sub IN ('HIGH','MID','LOW'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 티어 변동 회차. 현재 회차 = 최대 round_no
CREATE TABLE tier_round (
  id         BIGINT      NOT NULL AUTO_INCREMENT,
  round_no   INT         NOT NULL,
  started_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_tier_round_round_no (round_no),
  CONSTRAINT ck_tier_round_round_no CHECK (round_no >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
INSERT INTO tier_round (round_no, started_at) VALUES (1, UTC_TIMESTAMP(6));

-- 라인 티어 변동 로그(append-only). 값이 실제로 바뀐 때만 쌓는다. NULL = 그 라인 티어 없음
CREATE TABLE player_lane_tier_change (
  id         BIGINT      NOT NULL AUTO_INCREMENT,
  player_id  BIGINT      NOT NULL,
  position   VARCHAR(10) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  round_no   INT         NOT NULL,
  from_tier  INT NULL, from_sub VARCHAR(4) CHARACTER SET ascii COLLATE ascii_bin NULL,
  to_tier    INT NULL, to_sub   VARCHAR(4) CHARACTER SET ascii COLLATE ascii_bin NULL,
  changed_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  KEY ix_player_lane_tier_change_round_no (round_no),
  KEY ix_player_lane_tier_change_player_id (player_id),
  CONSTRAINT fk_player_lane_tier_change_player FOREIGN KEY (player_id) REFERENCES player (id) ON DELETE CASCADE,
  CONSTRAINT fk_player_lane_tier_change_round  FOREIGN KEY (round_no)  REFERENCES tier_round (round_no) ON DELETE RESTRICT,
  CONSTRAINT ck_player_lane_tier_change_position CHECK (position IN ('TOP','JUNGLE','MID','ADC','SUPPORT')),
  CONSTRAINT ck_player_lane_tier_change_from CHECK ((from_tier IS NULL) = (from_sub IS NULL)),
  CONSTRAINT ck_player_lane_tier_change_to   CHECK ((to_tier IS NULL) = (to_sub IS NULL)),
  CONSTRAINT ck_player_lane_tier_change_some CHECK (from_tier IS NOT NULL OR to_tier IS NOT NULL),
  CONSTRAINT ck_player_lane_tier_change_tiers CHECK ((from_tier IS NULL OR from_tier BETWEEN 1 AND 6) AND (to_tier IS NULL OR to_tier BETWEEN 1 AND 6)),
  CONSTRAINT ck_player_lane_tier_change_subs  CHECK ((from_sub IS NULL OR from_sub IN ('HIGH','MID','LOW')) AND (to_sub IS NULL OR to_sub IN ('HIGH','MID','LOW')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 점수표(현재 값 1행)
CREATE TABLE score_setting (
  id INT NOT NULL,
  base_tier1 INT NOT NULL, base_tier2 INT NOT NULL, base_tier3 INT NOT NULL,
  base_tier4 INT NOT NULL, base_tier5 INT NOT NULL, base_tier6 INT NOT NULL,
  sub_high INT NOT NULL, sub_mid INT NOT NULL, sub_low INT NOT NULL,
  lane_top INT NOT NULL, lane_jungle INT NOT NULL, lane_mid INT NOT NULL, lane_adc INT NOT NULL, lane_support INT NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT ck_score_setting_single CHECK (id = 1),
  CONSTRAINT ck_score_setting_range CHECK (base_tier1 BETWEEN -999 AND 999 AND … AND lane_support BETWEEN -999 AND 999)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
INSERT INTO score_setting VALUES (1, 60,52,44,36,28,20, 3,0,-3, 0,0,0,-4,-3, UTC_TIMESTAMP(6));
```

설계 메모:
- **같은 팀 조건, 참가자 선택, 확정 팀은 저장하지 않는다**(D11, Q3). Q3에서 "연결"을 고르면 V5로 `team_plan`(id, duel_id NULL UNIQUE, created_at)과 `team_plan_player`(plan_id, player_id, team TEAM1/TEAM2, position)를 추가한다(아래 Q3). V4에는 넣지 않는다.
- `ON DELETE CASCADE`는 선수에게 딸린 하위 데이터(라인 티어·로그)에만 쓴다. 선수 삭제(Q6)를 넣지 않더라도 해가 없다.
- **[확인 필요]** MySQL 오류 3823(CASCADE·SET NULL 참조 동작이 걸린 FK 컬럼은 CHECK에 쓸 수 없음): `player_id`는 CHECK에 쓰지 않으므로 괜찮을 것으로 본다. `round_no`는 RESTRICT다. Phase 0 마이그레이션 테스트로 확인한다.
- **[확인 필요]** `tier INT`와 Java `int` 매핑이 Hibernate `validate`를 통과하는지 확인한다(TINYINT를 피하려고 INT로 했다).
- **[확인 필요]** Flyway 시드의 `UTC_TIMESTAMP(6)`. 대안은 앱이 시작할 때 비어 있으면 넣는 것이다.
- 기존 선수(미등록 포함)와 별칭은 그대로 둔다. 라인 티어 초기값은 비어 있다(Q4).

## 3. 계산 위치: 서버 (추천)

| 기준 | 서버(Java 도메인) | 프런트(TS 이식) |
|---|---|---|
| 아티팩트와 같은 결과 | 원본 JS로 만든 골든 JSON을 Java가 대조한다. 정수 연산과 안정 정렬이 같아 차이가 날 곳이 없다 | 원본 JS와 같은 런타임에서 직접 대조할 수 있어 가장 쉽다 |
| 규칙 기준 | 서버 하나(M4 D11). DB의 최신 티어·점수표를 쓴다 | 브라우저가 받은 데이터가 낡을 수 있다 |
| 테스트 관례 | 도메인 규칙은 Java TDD(프로젝트 원칙) | 예외가 된다 |
| 응답 시간 | 최악: 126분할 × 120×120 쌍 = 약 181만 쌍 × 5라인, 약 900만 번의 단순 연산. Java로 수십 ms 이하로 추정한다(측정은 Phase 1-A). 보통은 한 사람이 1~3라인이라 훨씬 적다. 왕복 시간이 추가된다 | 아티팩트도 브라우저에서 수용 가능한 속도였다 |
| 공개 시 부하 | 비로그인 POST가 수십 ms CPU를 쓴다. 친구 규모에서는 문제없다(속도 제한은 M7) | 없음 |

결론: **서버**(D1). 결과가 나온 뒤의 교체 재계산은 덧셈과 최댓값뿐이라 프런트에서 하고, 점수는 서버가 준 `scoreMatrix`에서 찾는다(D2). 공식을 두 곳에 두지 않는다.

## 4. API

Q1 추천안 기준이다. 공개를 고르지 않으면 공개 경로 3개를 `/api/admin/` 아래로 옮긴다. 오류는 기존대로 ProblemDetail + `code`다. 변경 요청은 모두 CSRF가 필요하다. 비로그인도 앱이 시작할 때 `GET /api/csrf`로 토큰을 받으므로 공개 POST가 동작한다.

### 4.1 공개 (SecurityConfig에 permitAll 3줄 추가)
- `GET /api/tier-list` → `{ currentRoundNo, latestChangedRoundNo|null, currentRoundHasChanges, scoreTable, players: [{ id, displayName, lanes: { TOP: {tier, sub, score, trend: {direction: "UP"|"DOWN", fromTier}|null} | null, … } }] }`
  - 대상은 등록 선수 전체다(라인 티어가 0개일 수 있다). `score`는 서버가 계산한다. 순서는 id 오름차순이고 한국어 정렬은 프런트가 한다.
- `GET /api/score-table` → `{ base: {"1":60,…,"6":20}, sub: {HIGH:3, MID:0, LOW:-3}, lane: {TOP:0,…}, isDefault }`
- `POST /api/team-maker/auto` 본문 `{ participantIds: [10개, 순서 유지], together: [[a,b],…] }`
- `POST /api/team-maker/manual` 본문 `{ picks: { TOP:[a,b], JUNGLE:[…], MID, ADC, SUPPORT }, together }`
  - 두 계산 API의 응답: `{ options: [{ blue: [slot×5 라인 순], red: [slot×5], blueSum, redSum, diff, gap, cost, total }] (1~4개), scoreMatrix: { "<playerId>": { TOP: {score, hasTier}, … } } }`
  - `slot = { playerId, position, tier|null, sub|null, score }`. 블루 칸이 A팀이다(D10).

### 4.2 담당자 (`/api/admin/**`)
- `GET /api/admin/player-profiles` → 선수 전체(미등록 포함)를 담는다: `[{ id, displayName, realName|null, registered, mainPosition|null, aliases:[{id,nickname}], lanes:{…tier,sub} }]`
- `POST /api/admin/player-profiles` 본문 `{ realName, mainPosition|null, lanes: {TOP: {tier, sub}|null, …}, nicknames: [1~10개] }` → 201
  - 라인이 최소 1개 있어야 한다(아티팩트와 같음). 만들 때 라인 값은 로그를 남기지만 이전 값이 NULL이라 변동으로 치지 않는다.
- `PUT /api/admin/player-profiles/{id}` 같은 본문 → 200. 미등록 선수에 실명을 넣으면 등록된다. 처리 순서(한 트랜잭션):
  1. `PlayerRepository.findByIdForUpdate`(기존 행 잠금, 실패하면 409 기존 코드)
  2. 실명·주력 포지션: 기존 `update` 규칙(등록 선수 실명 비우기 금지). 실명이 **바뀔 때만** 다른 선수와 중복인지 검사한다.
  3. 닉네임: `PlayerService.replaceAliases(playerId, nicknames)`(새 공개 메서드). 비교 키 기준으로 새 키를 먼저 추가하고, 빠진 별칭을 나중에 지운다. 그래서 별칭이 0개가 되는 순간이 없다. 같은 키의 기존 별칭은 표기도 그대로 둔다. 대표 닉네임이 지워지면 남은 것 중 가장 오래된 것을 대표로 한다(기존 규칙). 목록이 비면 400 `NICKNAME_REQUIRED`(마지막 별칭 보호와 같은 뜻)다. 충돌은 기존 409 `ALIAS_CONFLICT` + `conflicts`다.
  4. 라인 티어: 현재 회차 번호를 읽고, 라인마다 이전 값과 새 값이 다를 때만 `player_lane_tier`를 추가·수정·삭제하고 로그 1행을 남긴다.
- `DELETE /api/admin/player-profiles/{id}`: Q6에서 넣기로 하면 추가한다. 세트 기록이 있으면 409 `PLAYER_HAS_RECORDS`, 없으면 별칭·티어·로그·선수를 지우고 204를 돌려준다.
- `PUT /api/admin/score-table` 본문은 GET 모양(isDefault 제외) → 200. 14칸이 모두 필요하고 정수 -999~999다.
- `POST /api/admin/score-table/reset` → 200(서버 상수 `ScoreTable.DEFAULT`로 덮어씀).
- `POST /api/admin/tier-rounds` → 201 `{ roundNo }`. 현재 회차에 보이는 변동이 없으면 409 `TIER_ROUND_EMPTY`, 동시에 두 번 누르면 UNIQUE 위반이라 409 `CONCURRENT_REQUEST_CONFLICT`다.

### 4.3 오류 코드 (새로 추가)
| 코드 | 상태 | 경우 |
|---|---|---|
| `INVALID_LANE_TIER` | 400 | 티어가 1~6 밖, sub 값 오류, 모르는 라인 |
| `LANE_TIER_REQUIRED` | 400 | 만들 때 라인이 0개 |
| `NICKNAME_REQUIRED` | 400 | 닉네임 목록이 빔 |
| `DUPLICATE_REAL_NAME` | 409 | 다른 선수와 실명이 같음(앞뒤 공백 제외, 정확히 같음) |
| `PLAYER_HAS_RECORDS` | 409 | (Q6) 기록 있는 선수 삭제 |
| `INVALID_SCORE_TABLE` | 400 | 칸 누락·범위 밖·정수 아님 |
| `TIER_ROUND_EMPTY` | 409 | 변동 없는 회차를 마감하려 함 |
| `TEAM_PARTICIPANT_COUNT` | 400 | 자동 모드 인원이 10명이 아님(`count` 포함) |
| `TEAM_DUPLICATE_PARTICIPANT` | 400 | 같은 사람이 두 번(수동은 `playerId` 포함) |
| `TEAM_LANE_PICK_INCOMPLETE` | 400 | 수동 모드에서 어떤 라인이 2명이 아님(`position`) |
| `TEAM_LANE_TIER_MISSING` | 400 | 수동 모드에서 그 라인 티어가 없는 사람(아티팩트는 NaN이 됨) |
| `TEAM_PLAYER_NOT_FOUND` | 400 | 없는 선수 id |
| `TEAM_PAIR_INVALID` | 400 | 쌍에 참가자가 아닌 사람이 있거나 자기 자신과의 쌍 |
| `TEAM_NO_VALID_SPLIT` | 422 | 자동 모드 결과 0개(§1.5-6 문구) |
| `TEAM_PAIR_UNSATISFIABLE` | 422 | 수동 모드 결과 0개(§1.6 문구) |
기존 코드는 그대로 쓴다: `ALIAS_CONFLICT`, `DUPLICATE_NICKNAME_IN_REQUEST`, `INVALID_NICKNAME`, `INVALID_REAL_NAME`, `REAL_NAME_CANNOT_BE_CLEARED`, `PLAYER_NOT_FOUND`, `CONCURRENT_REQUEST_CONFLICT`.

### 4.4 백엔드 클래스 (Phase 0에서 시그니처 고정)
- `domain.tier`: `TierSub`(HIGH/MID/LOW, label, rankBonus 2/1/0), `LaneTier(int tier, TierSub sub)` + `rank()`, `ScoreTable`(base[6], sub, lane) + `points(LaneTier, Position)`·`fallbackPoints(Position)`(6티어 하)·`DEFAULT`, `TierTrend.compute(List<ChangeEntry>, Map<PlayerLane, LaneTier> current) → TrendResult(latestRoundNo, Map<PlayerLane, Trend>)`, `TierTrend.hasVisibleChange(entries, roundNo)`.
- `domain.team`: `TeamMakerPlayer(long id, Map<Position, LaneTier>)`, `Slot`, `TeamOption`(blue/red 슬롯, 합계, diff, gap, cost, total), `TeamCost.of(diff, gap)`, `TeamMaker.auto(List<TeamMakerPlayer> ordered, Set<Pair> together, ScoreTable) → TeamMakeResult`(옵션 목록 또는 실패 이유), `TeamMaker.manual(Map<Position, List<TeamMakerPlayer>> picks, …)`, `ScoreMatrix.of(players, table)`. 무작위는 쓰지 않는다.
- `tier`: 엔티티 `PlayerLaneTier`, `PlayerLaneTierChange`, `TierRound`, `ScoreSetting` + 리포지토리, `TierListService`, `ScoreSettingService`, `TierRoundService`, `PlayerProfileService`, 컨트롤러 `TierListController`(공개 GET 2개), `ScoreSettingAdminController`, `TierRoundAdminController`, `PlayerProfileController`, DTO record.
- `teammaker`: `TeamMakerService`(입력 검증 → 티어·점수표 조회 → 도메인 → `RandomGenerator`로 좌우 교체), `TeamMakerController`, DTO. `common.config`에 `RandomGenerator` 빈(테스트는 `@Primary` 고정 빈).
- `player.PlayerService`에 추가할 것: `replaceAliases(Long playerId, List<String> nicknames)`(호출자 트랜잭션에 참여, 선수 행은 호출자가 이미 잠금)와 실명 중복 검사(이름 변경 시). M3 `create`/`update`에도 같은 중복 검사를 적용할지는 Q10에 따른다.

## 5. 프런트

라우트(Q1 추천안):
- 공개: `/tiers`(티어리스트 보기), `/team-maker`(팀 짜기). PublicLayout 메뉴에 "티어리스트", "팀 짜기"를 추가한다.
- 담당자: `/admin/players`(M3 화면을 대체: 티어리스트 + 편집기 + 미등록 목록 + 새 회차 버튼), `/admin/score`(점수 설정). AdminLayout 메뉴에 "점수 설정"을 추가한다.

파일:
- `src/api/tierTypes.ts`(계약, Phase 0), `src/api/tier.ts`(호출 함수), `queryKeys.ts`에 `TIER_LIST_KEY`, `SCORE_TABLE_KEY`, `PLAYER_PROFILES_KEY` 추가. 프로필을 바꾸면 `PLAYERS_KEY`(M4 등록 화면)도 무효화한다.
- `src/pages/tier/`:
  - `TierBoard.tsx`: 6행×5열 보드. 행 색은 `--tc: var(--tN)`. 칩에는 이름, ▲▼, 점수를 보여주고, 관리자 모드에서는 칩이 버튼이 되어 편집기를 연다.
  - `TierPeopleTable.tsx`: 사람별 표. 관리자 모드에는 수정·삭제 열이 있다.
  - `TrendMark.tsx`: `tr up|down`, title·aria-label이 툴팁 문구다.
  - `tierSort.ts`: `Intl.Collator('ko')`, 상 → 중 → 하.
  - `TierListPage.tsx`: 보기 전환(티어표/사람별)은 `seg sm`이다.
- `src/pages/admin/players/`:
  - `PlayerTierAdminPage.tsx`: TierListPage 구성에 편집 모드, "사람 추가", "새 회차 시작"(`currentRoundHasChanges`가 false면 비활성, 확인 문구 있음), 접는 "미등록 선수 N명" 목록(누르면 편집기, 실명 입력 시 등록)을 더한다.
  - `PlayerEditor.tsx`: 아티팩트 `renderEditor`와 같다. `edgrid`에 라인별 select 2개, 이름, 쉼표 닉네임 입력, 주력 포지션(선택, Q9)이 있다. 오류 문구는 §1.11과 `errorMessages.ts`의 새 코드를 쓴다.
  - `playerEditorModel.ts`: 폼 ↔ 요청 변환, 쉼표 분리, 검증.
- `src/pages/team/`:
  - `TeamMakerPage.tsx`
  - `AutoRoster.tsx`: `pick` 카드, n/10 표시, 모두 해제.
  - `ManualPicker.tsx`: 라인별 select 2개. 옵션 문구는 "이름 · N티어 (점수점)"이다.
  - `TogetherPairs.tsx`: 참가자만 고를 수 있고, 같은 사람이나 빈 값이면 "같은 팀으로 묶을 서로 다른 두 사람을 골라 주세요."를 낸다.
  - `TeamResults.tsx`: 후보 카드 4개, 상세, 미터, 라인 줄, 판정, 복사.
  - `CoinToss.tsx`: `coinbox`·`coin flip`·`--end`. `random`과 `delayMs`를 props로 주입할 수 있다.
  - `teamResultModel.ts`: 교체·되돌리기·진영 바꾸기·재계산·판정·복사 텍스트를 담는 순수 함수다.
  - `useTeamMakerState.ts`: localStorage 키 `lol-inhouse-team-maker`에 `{mode, chosen(순서 유지), pick, together}`를 둔다. 티어리스트에 없는 id는 걸러 낸다.
- `src/pages/admin/score/ScoreSettingsPage.tsx`: 기본 점수 6칸, 우열 보정 3칸, 라인 보정 5칸, 6×3 격자, "기본값으로" 버튼. 입력을 바꾸면 전체 표를 PUT한다(400ms 디바운스, 아티팩트의 즉시 저장 느낌 유지). 비로그인 공개 보기가 필요하면 같은 컴포넌트를 읽기 전용으로 쓴다(Q1).
- 스타일: `src/styles/tier.css`, `team.css`는 아티팩트 `style.css`의 board·chip·pick·pickrow·pairs·opt·lanerow·meter·score-head·coin 규칙을 옮긴 것이다. `theme.css`에 `--t1..--t6`를 추가한다. 다크 값은 아티팩트 그대로(#e6bb62, #ae8ef2, #58b9e4, #63c391, #93a0b8, #6c768b)이고, 아티팩트에 라이트 테마가 없으므로 라이트 값은 Phase 0에서 대비를 맞춰 새로 정한다.
- M3 파일 `PlayersPage.tsx`, `PlayerForm.tsx`, `PlayerCard.tsx`, `playerFormValidation.ts`, `PlayersPage.test.tsx`는 대체로 쓰이지 않게 되므로 지운다. `api/admin.ts`의 `addAlias`·`deleteAlias`·`createPlayer`·`updatePlayer`가 다른 곳에서 쓰이지 않으면 함께 지운다. `listPlayers`는 M4가 쓰므로 남긴다.

## 6. M3 선수 관리에서의 이전

- **데이터 이전은 없다.** `player`·`player_alias`는 그대로이고 라인 티어는 비어서 시작한다. 등록 선수는 사람별 보기에 "—"로 보이고 티어표·팀 짜기 명단에는 없다. 담당자가 편집기로 채운다(Q4).
- 미등록 선수(M4 등록 중 자동 생성)는 담당자 화면의 "미등록 선수" 목록에만 나온다. 실명과 라인 티어를 넣으면 등록 선수가 된다. 선수 ID가 같으므로 전적은 이어진다(유저플로우 §3.2).
- 알려진 한계: 미등록 선수가 사실 기존 선수의 부캐라면 별칭을 옮길 수 없다(비교 키 UNIQUE). 409 `ALIAS_CONFLICT`의 `conflicts`로 안내하고, 선수 병합은 M6 후보 그대로다.
- `main_position`은 유지하고 편집기에서 선택 항목으로 둔다(Q9).
- M4 등록 화면의 선수 고르기(`PlayerPicker`, `GET /api/admin/players`)는 바꾸지 않는다. 프로필을 저장하면 `PLAYERS_KEY`를 무효화해서 등록 화면이 새 별칭을 바로 본다.
- M3 API `/api/admin/players/**`는 남긴다(M4 사용, 테스트 유지). 새 화면은 `/api/admin/player-profiles`만 쓴다.

## 7. 테스트 계획

### 7.1 골든 테스트 (아티팩트와 결과가 같은지)
- 생성기 `tools/team-maker-golden/`(Node, 외부 패키지 없음):
  - `reference.js`: 아티팩트 `app.js`에서 해당 줄을 **고치지 않고 그대로 옮긴 것**이다. 대상은 23행 `pts`, 78행 `rank`, 254~324행 `PERMS`·`laneAssignments`·`cost`·`evaluate`·`okTogether`·`makeTeams`·`slotFor`·`recalcResult`다. 전역(`data`, `team`, `byId`, `ui`)을 클로저 인자로 넣는 얇은 감싸개만 붙이고, `Math.random`은 0.99로 고정해 좌우 교체가 없게 한다. Q7에서 커밋 여부를 정한다.
  - `generate.js`: 시드 PRNG(mulberry32)로 **가상 선수**("가상01"~)를 만든다. 시나리오는 다음과 같다.
    - 자동 200개: 사람마다 라인 1~4개, 티어와 상중하는 무작위. 점수표는 기본표 + 무작위 정수표. 같은 팀 조건 0~2쌍. 참가자 순서를 섞는다.
    - 수동 100개.
    - 동점이 많은 것 30개: 전원 같은 티어, 일부 라인만 다름.
    - 실패 20개: 라인이 겹쳐 분할 불가, 같은 팀 조건으로 막힘.
  - 출력은 `backend/src/test/resources/team-maker/golden-v1.json`이다. 머리말에 `app.js`의 SHA-256, 옮긴 줄 범위, 시드를 적는다. 친구 데이터는 넣지 않는다.
- Java `TeamMakerGoldenTest`: 시나리오마다 결과 목록 전체(순서, 라인별 슬롯 playerId·점수, blueSum, redSum, diff, gap, cost, total)나 실패 종류가 **완전히 같은지** 본다. 슬롯 교체(`slotFor`+`recalcResult`)도 시나리오 일부에 교체 작업 열을 넣어 프런트 `teamResultModel` 테스트와 같은 픽스처로 대조한다(Vitest가 JSON을 읽음).

### 7.2 도메인 TDD (손으로 계산한 작은 사례, 먼저 작성)
- `ScoreTable`: 공식, 기본값(1상 탑 63, 3중 원딜 40), 6티어 하 대체 점수(탑 17, 원딜 13, 서폿 14), V4 시드 == `DEFAULT`(통합 테스트).
- `LaneTier.rank`: 1상 = 20, 6하 = 3. 같은 티어 안 상승·하락.
- 수동 모드 안정 순서: 탑만 P1(1중, 60)·P2(2중, 52)이고 나머지 라인은 두 명 모두 3중이면, 16가지 모두 diff 8, gap 8, cost 32다. 상위 4개는 c = 1, 3, 5, 7 순서이고 블루 탑은 항상 P1이다.
- 자동 모드: 참가자 0번이 항상 블루(교체 전), 126분할, 라인 없는 순열 제외, cost가 같으면 tot가 큰 쪽, 완전 동점이면 먼저 찾은 것, 결과가 4개 미만이면 그대로, 같은 팀 조건, 실패 두 종류.
- `TierTrend`: 새 라인은 표시 없음, 삭제된 라인은 표시 없음, 같은 회차 안에서 되돌리면 표시 없음, 가장 최근 변동 회차만 표시, 이전 회차 표시 숨김, 툴팁 두 종류, `hasVisibleChange`.

### 7.3 영속성·서비스·API (Testcontainers)
- V4 `validate` 통과, `SchemaConstraintTest`에 V4 CHECK·UNIQUE·FK 추가(3823 확인 포함).
- 프로필 만들기·수정: 별칭 추가·삭제 비교, 대표 닉네임 교체, 빈 목록 400, 다른 선수 별칭 409 + conflicts, 실명 중복 409(이름이 바뀔 때만), 라인 추가·수정·삭제와 로그(바뀐 것만, 현재 회차 번호), 같은 값으로 저장하면 로그 0행, 미등록 → 등록 전환, 롤백 시 별칭·티어 모두 원상태.
- 회차: 변동이 없으면 409, 시작 후 새 수정은 새 번호로 기록, 티어리스트 trend가 바뀌는지.
- 점수표: PUT 범위 검증, reset, 티어리스트의 score 반영.
- 팀 짜기 API: 오류 코드 표 전부. `RandomGenerator`를 고정 빈으로 바꿔 좌우 교체가 정해진 대로 되는지 확인한다.
- 보안: 비로그인으로 공개 3개는 200(Q1)이고 담당자 경로는 401이다. CSRF 토큰 없는 POST는 403이다(공개 POST 포함).
- 로그: 닉네임·실명이 로그에 남지 않는지(M4 §11.2-9와 같은 방식).

### 7.4 프런트 (Vitest + Testing Library)
- `teamResultModel`: 같은 팀 라인 교환, 상대 팀 교환(티어 없는 라인은 "티어 없음" + 대체 점수), 같은 칸 다시 누르면 해제, 되돌리기, 판정 세 구간, 복사 텍스트(A팀·B팀 표기).
- `CoinToss`: 난수 주입 + 가짜 타이머(1.5초) + reduced-motion이면 즉시. t1 유지, 블루 칸 = A팀 문구, 교체 후 코인 상태 지움.
- `useTeamMakerState`: localStorage 왕복, 없는 id 제거, 참가자에서 빠진 사람의 쌍 자동 제거, 클릭 순서 유지.
- `tierSort`, `TrendMark`, `TierBoard` 렌더(칸 배치·정렬), `PlayerEditor` 검증·409 문구, 점수 설정 디바운스 PUT, routes 테스트 갱신(공개·관리 새 경로, `/admin/players`가 새 화면).

## 8. 작업 분할 (같은 트리, 파일 겹침 없음)

공통 규칙:
- 백엔드는 작업자마다 `./gradlew test -PbuildDirName=build-<이름>`을 쓴다(M4에서 확인됨).
- 프런트 `npm test`는 동시에 돌려도 되지만 `npm run build`(dist)는 리드가 순서대로 돌린다.
- 계약을 바꿔야 하면 리드에게 알린다.

| 단계 | 담당 | 파일 | 확인 |
|---|---|---|---|
| **Phase 0** (먼저, 단독) | 리드 | `V4__tier_team_maker.sql`; `domain/tier/{TierSub, LaneTier, ScoreTable(DEFAULT·시그니처), TierTrend(시그니처, 본문은 UnsupportedOperationException)}`; `domain/team/{TeamMakerPlayer, Slot, TeamOption, TeamMakeResult, TeamMaker(시그니처), ScoreMatrix}`; `tier/` 엔티티 4개 + 리포지토리 + 모든 요청·응답 DTO record; `teammaker/` DTO; `common/config/SecurityConfig`(공개 matcher, Q1 확정 후), `common/config/RandomConfig`; 테스트 `TestRandomConfiguration`; 골든 생성기 실행 → `golden-v1.json` 커밋 대상(Q7에 따라 생성기 포함 여부); 프런트 `src/api/tierTypes.ts`, `queryKeys.ts`, `routes.tsx`(빈 페이지 연결), `layouts/{PublicLayout, AdminLayout}.tsx` 메뉴, `styles/theme.css`(`--t1..6`), `api/errorMessages.ts`(새 코드 문구) | `./gradlew test` 녹색(V4 validate·시드, 기존 테스트 유지), `npm run lint && npm test && npm run build` |
| **Phase 1-A** | A (도메인) | `domain/tier/**`, `domain/team/**` 구현 + 같은 패키지 테스트 + `TeamMakerGoldenTest` | TDD 녹색, 골든 전부 일치, 최악 시나리오 실행 시간 측정 기록 |
| **Phase 1-B** | B (티어·프로필 영속성) | `tier/**` 전부(서비스·컨트롤러), `player/PlayerService.java`(`replaceAliases`, 실명 중복) + `PlayerServiceTest` 추가분, `SchemaConstraintTest` V4 추가분 | A의 실제 구현 전에는 `TierTrend`·`ScoreTable` 없이 테스트할 수 없는 부분만 Phase 2로 미룬다. 통합 테스트 녹색 |
| **Phase 1-C** | C (팀 짜기 API) | `teammaker/**`(서비스·컨트롤러) + 통합·보안 테스트 | 도메인은 A 결과를 받기 전까지 스텁으로 테스트한다. 오류 코드 표 전부 |
| **Phase 1-D** | D (프런트: 티어·관리) | `src/api/tier.ts`, `src/pages/tier/**`, `src/pages/admin/players/**`, `src/pages/admin/score/**`, `src/styles/tier.css`, M3 선수 화면 파일 삭제 | lint·test |
| **Phase 1-E** | E (프런트: 팀 짜기) | `src/pages/team/**`, `src/styles/team.css`, 골든 JSON을 읽는 `teamResultModel` 테스트 | lint·test |
| **Phase 2** | 리드 | 캐시 없는 clean build, 수동 종단 점검(가상 선수 10명으로 계산 → 아티팩트에 같은 입력을 넣어 눈으로 대조 1회), AGENTS.md(§3 상태, §7 개선 후보: 확정 팀 연결·티어 추천·선수 병합), implementation-plan 데이터 모델 절(V4), ADR 011(팀 짜기 계산은 서버 + 골든 픽스처), ADR 012(라인 티어 변동 로그·명시적 회차), Codex(Astra) 리뷰 | 사용자 승인 후 커밋·PR |

의존 관계:
- 0 → (A, B, C, D, E) 병렬이다. B와 C는 A의 구현을 Phase 2에서 연결한다. 이를 위해 Phase 0에 A의 시그니처를 확정한다.
- D와 E는 `tierTypes.ts`만 있으면 된다. D와 E가 같은 컴포넌트를 써야 하면(예: 이름 정렬) `tierSort.ts`를 D가 만들고 E는 가져다 쓰기만 한다.
- 학습 노트(로컬 전용): A 16-team-maker-domain, B 17-tier-profile, C 18-team-maker-api, D 19-frontend-tier, E 20-frontend-team-maker.

## 9. 위험과 사용자에게 물어야 할 질문

### 위험
- R1 **골든 기준의 출처**: 아티팩트 파일이 이번 세션 임시 폴더(scratchpad)에 있어 사라질 수 있다. Phase 0에서 리드가 기준 발췌와 `app.js` 해시를 먼저 확보한다(Q7).
- R2 **입력 순서 의존**: 동점 처리 때문에 참가자 순서가 결과를 바꾼다. 프런트는 클릭 순서를 그대로 보내야 한다. 정렬해서 보내면 아티팩트와 달라진다. 테스트로 고정한다.
- R3 **의도한 차이**(§1.7 쌍 검증, §1.9 코인 단순화, §1.10 변동 가장자리 사례, D7 정수, 수동 모드 티어 없음 400). 정상 입력에서 계산 결과는 같다. 이 목록을 ADR 011에 적는다.
- R4 공개 POST 계산 API는 요청마다 CPU를 수십 ms 쓴다. 친구 규모에서는 문제없고 속도 제한은 M7(WAF 또는 앱)에서 다룬다.
- R5 회차는 담당자가 "새 회차 시작"을 잊으면 변동 표시가 계속 쌓인다(같은 회차 안). 버튼 옆에 "이번 회차 변동 N건"을 보여 준다.
- R6 실명 중복 검사는 DB UNIQUE가 아니라 서비스 검사라서, 동시 요청이면 빠져나갈 수 있다(담당자 1명이라 수용). 기존 데이터에 이미 중복이 있을 수 있어 UNIQUE 인덱스는 두지 않는다.
- R7 **[확인 필요]** 3823 CHECK/FK, `INT` 매핑, `UTC_TIMESTAMP(6)` 시드는 Phase 0 테스트에서 판가름 난다.
- R8 라이트 테마 티어 색은 아티팩트에 없어서 새로 정해야 한다(디자인이 아티팩트와 달라지는 유일한 곳).
- R9 미등록 부캐 → 기존 선수 별칭으로 옮기는 기능이 없다(M6 병합까지 불편).

### 사용자 확인 질문 (추천안 포함)
1. **공개 범위**: 티어리스트 보기, 점수표 보기, 팀 짜기 계산을 로그인 없이 열까요? 추천: **연다.** 아티팩트의 "보기 전용"과 같고, 실명 공개는 2026-10-16에 정했다. 수정은 담당자만이다. 닫으면 세 API를 `/api/admin/`으로 옮기고 화면도 관리 영역에만 둔다. M5 공개 화면과 메뉴를 함께 쓰게 된다. Phase 0 전에 정해야 한다.
2. **회차 운영**: 추천: **담당자가 "새 회차 시작" 버튼을 누를 때 회차가 넘어간다**(아티팩트의 "공유하기"와 같은 뜻, 변동이 없으면 막음). 대안 (a) 한국 날짜가 바뀌면 자동으로 새 회차: 버튼이 없지만 하루 두 번 고치면 묶이고, 내전 다음 날 고치면 갈린다. 대안 (b) 저장할 때마다 새 회차: 여러 명을 고치면 마지막 사람 표시만 남아 쓸모가 없다.
3. **확정 팀과 대결 연결**: 추천: **M4.5에서는 연결하지 않는다.** 결과 복사와 브라우저 저장만 한다. 대결의 A팀·B팀은 지금처럼 1세트 등록 때 정한다(유저플로우 §4.2). 연결하면 V5 `team_plan`(대결별 10명·팀·라인)을 두고, 등록 화면에서 선수 후보 우선순위와 "계획과 다름" 경고로 쓸 수 있다. AGENTS 개선 후보로 올린다.
4. **기존 선수의 라인 티어 초기값**: 추천: **비워 두고 담당자가 새 편집기로 입력한다**(약 19명, 라인 몇 개씩). 처음 입력은 변동으로 치지 않는다. 대안: 친구 아티팩트 백업 JSON에서 이름으로 맞춰 한 번 가져오는 로컬 스크립트(커밋 안 함). 실명 데이터를 다루므로 사용자가 직접 돌려야 한다. 기존 선수는 라인 0개로 저장할 수 있고, **새로 만들 때만 최소 1라인**을 요구하는 것도 함께 확인 바랍니다.
5. **코인토스와 A팀**: 추천: **블루 칸 = A팀으로 고정**하고, 코인이 "어느 묶음이 블루(A팀)인지"를 정한다. 대결 규칙(1세트 블루 = A팀)과 맞는다. 원래 문구("나온 면이 A팀의 진영")를 문자 그대로 따르면 A팀이 레드가 될 수 있어 대결 기록의 A팀과 이름이 어긋난다.
6. **선수 삭제**: 아티팩트에는 삭제가 있다. 추천: **세트 기록이 없는 선수만 삭제 허용**(잘못 만든 선수 정리용). 기록 있는 선수는 409이고 병합은 M6이다. 넣지 않으면 편집기에서 삭제 버튼을 뺀다.
7. **친구 코드 일부를 저장소에 넣어도 될까요?** 골든 생성기의 기준 구현이 아티팩트 함수(약 70줄)를 그대로 옮긴 것이다. 추천: **친구에게 허락을 받으면 `tools/team-maker-golden/`에 넣고, 아니면 git 제외 폴더에 두고 픽스처 JSON만 커밋**한다(다른 사람은 다시 생성할 수 없지만 테스트는 돈다).
8. **점수는 정수만** 받아도 될까요? 추천: 예(-999~999). 아티팩트는 소수도 받았지만 결과가 같으려면 정수가 안전하다.
9. **주력 포지션**: 아티팩트에는 없다. 추천: 편집기에 "주력 포지션(선택)"으로 남긴다(유저플로우 §3.1, 기존 데이터 유지).
10. **같은 이름 금지**: 추천: 실명이 바뀔 때 다른 선수와 같으면 409(아티팩트 규칙). M3 생성·수정 API에도 똑같이 적용한다.
11. **티어 조정 추천(아티팩트의 승률·KDA 기반 상향·하향 제안)**은 이번 범위에서 뺀다. 추천: 빼고 M5 통계 이후 개선 후보로 올린다.

## 참고 파일
- 아티팩트(읽기 전용, 외부 코드): `C:/Users/ASUS/AppData/Local/Temp/claude/C--Users-ASUS-Desktop-----------------lol-inhouse/71177d5a-375e-4642-928e-ac05eca113c4/scratchpad/friend-artifact/app.js`(2~5, 23, 78~87, 164~251, 254~324, 325~368, 371~380, 843~855, 957~971, 990~1067, 1161, 1189, 1203~1211행), 같은 폴더 `style.css`(10행 티어 색, 68~78 board, 299~313 coin)
- `C:\Users\ASUS\Desktop\지민\프로젝트\개인 프로젝트\lol-inhouse\backend\src\main\java\com\lolinhouse\player\{PlayerService,PlayerController,Player,PlayerResponse,PlayerCreateRequest,PlayerRepository}.java`
- `C:\Users\ASUS\Desktop\지민\프로젝트\개인 프로젝트\lol-inhouse\backend\src\main\java\com\lolinhouse\common\config\SecurityConfig.java`, `...\domain\Position.java`
- `C:\Users\ASUS\Desktop\지민\프로젝트\개인 프로젝트\lol-inhouse\backend\src\main\resources\db\migration\V2__core_schema.sql`, `V3__extraction_job.sql`
- `C:\Users\ASUS\Desktop\지민\프로젝트\개인 프로젝트\lol-inhouse\frontend\src\{routes.tsx,api\admin.ts,api\client.ts,api\queryKeys.ts,layouts\PublicLayout.tsx,layouts\AdminLayout.tsx,pages\admin\PlayersPage.tsx,styles\theme.css}`
- `C:\Users\ASUS\Desktop\지민\프로젝트\개인 프로젝트\lol-inhouse\docs\design\m4-design.md`(§0·§8·§12 형식), `docs\유저플로우.md` §1·§3·§4.2

참고(검토 중 발견, 범위 밖):
- AGENTS.md §3 표 안에 M0 관련 줄들이 표 행이 아닌 목록으로 끼어 있어 렌더링이 깨질 수 있다.
- implementation-plan "학습 기록" 절은 코드 주석에 "문서 링크"를 적으라고 하지만, CLAUDE.md는 `docs/study` 경로를 주석에 금지한다. 설계 문서·ADR 경로만 적는 것으로 읽었다.

## 10. 사용자 결정 (2026-10-20, 구현은 이 절이 위 내용보다 우선)

- **Q1 공개 범위**: 티어리스트·점수표 조회와 팀 짜기 계산(`/api/tier-list`, `/api/score-table`, `/api/team-maker/*`)은 **로그인 없이 공개**. 수정은 `/api/admin/**`(관리자만).
- **Q2 티어 변동**: **회차·변동 이력을 두지 않는다.** 티어는 다시 설정하기 전까지 그대로 유지된다(사용자: "티어 변동은 계속 유기적으로, 따로 기록하지 않고 한번 설정하면 다시 설정하기 전까지 유지"). 따라서 `tier_round`, `player_lane_tier_change` 테이블과 "새 회차 시작" API·버튼, `TierTrend` 회차 계산을 **설계에서 뺀다**. 대신 ▲▼ 표시는 **라인마다 마지막 변경(방향·이전 티어)을 `player_lane_tier`에 함께 저장해 다음 변경 때까지 보여 준다**(`last_change_direction` UP/DOWN NULL, `last_change_from_tier`, `last_change_from_sub` — 같은 값 저장·새 라인 추가는 변동 아님, 라인 삭제 시 행 삭제). ▲▼가 필요 없다는 사용자 의견이 오면 이 컬럼과 표시만 뺀다.
- **Q3 확정 팀과 대결 연결**: 하지 않는다(추천안). 결과 복사와 브라우저 저장만. AGENTS 개선 후보.
- **Q4 기존 선수 라인 티어 초기값**: **친구 아티팩트 데이터에서 가져온다.** 일반 도구 `tools/artifact-import/`(실명·닉네임 데이터 없음, 저장소 포함): 아티팩트 데이터 JSON(`players[{name, lanes{top|jg|mid|adc|sup:{t,s}}, nicks[]}]`, 점수표 `score`)을 읽어 우리 관리자 API(`player-profiles`, `score-table`)로 넣는다. 이름이 같은 기존 선수는 갱신, 없으면 생성, 닉네임 충돌은 건너뛰고 보고. 입력 데이터 파일은 저장소 밖(git 제외) — 데스크탑 실사용 DB에서 사용자가 실행(M5.5 안내서에 절차). 가져오기는 변동(▲▼)으로 치지 않는다. 기존 선수는 라인 0개 허용, 새로 만들 때만 최소 1라인.
- **Q5 코인토스**: 블루 칸 = A팀(추천안).
- **Q6 선수 삭제**: 세트 기록 없는 선수만 삭제(추천안), 기록 있으면 409 `PLAYER_HAS_RECORDS`.
- **Q7 친구 코드**: 친구 허락을 받았다(사용자). 골든 생성기 기준 구현(아티팩트 팀 짜기 함수 발췌)을 `tools/team-maker-golden/`에 **포함**해 누구나 골든 JSON을 다시 만들 수 있게 한다. 친구 명단 데이터(`app-data`)·아이콘 덩어리는 넣지 않는다.
- **Q8 정수 점수**, **Q9 주력 포지션 선택 항목 유지**, **Q10 같은 실명 금지(M3 API 포함)**, **Q11 티어 조정 추천 제외**: 모두 추천안.

## 11. Codex(Astra) 설계 리뷰 반영 (2026-10-20, 구현은 §10과 이 절이 최우선)

- **P1-1·P1-2·P2-3(회차 초기화, 회차와 저장의 동시성, 라인 삭제 시 회차 표시)**: §10 Q2 결정(회차·변동 이력 없음)으로 해당 설계 자체가 빠져 **해당 없음**. ▲▼는 `player_lane_tier`의 마지막 변경 컬럼만 쓴다: 라인 티어를 바꾸면 `rank` 비교로 방향을 정해 덮어쓰고, 같은 값 저장·새 라인 추가는 변동 컬럼을 바꾸지 않으며(새 라인은 NULL), 라인 삭제는 행 삭제.
- **P2-4 교체 후 슬롯 복원**: 계산 응답의 `scoreMatrix`를 `{ "<playerId>": { TOP: {score, tier|null, sub|null, hasTier}, … } }`로 확장. 옵션과 행렬은 **같은 조회 스냅샷**(한 읽기 트랜잭션에서 읽은 티어·점수표)으로 만든다.
- **P2-5 점수표 자동 저장 순서**: 프런트는 한 번에 PUT 하나만 보낸다(진행 중이면 최신 표를 대기값으로 합치고 끝난 뒤 한 번 더). "기본값으로"도 같은 순서 큐에 넣는다. 저장 중·실패·미저장 상태를 표시.
- **P2-6 교체 후 total**: 원본 `recalcResult`는 `tot`를 갱신하지 않는 결함이 있다. 우리 계약은 **교체 후 `total = blueSum + redSum`**(의도한 차이, ADR 011에 기록). 골든 비교에서 교체 시나리오의 `total`은 제외하고 손계산 테스트로 검증.
- **P2-7 공개 계산 남용**: M5.5(터널 공개) 전에 필요하므로 M4.5에서 넣는다(새 의존성 없이): 팀 짜기 계산 **동시 실행 2개**(`Semaphore`, 초과 시 503 `TEAM_MAKER_BUSY`), **접속 주소별 분당 30회**(메모리 고정 창 카운터, 초과 시 429 `RATE_LIMITED`, 터널 뒤 실제 주소 헤더는 M5.5에서 설정), `together` 최대 5쌍(초과 400 `TEAM_PAIR_INVALID`). CSRF 유지.
- **P2-8 별칭 개수**: 생성(POST)만 닉네임 1~10개. 수정(PUT)은 1~(max(10, 기존 개수))개를 허용해 기존 선수가 티어만 고쳐도 저장되게 한다. 11개 별칭 선수의 티어 수정 회귀 테스트.
- **실명 동시 중복(R6)**: 프로필 생성·수정과 M3 생성·수정 경로 모두 `score_setting`(id=1) 행을 먼저 `PESSIMISTIC_WRITE`로 잠그는 **전역 직렬화**로 동시 같은 실명을 막는다(담당자 1명이라 성능 영향 없음). 잠금 순서: score_setting → 선수 행.
- 확인: 팀 계산 규칙(126분할, PERMS 순서, evaluate 동점 처리, 수동 16가지, 안정 정렬·상위 4개, slotFor, rank)은 원본과 일치. V4 정적 검토 이상 없음(실행 검증은 Phase 0).

## 12. Phase 0 결과 (2026-10-20, 공통 기반 작업자) — A~F는 작업 전에 이 절부터 읽는다

백엔드 `./gradlew test -PbuildDirName=build-p0` 녹색(총 652개 = 기존 632 + 신규 20), 프런트 `npm run lint && npm test && npm run build` 통과(테스트 110개). 구현은 §10·§11이 최우선이라 **회차·변동 이력 테이블, `TierTrend`, 회차 API·오류 코드는 만들지 않았다.** 아래 계약은 컴파일·테스트로 고정됐다. **계약을 바꿔야 하면 혼자 고치지 말고 리드에게 알린다.**

### 12.1 골든 기준 (R1 해소)

- `tools/team-maker-golden/{reference.js, generate.js, README.md}`. `reference.js`는 `app.js` 2(LANES)·23(pts)·24(byId)·78(rank)·254~324행을 **고치지 않고** 옮기고, 전역(`data`, `team`, `ui`)을 인자로 받는 감싸개 + `Math.random` 0.99 고정만 붙였다. 머리말에 출처·`app.js` SHA-256(`74e8b571…ce3f9`)·줄 범위. 2·24행은 설계 §7.1 목록에 없지만 `pts`·`laneAssignments`·`makeTeams`가 쓰는 의존이라 함께 옮겼다.
- `node generate.js`로 `backend/src/test/resources/team-maker/golden-v1.json`(2.7MB, 시드 20261020, mulberry32)을 만든다. 두 번 돌려 바이트까지 같음을 확인했다. 시나리오 350개: 자동 200, 수동 100, 동점 30(자동 24·수동 6), 실패 20(자동 14·수동 6). 성공 322개(설계한 실패 20 외에 무작위로 막힌 8개 포함), 그중 **교체 작업 열(`swaps`) 121개**.
- JSON 모양: `meta{…}`, `scenarios[]{id, category, mode, players[{id,name,lanes{POSITION:{tier,sub}|null}}], scoreTable{base{"1".."6"},sub{HIGH..},lane{POSITION}}, participantIds|picks, together, expected{ok,options[{blue[5],red[5],blueSum,redSum,diff,gap,cost,total}]}|{ok:false,failure}, scoreMatrix, swaps{optionIndex:0, compareTotal:false, steps[{a:{side,i},b:{side,i},result}]}}`. 슬롯은 `{playerId,position,tier|null,sub|null,score}`이고 라인 순서 TOP, JUNGLE, MID, ADC, SUPPORT. 선수 id는 시나리오마다 1~10. 실패 `failure`는 `TEAM_NO_VALID_SPLIT`(자동)/`TEAM_PAIR_UNSATISFIABLE`(수동). 옵션은 좌우 교체 **전**, 최대 4개.
- `swaps.steps[].result`에는 `total`이 **없다**(§11 P2-6: 원본 `recalcResult`가 `tot`를 갱신하지 않음. 우리 계약은 `total = blueSum + redSum`이라 비교에서 뺀다). 교체 규칙은 `app.js` 1039~1050행 `res-swap` 핸들러와 같다(두 사람이 자리를 바꾸고 각자 새 라인으로 `slotFor`).
- 친구 명단·아이콘 데이터는 쓰지 않았다(가상 선수만). `app-data.json`은 열지 않았다.

### 12.2 [확인 필요] 판정

| 항목 | 판정 |
|---|---|
| V4 적용, 3823(FK 컬럼을 CHECK에 쓸 때) | **통과.** `player_lane_tier.player_id`는 `ON DELETE CASCADE` FK지만 CHECK에 쓰지 않았고 V4가 그대로 적용됐다. CHECK는 `position`·`tier`·`sub`·마지막 변경 4칸만 건다. (CHECK에 FK 컬럼을 넣는 반례를 일부러 실행해 보지는 않았다.) 선수 삭제 시 CASCADE로 라인 티어가 지워지는 것은 테스트로 확인 |
| `INT` ↔ Java `int`/`Integer` | **통과.** Hibernate `validate` 통과, 왕복 동일(`tier` 3→2, `last_change_from_tier`). `TierSub`·`Position`·`Direction`은 `@Enumerated(STRING) + @JdbcTypeCode(VARCHAR)`로 ascii_bin VARCHAR와 맞음 |
| Flyway 시드 `UTC_TIMESTAMP(6)` | **통과.** `score_setting` 1행이 들어가고 `updated_at`이 현재 UTC 시각 근처(±1시간). 앱 시작 시 삽입 같은 대안은 필요 없음 |
| V4 시드 == `ScoreTable.DEFAULT` | 통과(`TierSchemaTest`) |
| 공개 matcher | `GET /api/tier-list`·`/api/score-table`은 로그인 없이 401/403이 아님, `POST /api/team-maker/*`는 CSRF 토큰이 없으면 403이고 `csrf()`가 있으면 인가 통과, `/api/admin/player-profiles`는 비로그인 401 (`PublicRoutesSecurityTest`) |
| 주의(발견) | 이 테스트에서 `GET /api/csrf`로 쿠키를 받는 방식을 썼더니 **전체 테스트 실행에서만** 쿠키가 비어 간헐 실패했다(단독 실행·auth 패키지와 함께 실행은 통과, 원인은 다른 테스트 클래스와의 상태 간섭으로 추정, 미규명). 그래서 이 테스트는 `csrf()` 후처리기를 쓴다. 쿠키 발급 자체는 `AuthIntegrationTest`가 검증한다. C가 공개 POST 통합 테스트를 쓸 때도 `csrf()`를 쓸 것 |

### 12.3 스키마 (`V4__tier_team_maker.sql`)

- `player_lane_tier(id, player_id FK CASCADE, position, tier INT, sub, last_change_direction NULL, last_change_from_tier NULL, last_change_from_sub NULL, updated_at)`. UNIQUE `(player_id, position)`. CHECK: position, tier 1~6, sub, 방향 UP/DOWN, from_tier 1~6, from_sub, 그리고 `ck_player_lane_tier_change_all_or_none`(마지막 변경 3칸은 모두 NULL이거나 모두 값).
- `score_setting(id, base_tier1..6, sub_high/mid/low, lane_top/jungle/mid/adc/support, updated_at)`. `ck_score_setting_single`(id = 1), `ck_score_setting_range`(14칸 -999~999). 시드 1행 = 기본 점수표.
- `tier_round`, `player_lane_tier_change`는 **없다**(Q2).

### 12.4 백엔드 계약 (모두 `com.lolinhouse.` 아래)

**domain.tier** (작업자 A 영역. 아래는 Phase 0이 이미 구현하고 `TierDomainTest`로 검증한 단순 본문)
- `TierSub { HIGH("상",2), MID("중",1), LOW("하",0) }`: `label()`, `rankBonus()`.
- `record LaneTier(int tier, TierSub sub)`: 생성 시 1~6 검증(IllegalArgumentException), `rank()` = `(7-tier)*3 + rankBonus`. 상수 `MIN_TIER`, `MAX_TIER`.
- `record ScoreTable(List<Integer> base /*6칸*/, Map<TierSub,Integer> sub, Map<Position,Integer> lane)`: 14칸 필수·-999~999 검증(생성 시 IllegalArgumentException, 서비스가 400 `INVALID_SCORE_TABLE`로 바꿈). `DEFAULT`, `baseOf(int tier)`, `points(LaneTier, Position)`, `fallbackPoints(Position)`(6티어 하). 상수 `MIN_VALUE`, `MAX_VALUE`.
- `record LastChange(Direction direction /*UP|DOWN*/, LaneTier from)` + **`static LastChange afterSave(LaneTier previous, LaneTier next, LastChange existing)`**: previous가 null(새 라인)이면 null, 같은 값이면 existing 그대로, 다르면 `next.rank() > previous.rank()`로 방향을 정해 새 값. 반환 null = 표시할 변경 없음. (`TierTrend`는 없다.)

**domain.team** (작업자 A가 `TeamMaker`를 구현)
- `record TeamMakerPlayer(long id, Map<Position,LaneTier> lanes)` + `tierOf(Position)`(없으면 null).
- `record Slot(long playerId, Position position, LaneTier tier /*null=티어 없음*/, int score)`.
- `record TeamOption(List<Slot> blue, List<Slot> red, int blueSum, int redSum, int diff, int gap, int cost, int total)` + `swapSides()`(blue/red와 합계만 맞바꿈).
- `record TogetherPair(long first, long second)`: 자기 자신이면 예외, 작은 id가 first(순서 무관 동치).
- `record TeamMakeResult(List<TeamOption> options, Failure failure)`: `enum Failure { NO_VALID_SPLIT, PAIR_UNSATISFIABLE }`, `success(options)`, `failure(Failure)`, `isSuccess()`.
- **`TeamMaker.auto(List<TeamMakerPlayer> ordered, Set<TogetherPair> together, ScoreTable table) → TeamMakeResult`**, **`TeamMaker.manual(Map<Position,List<TeamMakerPlayer>> picks, Set<TogetherPair> together, ScoreTable table) → TeamMakeResult`**. 본문은 `UnsupportedOperationException`("Phase 1-A에서 구현"). `MAX_OPTIONS = 4`. 무작위를 쓰지 않고 좌우 교체 전 상위 4개를 돌려준다.
- `record ScoreMatrix(Map<Long,Map<Position,Cell>> cells)`, `Cell(int score, LaneTier tier)` + `hasTier()`, `static ScoreMatrix.of(Collection<TeamMakerPlayer>, ScoreTable)`(티어 없는 칸은 대체 점수) — 구현 완료.

**tier** (작업자 B 영역)
- 엔티티 `PlayerLaneTier`: `static create(Long playerId, Position, LaneTier)`(마지막 변경 없음), `laneTier()`, `lastChange()`(null 가능), `changeTo(LaneTier)`(내부에서 `LastChange.afterSave` 적용, 같은 값이면 마지막 변경 유지), `updatedAt()`은 `@PrePersist/@PreUpdate`가 채움. 리포지토리 `PlayerLaneTierRepository`: `findByPlayerId(Long)`, `findByPlayerIdIn(Collection<Long>)`.
- 엔티티 `ScoreSetting`(`SINGLE_ID = 1`): `toTable()`, `apply(ScoreTable)`. 리포지토리 `ScoreSettingRepository.findForUpdate()` = `PESSIMISTIC_WRITE`, `where id = 1`. **전역 직렬화 잠금 순서: score_setting, 그다음 선수 행**(§11). 반드시 트랜잭션 안에서.
- DTO(record): `ScoreTableResponse(Map<Integer,Integer> base, Map<TierSub,Integer> sub, Map<Position,Integer> lane, boolean isDefault)` + `from(ScoreTable)`, `ScoreTableRequest(base, sub, lane)`(값 null 가능 → 서비스가 400), `TierListResponse(ScoreTableResponse scoreTable, List<TierPlayer> players)`(`TierPlayer(Long id, String displayName, Map<Position,LaneView|null> lanes)`, `LaneView(int tier, TierSub sub, int score, Trend trend)`, `Trend(Direction direction, int fromTier, TierSub fromSub)`), `PlayerProfileRequest(String realName, Position mainPosition, Map<Position,LaneTierInput> lanes, List<String> nicknames)`(`LaneTierInput(Integer tier, TierSub sub)`; 형태 검증만, 규칙 검증은 서비스), `PlayerProfileResponse(Long id, String displayName, String realName, boolean registered, Position mainPosition, List<AliasResponse> aliases, Map<Position,LaneValue|null> lanes)`(`LaneValue(int tier, TierSub sub)`, `AliasResponse`는 `PlayerResponse.AliasResponse` 재사용).
- 서비스·컨트롤러는 B의 몫: `TierListService`, `ScoreSettingService`, `PlayerProfileService`(+ `PlayerService.replaceAliases`, 실명 중복 검사), `TierListController`(공개 GET 2개), `ScoreSettingAdminController`(PUT, reset), `PlayerProfileController`(GET/POST/PUT/DELETE).

**teammaker** (작업자 C 영역): DTO `TeamMakerAutoRequest(List<Long> participantIds, List<List<Long>> together)`, `TeamMakerManualRequest(Map<Position,List<Long>> picks, List<List<Long>> together)`, `TeamMakerResponse(List<Option> options, Map<Long,Map<Position,MatrixCell>> scoreMatrix)`(`Option`, `SlotView(long playerId, Position position, Integer tier, TierSub sub, int score)`, `MatrixCell(int score, Integer tier, TierSub sub, boolean hasTier)`). `TeamMakerService`, `TeamMakerController`, 남용 방지(`Semaphore` 2개 → 503 `TEAM_MAKER_BUSY`, 접속 주소별 분당 30회 고정 창 → 429 `RATE_LIMITED`, `together` 최대 5쌍)는 C가 만든다.

**common**
- `common.config.RandomConfig`: `RandomGenerator` 빈(`RandomGenerator.getDefault()`). 서비스는 후보마다 `nextBoolean()` 한 번 → true면 `TeamOption.swapSides()`.
- 테스트 `com.lolinhouse.TestRandomConfiguration`: `@Primary` `FixedRandom`(기본 `nextBoolean()` = false). `setBooleans(boolean...)`로 순서 지정, `reset()`. `@IntegrationTest`에 이미 `@Import`됨. 쓴 테스트는 `@AfterEach`에서 `reset()`.
- `common.web.TierTeamErrorCodes`: 새 오류 코드 16개 상수(아래). `ApiException`에 `unprocessable(code, detail)`(422)·`tooManyRequests(code, detail)`(429) 팩토리를 추가했다(503은 기존 `serviceUnavailable`).
- `SecurityConfig`: 공개 matcher만 추가(`GET /api/tier-list`, `GET /api/score-table`, `POST /api/team-maker/auto|manual`). CSRF 유지.

**새 오류 코드(§4.3에서 회차 코드 제거, §11 추가)**

| 코드 | 상태 | 경우 |
|---|---|---|
| `INVALID_LANE_TIER` | 400 | 티어 1~6 밖, sub 오류, 모르는 라인 |
| `LANE_TIER_REQUIRED` | 400 | 만들 때 라인 0개 |
| `NICKNAME_REQUIRED` | 400 | 닉네임 목록이 빔 |
| `DUPLICATE_REAL_NAME` | 409 | 다른 선수와 실명 동일 |
| `PLAYER_HAS_RECORDS` | 409 | 기록 있는 선수 삭제 |
| `INVALID_SCORE_TABLE` | 400 | 칸 누락·범위 밖·정수 아님 |
| `TEAM_PARTICIPANT_COUNT` | 400 | 자동 10명 아님(`count`) |
| `TEAM_DUPLICATE_PARTICIPANT` | 400 | 같은 사람 두 번(수동은 `playerId`) |
| `TEAM_LANE_PICK_INCOMPLETE` | 400 | 수동에서 라인이 2명이 아님(`position`) |
| `TEAM_LANE_TIER_MISSING` | 400 | 수동에서 그 라인 티어 없는 사람 |
| `TEAM_PLAYER_NOT_FOUND` | 400 | 없는 선수 id |
| `TEAM_PAIR_INVALID` | 400 | 쌍에 비참가자, 자기 자신, 5쌍 초과 |
| `TEAM_NO_VALID_SPLIT` | 422 | 자동 결과 0개 |
| `TEAM_PAIR_UNSATISFIABLE` | 422 | 수동 결과 0개 |
| `TEAM_MAKER_BUSY` | 503 | 동시 계산 한도 초과 |
| `RATE_LIMITED` | 429 | 분당 30회 초과 |

### 12.5 프런트 계약 (`frontend/src/` 아래)

- `api/tierTypes.ts`: `TierSub`·`TIER_SUB_LABEL`·`Tier`, `ScoreTable`/`ScoreTableResponse`/`ScoreTableRequest`, `TierListResponse`(`TierPlayer`, `LaneView`, `Trend`), `PlayerProfile`/`PlayerProfileRequest`/`LaneValue`, `TeamMakerAutoRequest`/`TeamMakerManualRequest`/`TeamMakerResponse`(`TeamOption`, `SlotView`, `MatrixCell`, `ScoreMatrix`), `MAX_TOGETHER_PAIRS`, `SCORE_MIN/MAX`, `TIER_TEAM_ERROR_CODES`. `Position`은 기존 `api/types.ts`.
- `api/queryKeys.ts`: `TIER_LIST_KEY = ['tier-list']`, `SCORE_TABLE_KEY = ['score-table']`(둘 다 공개라 `['admin']` 아래가 아님), `PLAYER_PROFILES_KEY = ['admin','player-profiles']`. 로그아웃 시 `PLAYER_PROFILES_KEY`도 지운다.
- `routes.tsx`: 공개 `/tiers` → `pages/tier/TierListPage`, `/team-maker` → `pages/team/TeamMakerPage`. 관리 `/admin/players` → `pages/admin/players/PlayerTierAdminPage`, `/admin/score` → `pages/admin/score/ScoreSettingsPage`. 모두 자리표시자(`PlaceholderPage`)이고 **D·E는 해당 파일의 내용만 바꾸고 `routes.tsx`는 건드리지 않는다.** 임시 경로 `/admin/players-legacy` → 기존 `PlayersPage`(M3)를 남겼다: `PlayersPage.test.tsx` 전체와 `auth.test.tsx` 한 곳이 이 경로를 쓴다. **D가 `PlayersPage` 파일들을 지울 때 이 줄과 테스트도 함께 정리한다.**
- `layouts`: 공개 메뉴에 "티어리스트"(`/tiers`)·"팀 짜기"(`/team-maker`), 담당자 메뉴에 "점수 설정"(`/admin/score`).
- `styles/theme.css`: `--t1..--t6`. 다크 = 아티팩트 값(#e6bb62 #ae8ef2 #58b9e4 #63c391 #93a0b8 #6c768b), 라이트 = #8a6200 #6f45c6 #16709b #1a7a4c #4d5b78 #66708a(흰 패널 위 글자 대비 4.9~6.8). 라이트 t6만 `--bg`(#f1f2f6) 위에서 4.42로 4.5에 약간 모자란다(패널 위는 4.94). 배경 색조로만 쓰면 문제없고 글자색으로 쓰면 D가 확인한다.
- `api/errorMessages.ts`: 새 코드 16개 문구(`errorMessages.test.ts`가 모든 코드에 전용 문구가 있는지 검사). `count`·`position` 같은 확장 필드는 `ApiError`가 보존하지 않아 문구에 넣지 않았다.

### 12.6 작업자별 바로 쓸 목록

- **A (도메인)**: `domain/team/TeamMaker.auto/manual` 본문 + 같은 패키지 테스트(§7.2). 골든 `backend/src/test/resources/team-maker/golden-v1.json`을 읽는 `TeamMakerGoldenTest`(성공은 옵션 목록 전체, 실패는 `failure` 문자열과 `Failure` 대응, `ScoreMatrix.of` 결과와 `scoreMatrix` 일치). JSON의 숫자 id는 `TeamMakerPlayer.id`로, `together`는 `TogetherPair`로 바꿔 넣는다. `domain/tier`는 이미 단순 본문이 있으니 사례를 더하기만 한다(`TierDomainTest`). 최악 시나리오(전원 5라인 티어) 실행 시간 측정 기록.
- **B (티어·프로필)**: `tier/**` 서비스·컨트롤러, `PlayerService.replaceAliases`·실명 중복, 전역 직렬화(`ScoreSettingRepository.findForUpdate()` 먼저), 마지막 변경은 `PlayerLaneTier.changeTo`에 맡김(같은 값 저장·새 라인은 유지/NULL, 삭제는 행 삭제). `TierSchemaTest`에 V4 항목을 더해도 된다. 별칭 개수: POST 1~10, PUT 1~max(10, 기존 개수).
- **C (팀 짜기 API)**: `teammaker/**` 서비스·컨트롤러·남용 방지·오류 코드 표 전부. A의 구현 전에는 서비스의 입력 검증을 먼저 테스트한다. 공개 POST 통합 테스트는 `csrf()` 후처리기와 `TestRandomConfiguration.FixedRandom`을 쓴다.
- **D (프런트 티어·관리)**: `src/api/tier.ts`, `pages/tier/**`(`tierSort.ts` 포함 — E가 가져다 씀), `pages/admin/players/PlayerTierAdminPage.tsx` + `PlayerEditor`, `pages/admin/score/ScoreSettingsPage.tsx`, `styles/tier.css`, M3 선수 화면 파일 삭제와 `/admin/players-legacy` 정리, 라이트 `--t6` 글자 대비 확인.
- **E (프런트 팀 짜기)**: `pages/team/**`, `styles/team.css`, `teamResultModel`의 골든 대조 테스트(`swaps.steps`를 Vitest가 읽음; `total`은 `blueSum + redSum`으로 직접 계산해 검증), `CoinToss`.
- **F (가져오기 도구, Q4)**: `tools/artifact-import/`. 입력 `players[{name, lanes{top|jg|mid|adc|sup:{t,s}}, nicks[]}]`와 점수표 `score` → `POST/PUT /api/admin/player-profiles`, `PUT /api/admin/score-table`. 변환: 라인 `top/jg/mid/adc/sup` → `TOP/JUNGLE/MID/ADC/SUPPORT`, `t` → `tier`, `s` 상/중/하 → `HIGH/MID/LOW`, 점수표 `base`(1~6)·`sub`(상/중/하)·`lane`도 같은 대응. 닉네임 충돌(409 `ALIAS_CONFLICT`)은 건너뛰고 보고. 입력 데이터 파일은 저장소 밖, 테스트 픽스처는 가상 이름만.
  - **결정 필요(리드)**: Q4는 "가져오기는 변동(▲▼)으로 치지 않는다"인데, 현재 계약에서는 기존 선수의 티어가 달라지면 `changeTo`가 ▲▼를 남긴다. 해결안 (1) `PlayerProfileRequest`에 선택 필드 `recordChange`(기본 true)를 더해 도구가 false로 보낸다, (2) 도구가 신규 생성만 하고 기존 선수는 보고만 한다. Phase 0에서는 임의로 정하지 않았다.

## 13. 아티팩트 새 버전 반영 (2026-10-21, 구현은 이 절이 최우선)

친구 아티팩트를 다시 열어 보니 `app.js`가 바뀌었다(SHA-256 `f7194a45…57dd94`, 이전 `74e8b571…ce3f9`). 팀 짜기 계산에서 달라진 곳은 **자동 모드의 라인 고정** 하나이고, 나머지(분할·순열·evaluate·수동 모드·slotFor·recalcResult·rank·기본 점수표)는 그대로다. 사용자 결정(2026-10-21): 라인 고정과 요주 인물은 M4.5에 넣고, **티어 조정 추천은 M5 이후**(경기 기록이 쌓인 뒤)로 미룬다. 밴픽·결과 올리기·백업·기록 코드는 §10대로 범위 밖이다.

### 13.1 라인 고정 (자동 모드)

새 `app.js` 규칙:
- 참가자마다 [자동 / 탑 / 정글 / 미드 / 원딜 / 서폿] 중 하나를 고를 수 있다(`team.fix[id] = laneKey`). 수동 모드에는 없다.
- 라인 배정(`laneAssignments`)에서 고정한 사람은 **고정한 라인에만** 놓인다. 고정한 라인에 티어가 없어도 놓일 수 있고, 그때 점수는 **6티어 하 점수**(`ScoreTable.fallbackPoints`), 슬롯의 티어는 null이다. 고정하지 않은 사람은 지금처럼 티어가 있는 라인에만 놓인다.
- 참가자 중 같은 라인에 고정한 사람이 **3명 이상**이면 계산 전에 막는다: "`{라인}`에 `{n}`명이 고정돼 있어요. 한 라인은 양 팀 합쳐 2명까지만 고정할 수 있어요." 여러 라인이 넘치면 `LANES` 순서(탑→서폿)로 처음 넘친 라인을 알린다.
- 결과가 0개이고 고정이 하나라도 있으면 문구가 다르다: "라인 고정 때문에 가능한 조합이 없어요. 고정한 라인 때문에 다른 사람들이 갈 라인이 모자라요. 고정을 하나씩 풀어 보세요."
- 결과 칸에서 고정한 사람의 라인 표기 뒤에 "· 고정"(accent 색)을 붙인다. 티어 없는 칸은 "티어 없음"(`--warn`).
- 참가자에서 빠진 사람의 고정은 계산에 쓰지 않는다(아티팩트는 값을 남겨 두고 무시한다. 우리 프런트는 요청에 참가자 것만 담는다).

우리 계약:
- 도메인: `TeamMaker.auto(List<TeamMakerPlayer> ordered, Set<TogetherPair> together, ScoreTable table, Map<Long, Position> fixedLanes)`. 기존 3인자 `auto`는 빈 맵으로 위임한다. 라인당 3명 이상 검증은 서비스 몫이고, 도메인은 받은 대로 계산한다.
- 요청: `TeamMakerAutoRequest(List<Long> participantIds, List<List<Long>> together, Map<Long, Position> fixedLanes)`. `fixedLanes`는 생략·null·빈 맵 = 고정 없음. 키가 참가자가 아니면 400 `TEAM_LANE_FIX_INVALID`("라인 고정에 참가자가 아닌 사람이 있어요."), 값이 null이면 그 키는 고정 없음으로 본다. 라인당 3명 이상이면 400 `TEAM_LANE_FIX_INVALID`(위 문구, `position`·`count` 포함).
- 실패: 고정이 있고 결과 0개면 422 `TEAM_FIXED_LANE_BLOCKED`(위 문구). 고정이 없으면 지금처럼 `TEAM_NO_VALID_SPLIT`.
- 골든: `reference.js`를 새 `app.js`에서 다시 옮기고(새 줄 번호·해시), 생성기는 기존 350개 시나리오를 **같은 순서·같은 난수로** 먼저 만든 뒤 `fix` 시나리오를 뒤에 덧붙인다. 파일 이름은 `golden-v2.json`(v1은 지운다). 시나리오에 `fixedLanes{ "<id>": "POSITION" }`를 담는다(자동만). 고정 시나리오에는 성공·실패(`TEAM_FIXED_LANE_BLOCKED`)·라인당 2명 고정·티어 없는 라인 고정이 모두 들어가야 한다. 라인당 3명 이상은 서비스 검증이라 골든에 넣지 않는다.

### 13.2 요주 인물 표시

- 새 `app.js`: 선수에 `bee: true`를 둘 수 있고, 편집기에 "요주 인물로 표시" 체크박스가 있다. 체크한 사람은 티어리스트(보드·표)와 팀 짜기 참가자 카드에서 이름 뒤에 벌 아이콘(24px, 금색 그림자)이 붙는다. 계산에는 영향이 없다.
- 스키마: `V5__player_flagged.sql`로 `player.flagged BOOLEAN NOT NULL DEFAULT FALSE`를 더한다(V4는 로컬 DB에 이미 적용됐을 수 있어 고치지 않는다).
- API: `PlayerProfileRequest.flagged`(Boolean, 생략·null이면 생성은 false, 수정은 기존 값 유지), `PlayerProfileResponse.flagged`(boolean), `TierListResponse.TierPlayer.flagged`(boolean). M3 API(`/api/admin/players/**`)는 건드리지 않는다.
- 아이콘: 아티팩트의 `BEE_IMG`를 `frontend/src/assets/bee.webp`로 옮겼다(친구 허락 범위). `alt`/`title`은 "요주 인물".
- 가져오기 도구: 아티팩트 선수의 `bee: true` → `flagged: true`. 기존 선수 갱신 때도 아티팩트 값으로 덮는다.

### 13.3 미루는 것

- 티어 조정 추천(라인별 3경기 이상, 승률 (w+2)/(g+4)와 KDA/라인 평균으로 한 칸 상·하향 추천, 적용·넘기기). M5에서 경기 기록이 쌓인 뒤 검토한다(AGENTS.md §7).
