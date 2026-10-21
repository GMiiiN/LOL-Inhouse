# M5 설계: 공개 조회(홈·내전 기록·대결/세트 상세·선수·랭킹·챔피언 통계·CSV)

> 작성: architecture-planner(Opus), 2026-10-21. 리뷰 전 초안이다.
> 버전에 따라 동작이 달라질 수 있는 부분은 **[확인 필요]**로 표시했다.
> 기준: `docs/implementation-plan.md` M5 행, `docs/유저플로우.md` §1·§8·§9·§11·§12, 기능명세서 §2·§3·§5, `docs/design/m45-design.md`(§10~§13), M2 `domain.stats`. 친구 아티팩트 `app.js`의 `computeStats`·`computeChampStats`·`statBody`·`matchRow`·`renderStatDetail`·`renderStats`·`renderChampStats`(50~77행, 383~457행)와 `style.css` 183~229행·315~326행은 기능과 디자인 참고로만 읽었다. 459행(ICONS)과 `app-data.json`은 열지 않았다. 와이어프레임 링크는 열 수 없어서 유저플로우와 명세를 기준으로 삼았다.

기존 문서와 비교해 미리 알려 둘 점:
- **원본 결과창 이미지의 공개 여부는 이미 정해져 있다.** 유저플로우 §7.3("원본은 담당자만 열람"), §8.4("일반 방문자가 원본 이미지를 열람하는 경로는 제공하지 않는다"), §12-2, 기능명세서 §3-4·§5-3이 모두 "담당자만"이다. 따라서 이 설계는 **공개 화면에서 원본을 보여 주지 않는 것**을 기본으로 한다. 남은 질문은 "담당자용 원본 보기를 M5에 넣을지"뿐이다(Q1).
- **스키마는 바꾸지 않는다(V6 없음).** 필요한 인덱스(`game_set_player.player_id`, `game_set(duel_id, …)`, `duel(session_id, seq)`, `inhouse_session.start_date`)가 이미 있다.
- 챔피언 이름과 아이콘을 주는 `GET /api/admin/champions`는 담당자 전용이다. 공개 화면도 이 목록이 필요하므로 **같은 응답을 주는 공개 경로**를 새로 연다(D9).

## 0. 결정 요약

| # | 결정 | 이유 |
|---|---|---|
| D1 | 통계는 **조회할 때마다 서버에서 계산**하고 저장하지 않는다. M2 `domain.stats`(`StatSummary`, `PlayerStats`, `RankingCalculator`, `Kda`)를 그대로 쓴다 | 구현 계획의 원칙("점수·피어리스·통계는 저장하지 않고 조회 시 계산")과 같다. 데이터가 작아서(§2.3) 비용이 없고, 수정·삭제(M6)가 들어와도 따로 갱신할 일이 생기지 않는다 |
| D2 | DB는 **프로젝션 쿼리 몇 번으로 통째로 읽고** Java 메모리에서 묶는다. 엔티티의 지연 로딩(`GameSet.players`)은 공개 조회에서 쓰지 않는다 | N+1을 구조적으로 막는다. 요청 하나의 쿼리 수가 세트 수와 관계없이 일정하고, 테스트로 고정한다(§6.3) |
| D3 | 캐시는 두지 않는다(Spring Cache·Redis 모두 없음). 프런트는 TanStack Query 기본 동작을 쓴다 | 근거 없는 기술 금지. 저장하면 바로 반영되어야 한다(기능명세 §5-1). 다시 볼 기준은 §2.3에 적었다 |
| D4 | 공개 응답은 **정수 합계와 서버가 판정한 값**을 함께 보낸다: `sets, wins, losses, winRate, kda{kills, deaths, assists, ratio(null=PERFECT)}, cs, gold`. 평균(경기당 K/D/A·CS·골드)과 % 표기는 프런트가 이 합계를 나눠서 만든다 | 규칙(승률 정의, PERFECT 판정, 랭킹 정렬)은 서버 하나에 둔다(M4 D11). 화면 표기만 프런트가 맡는다 |
| D5 | 기간은 **내전 시작일(`inhouse_session.start_date`) 기준**으로 거르고, "최근" 순서는 **시작일 → 대결 seq → 세트 번호** 내림차순으로 정한다. `shot_date`(결과창 날짜)는 쓰지 않는다 | 자정을 넘긴 경기도 시작일 묶음에 속한다(유저플로우 §1, §8.1 "날짜 범위는 내전 시작일 기준", §12-3) |
| D6 | **원본 이미지는 공개 응답 어디에도 넣지 않는다.** `imageKey`·`imageHash`·`fingerprint`도 공개 응답에서 뺀다. 담당자용 보기는 Q1에 따른다(추천: `GET /api/admin/sets/{id}/image`) | 문서 결정(§7.3·§8.4). 결과창에는 친구 닉네임과 캡처한 사람의 화면이 그대로 담겨 있다 |
| D7 | 응답에 `Instant`를 넣지 않는다. 날짜는 `LocalDate`(`"2026-10-12"`)만 쓴다 | M4 D15, M4.5 D14와 같다. M3 응답(`InhouseSessionSummaryResponse.startDate`)이 이미 `LocalDate` 직렬화로 동작하고 있다 |
| D8 | 화면에 보이는 이름은 `Player.displayName()`(등록 선수는 실명, 미등록 선수는 게임 닉네임)이다. **별칭 목록은 공개하지 않는다** | 실명 공개는 2026-10-16에 정했다. 미등록 선수가 닉네임으로 보이는 것도 유저플로우 §3.1에서 정했다. 부계정 목록까지 공개할 이유는 없다 |
| D9 | `GET /api/champions`(공개)를 새로 연다. 응답은 `ChampionListResponse`를 그대로 쓴다. 관리용 경로는 그대로 둔다 | Data Dragon 공개 데이터다. 공개 응답마다 이름·아이콘을 넣으면 응답이 커지고 중복된다. 카탈로그가 없으면(503) 프런트는 챔피언 키를 글자로 보여 준다 |
| D10 | 패키지는 `record`(내전 기록·대결·세트·CSV)와 `stats`(선수 목록·프로필·랭킹·챔피언 통계)로 나눈다. 의존 방향은 `stats → record → {session, player, tier, champion, domain}` 한쪽뿐이다 | 패키지 순환을 막는다(M4 R12, M4.5 D9). 공통 로더는 `record`에 하나만 둔다 |
| D11 | 내전 기록 목록은 **묶음 단위 커서 페이지**(`before=시작일`, 기본 10묶음)로 나눈다. 각 묶음에는 대결과 세트 줄을 **펼침 내용(10명·밴 10개)까지 담아** 보낸다 | 펼칠 때 추가 요청이 없어 바로 열린다. 한 페이지는 약 100KB 이하로 추정한다(§2.3). 대안 비교는 §1.2에 있다 |
| D12 | 피어리스 제한은 **세트별로** "이 세트 시점의 제한"(이어가기 체인에서 앞선 세트들의 픽)을 계산해 보여 준다. 대결 상세에는 "다음 세트 기준 제한"을 이전 대결에서 이어받은 것과 이번 대결에서 픽된 것으로 나눠 보여 준다. 밴과는 다른 칸에 둔다 | 유저플로우 §8.1 "피어리스 제한은 드래프트 밴 목록과 구분해서 표시". 계산은 기존 `FearlessChain.chainDuelIds`를 메모리 데이터에 그대로 쓴다 |
| D13 | 대결은 **세트 점수(A n : m B)만** 보여 주고 "대결 승리"라고 단정하지 않는다 | 대결 종료를 확정하는 동작이 아직 정해지지 않았다(유저플로우 §11-8) |
| D14 | 팀 표기는 A팀/B팀이고 진영은 세트마다 블루/레드로 따로 표시한다(예: "A팀 · 블루"). 세트 펼침은 아티팩트 `matchRow`처럼 **블루 왼쪽, 라인 가운데, 레드 오른쪽**이다 | 2026-10-20 표기 결정, 아티팩트 디자인과 같다 |
| D15 | 화면 상태(기간·선수·챔피언·탭·정렬)는 **URL 검색 파라미터**에 둔다(`/records?player=3&champion=Ahri`) | 링크를 공유할 수 있다. 프로필의 "챔피언 선택 → 그 챔피언 경기 목록"(유저플로우 §8.2)을 새 화면 없이 `/records` 필터 링크로 처리한다 |
| D16 | 새 의존성은 없다(백엔드·프런트 모두). 차트 라이브러리도 쓰지 않는다. 막대는 아티팩트처럼 CSS `barw`로 그린다 | 근거 없는 기술 금지 |
| D17 | "요주 인물"(벌) 표시는 M5 화면에 넣지 않는다. 티어리스트·팀 짜기에만 둔다 | 아티팩트도 전적 탭에는 표시하지 않았다. 반대 의견이 있으면 응답에 `flagged`를 더하기만 하면 된다 |

## 1. 화면 목록

모든 화면은 `PublicLayout` 안에 있고 로그인 없이 볼 수 있다. 다크/라이트는 `theme.css` 토큰만 쓴다. 아티팩트 CSS의 `--surface`는 우리 토큰에 없으므로 `--panel2`로 대응시킨다. **[확인 필요]** M4.5 `tier.css`에서 쓴 대응과 같은지 Phase 0에서 확인한다.

### 1.1 홈 `/` (Q3)
추천 구성(별도 API 없이 기존 공개 API를 재사용):
1. 요약 줄: 총 세트 수, 기록이 있는 선수 수, 마지막 내전 날짜. 랭킹 응답의 `totalSets`, 기록 응답의 첫 묶음을 쓴다.
2. **최근 내전**: `GET /api/records?limit=1`의 묶음 하나를 내전 기록 화면과 같은 컴포넌트로 보여 준다(대결 점수와 세트 줄, 펼치기 가능).
3. **랭킹 상위 5명**(전체 탭, 승률 기준, 순위가 있는 사람만)과 "전체 랭킹" 링크.
4. 바로가기: 팀 짜기, 티어리스트, 내전 기록.
- 기록이 하나도 없으면 "아직 기록된 경기가 없어요." 하나만 보여 준다.

### 1.2 내전 기록 `/records`
- 계층은 **묶음(시작일) → 대결 N(피어리스 방식, A n : m B) → 세트 한 줄 → 펼치기**다.
- **세트 한 줄**: "N세트", A팀 진영(블루/레드), 승리 팀 태그(아티팩트 `wtag`, "A팀 승"), 경기 시간 mm:ss, 양 팀 픽 챔피언 아이콘 5개씩(라인 순), 팀 킬 합계(결과창 표시값) "23 : 17", 펼침 버튼(`aria-expanded`), 세트 상세 링크.
- **펼침**(아티팩트 `matchRow`의 `mt` 표): 라인마다 한 줄씩 5줄이다. 블루 칸은 `KDA · CS · 골드 · 챔피언 · 이름`, 가운데는 라인 이름, 레드 칸은 그 반대 순서다. 이름을 누르면 프로필로 간다. 그 아래에 **밴 줄**을 B1 R1 B2 R2 B3 R3 / B4 R4 B5 R5 순서로 두고, 블루와 레드 색을 구분하며 순번 라벨을 단다. 그 아래에 **피어리스 제한** 줄을 "이 세트 시점 제한 N개"로 접어 둔다(D12).
- **필터**(Q6): 기간(from~to), 선수, 챔피언(픽 또는 밴). 조건에 맞는 세트가 있는 대결과 묶음만 남는다. **대결 점수는 걸러진 세트가 아니라 그 대결의 유효 세트 전체로 계산**하고, "조건에 맞는 세트 2/4"를 함께 표시한다. 결과가 없으면 빈 결과 안내와 "조건 지우기" 버튼을 보여 준다(유저플로우 §8.4).
- 맨 아래 "더 보기"는 다음 10묶음을 불러온다(`useInfiniteQuery`).
- 오른쪽 위 구석에 작은 **CSV** 버튼을 둔다(§5). 현재 필터가 링크에 그대로 실린다.

목록과 펼침의 대안 비교:

| 방식 | 장점 | 단점 |
|---|---|---|
| **A. 묶음 페이지에 펼침 내용까지 포함(추천)** | 바로 펼쳐진다. 요청은 페이지당 1번이다. 세트 상세 화면도 같은 `SetView` 모양을 쓴다 | 페이지가 약 100KB다(10묶음 × 6세트 × 약 1.5KB) |
| B. 줄만 보내고 펼칠 때 `/api/sets/{id}` | 첫 응답이 작다 | 펼칠 때마다 기다려야 하고 요청이 늘어난다. 로딩·오류 상태가 세트마다 생긴다 |
| C. 페이지 없이 전체 | 가장 단순하다 | 1년이면 1MB 이상이라 점점 느려진다 |

### 1.3 대결 상세 `/duels/:duelId`
- 머리: 묶음 날짜, "대결 N", 피어리스 방식(초기화 / 이어가기), **A n : m B**(앞선 팀은 강조만 하고 "승리"라고 쓰지 않는다, D13).
- 세트 목록: 1.2의 세트 줄을 그대로 쓰고 기본은 펼친 상태다.
- **대결별 개인 결과**: 이 대결에 뛴 모든 선수의 이름, 뛴 세트 수, 세트 승·패, 개인 결과(승/무/패, `PlayerStats.duelResult`)를 보여 준다. 맞라인 교환이 있으면 한 선수가 대결 안에서 A팀과 B팀을 모두 뛸 수 있다. 그래서 팀은 "A팀"·"B팀"·"A→B"처럼 **뛴 팀 목록**으로 표시한다(§6.1 교환 규칙).
- **피어리스 제한(다음 세트 기준)**: "이전 대결에서 이어온 제한"(CONTINUE 체인)과 "이 대결에서 픽된 챔피언"을 두 묶음의 아이콘으로 보여 준다. RESET 대결은 앞 묶음이 비어 있다.

### 1.4 세트 상세 `/sets/:setId`
- 머리: 묶음 날짜 · 대결 N · N세트, 결과창 날짜, 경기 시간, A팀 진영, 승리 팀, 팀 합계(결과창 표시값 K/D/A·골드).
- 본문은 1.2 펼침과 같고 더 넓게 보여 준다(CS·골드 열 포함). 밴 10개와 피어리스 제한을 구분해서 둔다(유저플로우 §8.1).
- 링크: 대결 상세, 선수 프로필.
- **원본 결과창 이미지**: 방문자에게는 버튼조차 없다(D6). 담당자로 로그인했을 때만(`useAdminMe`) "원본 보기" 버튼을 보여 주고, 누르면 `GET /api/admin/sets/{id}/image`를 blob으로 받아 확대해 보여 준다(Q1 추천안). 수동 입력 세트(이미지 없음)에는 버튼이 없다. 404를 받으면 "원본 없음"이다.
- 삭제된 세트(소프트 삭제)는 공개 404다.

### 1.5 선수 목록 `/players`
- 아티팩트 "개인 전적"의 `statgrid` 카드 격자를 따른다. 카드에는 이름("미등록" 배지 가능), "N경기 W승 · 승률 %", **KDA 큰 숫자**(PERFECT는 글자로), 경기당 K/D/A, 평균 CS와 골드(`kv`), 많이 쓴 챔피언 5개와 비율 막대("40% · 4판"), 그 밖의 챔피언 수, 라인 칩("미드 6 · 원딜 3")이 들어간다. 카드를 누르면 프로필로 간다(아티팩트의 그 자리 펼침 대신 라우트로 이동).
- 정렬: 경기 수 내림차순, 같으면 이름을 `Intl.Collator('ko')`로 정렬한다(아티팩트와 같음, M4.5 `tierSort.ts` 재사용).
- 대상: 기간 안에 1세트 이상 뛴 선수 전원(미등록 포함, Q7)과 기록이 없는 등록 선수(맨 뒤 "기록 없음" 접힘).
- 기간 필터(Q6).

### 1.6 선수 프로필 `/players/:playerId`
유저플로우 §8.2의 항목 전부에 아티팩트 `renderStatDetail`을 더한다:
- 머리: 표시 이름, 주력 포지션, **라인 티어**(M4.5 데이터 `player_lane_tier`: "탑 2티어 상 ▲"처럼, 기존 `TrendMark` 재사용. 티어가 없는 라인은 "—").
- 요약(`summary`): 참여 세트, 세트 승패, 승률, 누적 KDA(PERFECT), 경기당 K/D/A, 평균 CS·골드, **경기 참여율** = 내 세트 수 ÷ 같은 기간 전체 유효 세트 수(`PlayerStats.participationRate`), 간 라인 수.
- **라인별 기록**(`lanegrid`·`lanecard`): 경기 수가 많은 라인부터 보여 준다. 카드마다 "N경기 W승 · 승률", KDA, 평균, 그 라인의 챔피언 상위 5개가 들어간다.
- **챔피언별**: 전부를 사용 횟수순(같으면 키 순, `PlayerStats.byChampion`)으로 보여 준다. 열은 아이콘·이름, 판 수(표본), 승·패, 승률, KDA다. 행을 누르면 `/records?player={id}&champion={key}`로 간다.
- **대결별 개인 결과**: 합계 "N승 N무 N패"와 최근 대결 목록(묶음 날짜, 대결 N, 내 세트 승·패, 결과). 누르면 대결 상세로 간다.
- **최근 경기**: 최근 5세트를 1.2 세트 줄 컴포넌트로 보여 주고 본인 행을 강조한다. "전체 보기"는 `/records?player={id}`다.
- 기록이 없는 선수: 머리와 라인 티어만 보여 주고 "아직 기록된 경기가 없어요."를 낸다. 없는 id는 404 화면이다.

### 1.7 랭킹 `/ranking`
- 탭: 전체·탑·정글·미드·원딜·서포터(`seg`). 정렬: 승률(기본)·KDA·경기 수. 기간 필터.
- 열: 순위, 이름, 경기 수, 승패, 승률, KDA.
- **최소 5세트**(`RankingCalculator.DEFAULT_MIN_SETS`). 포지션 탭은 그 포지션으로 뛴 세트가 5개 이상이어야 한다. 미달 선수는 목록 아래에 순위 "—"로 기록만 보여 준다(서버가 이미 뒤에 붙여서 준다). 표 위에 "5세트 미만은 순위 없이 기록만 보여요."를 적는다.
- 동률은 공동 순위(1,1,3)다. PERFECT는 정렬에서 데스를 1로 계산하지만 화면에는 "PERFECT"로 표시한다(이미 구현됨).
- 행을 누르면 프로필로 간다.

### 1.8 챔피언 통계 `/champions` (새 라우트, Q9)
- 아티팩트 `renderChampStats`와 같다. 열은 챔피언(아이콘+이름), **밴픽률**((픽+밴) ÷ 전체 세트, 막대 `pbbar`), 픽, 밴, 승률(`wgood` 50% 이상 / `wbad`), "W승 L패"다. 정렬 `seg`는 밴픽률(기본)·픽·밴·승률이고, 같으면 밴픽 수, 그다음 한국어 이름순이다. 기본 15개를 보여 주고 "전체 N개 보기"를 둔다.
- 머리에 "총 N경기"를 쓴다. 우리는 밴 10개가 필수라 아티팩트의 "밴 기록이 있는 경기는 NB경기" 문구가 필요 없다.
- 도움말: "밴픽률은 전체 경기 중 그 챔피언이 밴이나 픽된 비율이에요."
- 행을 누르면 `/records?champion={key}`로 간다. 기간 필터를 둔다.

### 1.9 아티팩트 "전적" 탭 기능 대응표

| 아티팩트 | 우리 M5 | 차이 |
|---|---|---|
| 개인 전적 카드 격자(`renderStats`, `statgrid`) | `/players` 카드 격자 | 카드를 누르면 그 자리 펼침 대신 프로필 라우트로 간다 |
| 카드 KDA = (K+A)/max(D,1) 숫자 | 누적 KDA, 데스 0이면 **PERFECT** | 의도한 차이(유저플로우 §8.2) |
| 경기당 K/D/A, 평균 CS·골드(`statBody`) | 같음(합계 ÷ 세트, 프런트 계산) | 없음 |
| 챔피언 상위 5개와 비율 막대 | 같음 | 없음 |
| 라인 칩 | 같음 | 없음 |
| 상세: 요약, 라인별 카드, 최근 경기 5개(`renderStatDetail`) | 프로필 §1.6 | 참여율·대결별 결과·챔피언별 표·라인 티어를 더했다(유저플로우 §8.2) |
| 정렬: 경기 수 → 이름 ko | 같음 | 없음 |
| 챔피언 통계(`renderChampStats`) | `/champions` | "밴 기록 있는 경기" 구분은 필요 없다(밴 10개 필수) |
| 최근 경기 5개(`matchRow`: 블루·라인·레드) | 홈 최근 내전과 `/records` | 세트 줄 + 펼치기, 밴과 피어리스 제한을 더했다 |
| 닉네임 → 사람 연결(`resolveEntry`, 조회 시 매칭) | 저장할 때 `player_id`로 확정됨 | 조회할 때 매칭하지 않는다 |
| 진행 중인 경기(pending), 결과 올리기, 지난 경기 입력, 경기 삭제 | 범위 밖(M4.5 Q3, M6) | 옮기지 않는다 |
| 티어 조정 추천(`computeRecs`) | 범위 밖(Q5) | 이번에 만드는 라인별 통계를 나중에 재사용한다 |

## 2. 통계 계산 위치와 방식

### 2.1 공통 로더 `record.RecordReader` (Phase 0에서 구현)
읽기 전용 트랜잭션(`@Transactional(readOnly = true)`) 하나 안에서 아래 프로젝션 쿼리만 쓴다. InnoDB REPEATABLE READ의 일관 읽기라서, 같은 트랜잭션의 여러 쿼리는 같은 스냅샷을 본다.

| 쿼리 | 모양(JPQL 생성자 표현식 → record) | 쓰는 곳 |
|---|---|---|
| Q-S 세트 행 | `SetRow(setId, duelId, setNo, team1Side, winnerTeam, shotDate, durationSec, team1Totals 4개, team2Totals 4개, duelSeq, fearlessMode, sessionId, startDate)`. `GameSet g`, `Duel d`, `InhouseSession s`를 엔티티 조인한다(`join Duel d on d.id = g.duelId`). 조건: `g.deletedAt is null` + 기간 | 전부 |
| Q-P 선수 행 | `PlayerRow(setId, playerId, team, position, championKey, k, d, a, cs, gold)`. 세트 id IN 또는 기간 조건 | 전부 |
| Q-B 밴 행 | `BanRow(setId, turn, side, championKey)` | 기록·세트·챔피언 통계·CSV |
| Q-N 이름 | `PlayerName(id, gameNickname, realName, mainPosition)`. 필요한 id만 IN | 전부 |
| Q-D 대결 | 묶음 id IN에 대해 `Duel` 전부(체인 계산용) | 기록·대결·세트 |

- 내전 기록 페이지는 ① 조건에 맞는 세트의 `(setId, sessionId, startDate)`만 가볍게 읽고, ② 묶음을 시작일 내림차순으로 페이지에 자른 뒤, ③ 그 묶음들의 세트·선수·밴·대결·이름을 IN으로 읽는다. 쿼리는 **고정 6~7번**이다.
- 통계(목록·프로필·랭킹·챔피언)는 기간 안의 Q-S + Q-P를 통째로 읽어 `List<PlayerSetRecord>`로 바꾼다. `playerId`·`duelId`·`setId`는 `String.valueOf(Long)`, `win = (team == winnerTeam)`이다. 그다음 M2 함수에 넣는다. 쿼리는 **고정 3~4번**이다.
- **[확인 필요]** Hibernate 7(Boot 4.1.1)에서 엔티티 조인(`join Duel d on …`)과 record 생성자 표현식이 되는지. Hibernate 6 이상에서 지원하는 문법이다. Phase 0 통합 테스트로 확인하고, 안 되면 `@Query(nativeQuery = true)` + 인터페이스 프로젝션으로 바꾼다.

### 2.2 M2 도메인 재사용과 새로 필요한 순수 함수
- 그대로 쓰는 것: `StatSummary.of`, `PlayerStats.overall/byPosition/byChampion/participationRate/duelResult`, `RankingCalculator.overall/byPosition(records, 5, key)`, `Kda.isPerfect/ratio`, `DuelPersonalResult.result`, `FearlessChain.chainDuelIds`.
- 새로 만드는 것(TDD, 작업자 A):
  - `domain.stats.PlayTotals(long cs, long gold)` + `static of(Collection<PlayerSetRecord>)`. `StatSummary`는 바꾸지 않는다. record 생성자가 바뀌면 M2 테스트가 깨지기 때문이다.
  - `domain.stats.ChampionPickBan(String champion, int picks, int bans, int wins, int losses, int setsWithPickOrBan)` + `static List<ChampionPickBan> compute(List<PlayerSetRecord> picks, List<BannedChampion> bans)`(§10-2로 갱신). 승패는 픽한 쪽 결과다. 정렬은 하지 않는다(프런트가 seg로 정렬).
  - `domain.stats.TopChampions.of(records, limit)` → `[(champion, sets)]`. `byChampion` 결과를 자르면 되므로 따로 만들 필요가 없을 수 있다. A가 판단한다.
- 순수 계산이지만 화면 조립에 속하는 것은 `record.RecordViews`(패키지 전용, DB 없이 단위 테스트)에 둔다:
  - 대결 점수: 유효 세트의 `winnerTeam` 개수.
  - 세트 시점 피어리스 제한: 체인 대결 id(오래된 순)에서 **(앞선 대결의 모든 세트) ∪ (같은 대결의 더 작은 setNo 세트)**의 픽 집합을 구한다. 정렬은 키 오름차순이다.
  - 대결 상세의 "이어온 제한 / 이 대결 픽" 나누기.
  - 필터: 선수(그 세트에 뛰었는지), 챔피언(픽 또는 밴).

### 2.3 데이터 규모와 캐시
- 가정: 21명, 주 2회 내전 × 약 8세트 → 연 약 830세트이다. `game_set_player`·`game_set_ban`은 각각 연 약 8.3천 행, 3년이면 약 2.5만 행이다.
- 통계 요청 하나가 2.5만 행 × 10열을 읽는다. MySQL에서 수십 ms, Java 그룹 계산은 ms 단위로 추정한다. 응답 크기는 랭킹 약 5KB, 프로필 약 15KB, 기록 페이지 약 100KB다.
- **캐시는 넣지 않는다.** 다시 볼 기준: 실측 p95가 300ms를 넘거나 `game_set_player`가 10만 행을 넘을 때다. 그때 첫 후보는 "마지막 세트 변경 시각을 키로 삼는 메모리 캐시 하나"이고 Redis가 아니다.
- `game_set.deleted_at`에는 인덱스가 없어 전체를 훑는다. 수천 행이라 문제가 없다.
- Spring Security 기본 헤더(`Cache-Control: no-cache, no-store`)를 그대로 둔다. 저장하면 바로 반영된다.

## 3. 공개 API

모두 `GET`이고 로그인이 필요 없다. 오류는 기존대로 ProblemDetail + `code`다. 쿼리 파라미터: `from`·`to`(YYYY-MM-DD, 선택, 시작일 기준, 양끝 포함), `playerId`, `champion`(챔피언 키), `position`(ALL|TOP|…), `sort`(WIN_RATE|KDA|SETS), `before`(YYYY-MM-DD), `limit`(1~20, 기본 10).

### 3.1 공통 모양 (TS 표기)
```ts
SummaryView = { sets, wins, losses, winRate /*0~1*/, kda: { kills, deaths, assists, ratio: number|null /*null=PERFECT 또는 sets=0*/ }, cs, gold }
PlayerRef   = { playerId, displayName, registered }
PlayerLine  = PlayerRef & { position, championKey, kills, deaths, assists, cs, gold }
TeamView    = { team: 'TEAM1'|'TEAM2', side: 'BLUE'|'RED', won, displayed: { kills, deaths, assists, gold }, players: PlayerLine[5] /*TOP→SUPPORT*/ }
SetView     = { setId, setNo, duelId, duelSeq, sessionId, sessionDate, shotDate, durationSec, team1Side, winnerTeam,
                teams: [TeamView /*TEAM1*/, TeamView /*TEAM2*/], bans: { turn, side, championKey }[10], fearlessRestricted: string[] }
DuelHead    = { duelId, seq, fearlessMode, score: { TEAM1: number, TEAM2: number }, totalSets }
```

### 3.2 엔드포인트

| 경로 | 응답 | 비고 |
|---|---|---|
| `GET /api/records?from&to&playerId&champion&before&limit` | `{ sessions: [{ sessionId, startDate, duels: [DuelHead & { sets: SetView[] /*조건에 맞는 것만*/ }] }], nextBefore: string\|null }` | 묶음은 시작일 내림차순, 대결은 seq, 세트는 setNo 오름차순. 점수와 `totalSets`는 필터와 관계없는 전체 값 |
| `GET /api/duels/{id}` | `DuelHead & { sessionId, sessionDate, sets: SetView[], personal: [PlayerRef & { teams: ('TEAM1'\|'TEAM2')[], wins, losses, result: 'WIN'\|'DRAW'\|'LOSS' }], fearless: { inherited: string[], picked: string[] } }` | 404 `DUEL_NOT_FOUND`(기존 코드) |
| `GET /api/sets/{id}` | `SetView` | 삭제된 세트·없는 세트는 404 `SET_NOT_FOUND`(새 코드) |
| `GET /api/players?from&to` | `{ totalSets, players: [PlayerRef & { mainPosition, summary: SummaryView, positions: { [Position]: number }, topChampions: { championKey, sets }[≤5], championCount }] }` | 기록 없는 등록 선수는 `summary.sets = 0` |
| `GET /api/players/{id}?from&to` | `PlayerRef & { mainPosition, laneTiers: { [Position]: { tier, sub, trend } \| null }, totalSets, participationRate, summary, byPosition: [{ position, summary, topChampions }], byChampion: [{ championKey, summary }], duels: { wins, draws, losses, items: [{ duelId, sessionDate, seq, wins, losses, result }] }, recentSets: SetView[≤5] }` | 404 `PLAYER_NOT_FOUND`(기존). `laneTiers`는 M4.5 `TierListResponse`의 `LaneView`·`Trend` 모양에서 score를 뺀 것 |
| `GET /api/rankings?position&sort&from&to` | `{ position, sort, minSets: 5, totalSets, entries: [PlayerRef & { rank: number\|null, summary }] }` | `rank` null = 표본 미달. 순서는 서버 정렬 그대로 |
| `GET /api/champion-stats?from&to` | `{ totalSets, champions: [{ championKey, picks, bans, wins, losses }] }` | 정렬은 프런트 |
| `GET /api/champions` | 기존 `ChampionListResponse` | 카탈로그가 없으면 503 `CHAMPION_CATALOG_UNAVAILABLE`(기존) |
| `GET /api/records/export?from&to&playerId&champion` | `text/csv` | §5 |
| (Q1) `GET /api/admin/sets/{id}/image` | 이미지 바이트(`image/png`·`image/jpeg`), `Cache-Control: private, no-store` | 담당자만. 이미지가 없거나 파일이 없으면 404 `SET_IMAGE_NOT_FOUND` |

새 오류 코드: `SET_NOT_FOUND`(404), `SET_IMAGE_NOT_FOUND`(404), `INVALID_QUERY`(400: from > to, limit 범위 밖, 모르는 position·sort 값). 형식이 틀린 날짜나 enum은 `ResponseEntityExceptionHandler`의 타입 불일치 처리로 400이 나는지 **[확인 필요]**. 기존 `ApiExceptionHandler` 115행 경로에서 code가 `BAD_REQUEST`로 나갈 수 있으므로, 필요하면 `INVALID_QUERY`로 바꾸는 처리기를 하나 더한다.

### 3.3 보안·개인정보
- `SecurityConfig`에 `GET` permitAll을 추가한다: `/api/records`, `/api/records/export`, `/api/duels/*`, `/api/sets/*`, `/api/players`, `/api/players/*`, `/api/rankings`, `/api/champion-stats`, `/api/champions`. `/api/admin/**` 규칙보다 위에 두되 경로가 겹치지 않는다. 같은 경로의 다른 메서드는 `anyRequest().denyAll()`로 막힌다. 클래스 주석의 "그 밖의 공개 조회 API는 M5에서 연다"도 갱신한다.
- 공개 응답에 넣지 않는 것: 이미지 키·해시, 지문, `top_team`(누가 캡처했는지 드러난다), `screen_row`, 별칭 목록, `created_at`·`updated_at`·`deleted_at`, 추출 작업 정보.
- 실명 공개는 정해진 사항이다(AGENTS §7: 외부 공개 시 "내전 코드"는 개선 후보).
- 공개 GET에는 호출 제한이 없다. M5.5에서 터널로 공개할 때의 위험은 §8 R2에 있다.

### 3.4 백엔드 클래스 (Phase 0에서 시그니처 고정)
- `record`: `RecordReader`(프로젝션 record `SetRow`·`PlayerRow`·`BanRow`·`PlayerName` + 메서드 `setsInPeriod(from, to)`, `playersOfSets(ids)`, `bansOfSets(ids)`, `playerRecords(from, to) → LoadedRecords(List<PlayerSetRecord>, Map<Long, PlayerName>, int totalSets)`, `duelsOfSessions(ids)`), `RecordViews`(순수 조립), `RecordFilter(from, to, playerId, champion)`, DTO(`SetView`, `TeamView`, `PlayerLine`, `DuelHead`, `RecordPageResponse`, `DuelDetailResponse`), `RecordService`·`RecordController`, `SetImageAdminController`(Q1), 하위 패키지 `record.export`(`RecordCsvWriter`, `RecordExportController`).
- `stats`: `SummaryView`(+ `from(StatSummary, PlayTotals)`), DTO(`PlayerListResponse`, `PlayerRecordResponse`, `RankingResponse`, `ChampionStatsResponse`), `PlayerRecordService`(`tier.PlayerProfileService`와 이름이 겹치지 않게 함), `RankingService`, `ChampionStatsService`, `StatsController`.
- `champion`: `PublicChampionController`(`GET /api/champions`, 기존 컨트롤러와 같은 본문).
- `common.web`: `PublicReadErrorCodes`(위 3개).

## 4. 프런트 구조

### 4.1 라우트 (`routes.tsx`, Phase 0)
`/` HomePage, `/records` RecordsPage, `/duels/:duelId` DuelDetailPage, `/sets/:setId` SetDetailPage, `/players` PlayersPage, `/players/:playerId` PlayerProfilePage, `/ranking` RankingPage, **`/champions` ChampionStatsPage(새 경로)**. `PublicLayout` 메뉴는 홈 · 랭킹 · 선수 · 챔피언 · 내전 기록 · 티어리스트 · 팀 짜기 순서다.

### 4.2 파일
- Phase 0(리드):
  - `api/publicTypes.ts`(§3 계약), `api/public.ts`(fetch 함수, `apiRequest` 재사용), `api/queryKeys.ts` 추가분.
  - `components/stats/`: `ChampionIcon.tsx`(공개 카탈로그 훅 `usePublicChampions`, 없으면 키 글자, `ci sm`), `KdaText.tsx`(`ratio` null이고 sets > 0이면 PERFECT, sets = 0이면 "—", 숫자는 소수 2자리), `WinRate.tsx`(반올림 %, `wgood`/`wbad`), `TeamLabel.tsx`("A팀 · 블루").
  - `pages/stats/format.ts`(mm:ss, 경기당 평균 1자리, 골드 천 단위, %), `pages/stats/usePeriodParams.ts`(URL `from`·`to` 읽기·쓰기), `components/stats/PeriodFilter.tsx`.
  - 각 페이지 파일의 자리표시자, `api/errorMessages.ts`(새 코드 3개).
  - **E와 F가 함께 쓰는 것은 모두 Phase 0에 둔다.**
- E(기록): `components/record/SetRow.tsx`(한 줄 + 펼치기), `components/record/SetScoreboard.tsx`(블루·라인·레드 표), `components/record/BanStrip.tsx`(B1~R5), `components/record/FearlessStrip.tsx`, `components/record/DuelBlock.tsx`, `pages/records/{RecordsPage, RecordFilters, CsvLink}.tsx`, `pages/duel/DuelDetailPage.tsx`, `pages/set/{SetDetailPage, OriginalImageButton}.tsx`, `api/adminImage.ts`(blob 받기 + 기존 `useObjectUrl` 재사용), `styles/records.css`(아티팩트 `match`·`mh`·`mt`·`wtag` 이식).
  - **우선순위**: `SetRow`와 `DuelBlock`을 먼저 끝낸다. F(홈·프로필)가 가져다 쓴다.
- F(통계): `pages/home/HomePage.tsx`, `pages/players/{PlayersPage, PlayerCard, StatBody}.tsx`, `pages/players/PlayerProfilePage.tsx`(+ `LaneCards`, `ChampionTable`, `DuelResultList`), `pages/ranking/RankingPage.tsx`, `pages/champions/{ChampionStatsPage, championSort.ts}.tsx`, `styles/stats.css`(아티팩트 `statgrid`·`stat`·`kda`·`kv`·`champs`·`champ`·`lanegrid`·`lanecard`·`chs`·`pbbar` 이식).
  - `SetRow`·`DuelBlock`은 E의 것을 가져다 쓴다. E가 끝나기 전에는 자리만 두고 Phase 2에서 연결한다.
  - `TrendMark`·`tierSort`는 M4.5 것을 재사용한다.

### 4.3 쿼리 키 (모두 `['public', …]`, 로그아웃해도 지우지 않는다)
`['public','records', filters]`(infinite), `['public','duel', id]`, `['public','set', id]`, `['public','players', period]`, `['public','player', id, period]`, `['public','rankings', position, sort, period]`, `['public','champion-stats', period]`, `['public','champions']`(staleTime 길게).
- 담당자가 세트를 저장하거나 선수 프로필을 저장하면 `['public']` 전체를 무효화한다. 이 두 줄은 M4·M4.5 파일(`SetRegisterPage`, `PlayerTierAdminPage`)을 고치는 일이라 **Phase 2 리드**가 맡는다.
- **[확인 필요]** `queryClient.ts`의 기본 staleTime. 0이면 화면에 들어올 때마다 다시 받아서 무효화가 없어도 맞다.

### 4.4 디자인
- 토큰만 쓴다. 블루·레드는 `--blue`/`--red`(+soft), 승패 배경은 `--win`/`--lose`, KDA 강조는 `--accent`다. `--k5/--k4/--k3`의 쓰임새는 **[확인 필요]**(와이어프레임 KDA 색 구간으로 보인다. 쓰임새를 확인하지 못하면 쓰지 않는다).
- 라이트 테마는 아티팩트에 없으므로 이식한 규칙에서 하드코딩된 색(`#c99d4f`, `rgba(217,179,106,…)`)을 `--accent`·`--accent-soft`·`--accent-line`으로 바꾼다.
- 표가 넓으므로(`mt` 최소 620px) `scroll` 감싸개를 쓴다. 모바일은 지원하지 않지만 좁은 창에서 깨지지 않을 정도는 맞춘다.

## 5. CSV

- 경로: `GET /api/records/export?from&to&playerId&champion`. 필터는 기록 화면과 같은 `RecordFilter`를 쓴다(보이는 대로 받는다, Q4).
- **행 = 세트 × 선수**(세트당 10행). 각 행에 그 세트의 밴 10개 열을 반복해서 넣는다(파일 하나, 엑셀 피벗이 쉬움). 대안은 Q4에 있다.
- 열(한국어 머리글): 내전 시작일, 대결, 대결 ID, 세트, 세트 ID, 결과창 날짜, 경기 시간(초), 승리 팀(A/B), 선수 ID, 선수, 등록 여부(등록/미등록), 팀(A/B), 진영(블루/레드), 포지션(탑…서포터), 챔피언, 챔피언 키, 킬, 데스, 어시스트, CS, 골드, 결과(승/패), B1, R1, B2, R2, B3, R3, B4, R4, B5, R5(챔피언 한글 이름, 카탈로그가 없으면 키).
- 행 순서: 시작일 → 대결 seq → 세트 번호 오름차순, 그 안에서 A팀 → B팀, 탑 → 서포터.
- 형식: **UTF-8 + BOM(EF BB BF)**(엑셀 한글 깨짐 방지), 줄 끝은 CRLF(RFC 4180). 쉼표·큰따옴표·줄바꿈이 든 값은 큰따옴표로 감싸고 `"`는 `""`로 쓴다.
- **수식 주입 방지**: 글자 열(선수, 챔피언)의 값이 `= + - @ \t \r`로 시작하면 앞에 `'`를 붙인다. 숫자 열은 건드리지 않는다.
- 응답 헤더: `Content-Type: text/csv; charset=UTF-8`, `Content-Disposition: attachment; filename="lol-inhouse-records-YYYYMMDD.csv"`(ASCII 이름, 날짜는 Asia/Seoul).
- 크기: 연 약 8천 행 × 약 250B = 약 2MB라 메모리에서 문자열로 만든다. 스트리밍은 필요 없다.
- 프런트: 기록 화면 오른쪽 위의 작은 `<a href="/api/records/export?…" download>CSV</a>`. 쿠키가 필요 없는 GET이다.
- 알려진 한계(도움말에 한 줄):
  - ① 엑셀은 숫자로만 된 긴 닉네임(예: `2630005513053`)을 숫자로 바꿔 지수 표기로 보여 준다. CSV만으로는 깔끔히 막을 수 없다.
  - ② 그래서 "18:14" 같은 시간 대신 초(정수)로 내보낸다. 엑셀이 "18:14"를 18시 14분으로 읽기 때문이다.

## 6. 테스트 계획

### 6.1 도메인 TDD (작업자 A, 손으로 계산한 작은 사례부터)
- `PlayTotals`: 합계, 빈 목록 0.
- `ChampionPickBan`: 픽·밴 각각 세기, 픽한 쪽 승패, 픽만 있는 챔피언과 밴만 있는 챔피언, 같은 챔피언의 여러 세트.
- 기존 사례 재확인(새 테스트는 필요 없음): 1승 1패 = DRAW·50%(§12-14), 포지션별 분리(§12-15), PERFECT 정렬, 최소 5세트.

### 6.2 조립 단위 테스트 (`RecordViews`, `RecordCsvWriter`: DB 없음)
- 대결 점수.
- 세트 시점 피어리스 제한: 1세트는 빈 목록, 3세트는 앞 두 세트의 20개(§12-13), CONTINUE 체인은 앞 대결 포함, RESET은 끊김, 삭제 세트 제외.
- 필터(선수, 챔피언 픽, 챔피언 밴).
- CSV: BOM 첫 3바이트, CRLF, 따옴표 처리, `=SUM(…)`으로 시작하는 닉네임에 `'` 붙음, 머리글 순서, 밴 열 순서, 카탈로그 없을 때 키로 대체.

### 6.3 통합 (Testcontainers, 공통 픽스처 `PublicReadFixtures`는 Phase 0)
- **삭제된 세트가 모든 곳에서 빠지는지**: 기록, 대결 점수, 세트 404, 선수·프로필·랭킹·챔피언 통계, 피어리스 제한, CSV(§12-16의 조회 부분).
- 자정 이후 세트가 시작일 묶음과 기간 필터에 들어가는지(§12-3).
- 미등록 선수는 닉네임으로 보이고, 실명 등록 후 과거 기록도 실명으로 보이는지(§12-7).
- 랭킹: 5세트 미만은 순위 null로 뒤에 놓이는지, 포지션 탭, 정렬 세 가지, 공동 순위.
- 참여율 분모 = 기간 전체 유효 세트.
- 맞라인 교환 대결에서 한 선수의 `teams`가 2개이고 개인 결과가 맞는지.
- 커서 페이지(`nextBefore`), 필터가 걸려도 대결 점수는 전체 값인지.
- **N+1 방지**: Hibernate Statistics `prepareStatementCount`가 세트 3개와 30개에서 같은지(기록 페이지·프로필·랭킹). **[확인 필요]** 테스트 설정에서 `hibernate.generate_statistics=true`를 켜는 방법.
- 응답 JSON에 `imageKey`·`imageHash`·`fingerprint`·`topTeam`·`createdAt` 키가 없는지(직렬화 결과 문자열 검사).
- 보안:
  - 비로그인으로 공개 GET 9개가 401·403이 아닌지.
  - 같은 경로의 POST는 거부되는지.
  - `/api/admin/sets/{id}/image`는 비로그인 401, 로그인 시 바이트와 `Content-Type`, 이미지가 없으면 404인지.
  - 기존 `PublicRoutesSecurityTest` 방식(`csrf()` 후처리기, m45 §12.2 주의)을 따른다.
- `INVALID_QUERY`(from > to, limit 0·21), 형식이 틀린 날짜 400.
- 로그: 실명·닉네임이 로그에 남지 않는지(M4 §11.2-9 방식).

### 6.4 프런트 (Vitest + Testing Library)
- `format.ts`, `KdaText`(PERFECT, sets = 0이면 "—", 소수 2자리), `WinRate`.
- `SetRow`: 펼치기·접기(`aria-expanded`), 블루가 왼쪽(A팀이 레드인 짝수 세트 포함), 밴 순서 라벨 B1~R5, 피어리스 줄.
- `RecordsPage`: 필터가 URL에 반영되는지, "더 보기", 빈 결과와 조건 지우기, CSV 링크 href에 필터가 있는지.
- `RankingPage`: 탭·정렬 변경 시 쿼리, 순위 없는 행의 "—".
- `ChampionStatsPage`: seg 정렬 4가지, 15개와 전체 보기, 같은 값의 정렬 순서.
- `PlayerProfilePage`: 라인 카드 순서, 챔피언 행 링크(`/records?player&champion`), 기록 없음 상태.
- `HomePage`: 빈 상태. `OriginalImageButton`: 담당자일 때만 보이는지.
- `routes.test.tsx`: 새 경로 `/champions`와 자리표시자 제거.

### 6.5 수동 종단 (Phase 2)
가상 데이터(묶음 2개, 대결 3개, CONTINUE 하나, 세트 8개, 미등록 1명, 맞라인 교환 1회, 삭제 세트 1개)로 모든 화면을 다크/라이트에서 확인한다. CSV를 엑셀에서 열어 한글을 확인한다. 와이어프레임은 사용자가 대조한다.

## 7. 작업 분할 (같은 트리, 파일 겹침 없음)

공통 규칙: 백엔드는 `./gradlew test -PbuildDirName=build-<이름>`, 프런트 `npm run build`는 리드가 순서대로 돌린다. 계약을 바꿔야 하면 리드에게 알린다. 학습 노트 번호는 리드가 `docs/study/README.md`의 다음 번호부터 나눠 준다(로컬 전용).

| 단계 | 담당 | 파일 | 확인 |
|---|---|---|---|
| **Phase 0**(먼저, 단독) | 리드 | `record/RecordReader.java`(**쿼리 구현 포함**) + 프로젝션 record, `record/RecordFilter.java`, `record` 모든 DTO, `stats` 모든 DTO와 `SummaryView`, `domain/stats/{PlayTotals, ChampionPickBan}` 시그니처(본문은 `UnsupportedOperationException`), `common/web/PublicReadErrorCodes.java`, `common/config/SecurityConfig.java`(공개 matcher), 테스트 `PublicReadFixtures`·`RecordReaderTest`(엔티티 조인 [확인 필요] 판정, 쿼리 수), 프런트 `api/{publicTypes, public}.ts`, `queryKeys.ts`, `routes.tsx`, `layouts/PublicLayout.tsx`, `components/stats/**`, `pages/stats/{format, usePeriodParams}.ts`, 페이지 자리표시자, `errorMessages.ts` | `./gradlew test` 녹색, `npm run lint && npm test && npm run build` |
| **1-A** | A(도메인) | `domain/stats/{PlayTotals, ChampionPickBan}` 구현과 테스트 | TDD 녹색 |
| **1-B** | B(기록 API) | `record/{RecordViews, RecordService, RecordController, SetImageAdminController}.java`, `champion/PublicChampionController.java` + 테스트 | §6.2(조립)·§6.3 해당분 |
| **1-C** | C(통계 API) | `stats/**`(서비스·컨트롤러) + 테스트 | A 구현 전에는 `PlayTotals`·`ChampionPickBan`만 스텁. §6.3 해당분 |
| **1-D** | D(CSV) | `record/export/**` + 테스트 | §6.2 CSV, 통합 1개 |
| **1-E** | E(프런트 기록) | §4.2 E 목록, `styles/records.css` | lint·test. `SetRow`·`DuelBlock` 먼저 |
| **1-F** | F(프런트 통계) | §4.2 F 목록, `styles/stats.css` | lint·test |
| **Phase 2** | 리드 | A↔C 연결 확인, F에 `SetRow` 연결, `SetRegisterPage`·`PlayerTierAdminPage`에 `['public']` 무효화 한 줄씩, 캐시 없는 clean build, §6.5 수동 점검, AGENTS.md(§3 M5 행, §7: 공개 GET 호출 제한·티어 조정 추천·캐시 기준), implementation-plan M5 결과, ADR 013(공개 조회 = 조회 시 계산·프로젝션 로더·원본 비공개·CSV 형식), Codex(Astra) 리뷰 | 사용자 승인 후 커밋·PR |

- 의존: 0 → (A, B, C, D, E, F) 병렬. B·D는 같은 `record` 패키지지만 D는 `record.export` 하위 패키지만 만지므로 파일이 겹치지 않는다. `RecordViews`의 필터 함수를 D도 쓰므로 Phase 0에 `RecordFilter.matches(...)`를 구현까지 해 둔다.

## 8. 위험과 사용자에게 물어야 할 질문

### 위험
- R1 **[확인 필요]** Hibernate 7의 엔티티 조인과 record 생성자 표현식. Phase 0에서 판정하고, 안 되면 네이티브 쿼리로 바꾼다(로더 안에서만 바뀐다).
- R2 공개 GET(특히 통계·CSV)에는 호출 제한이 없다. M5.5에서 터널로 열면 누구나 반복 호출할 수 있다. 요청 하나가 수십 ms라 친구 규모에서는 괜찮지만, 터널 단(Cloudflare 등)에서 제한하거나 팀 짜기의 `TeamMakerGuard` 방식으로 넓히는 일을 M5.5 체크리스트에 넣는다. 그 전에 M5.5의 `X-Forwarded-For` 처리가 먼저다.
- R3 `Kda.ZERO.isPerfect()`가 true다. 세트가 0인 요약이 "PERFECT"로 보이는 함정이 있다. 서버는 sets = 0이면 `ratio: null`, 프런트는 sets를 먼저 보고 "—"로 쓴다(테스트로 고정).
- R4 이름 변경(미등록 → 실명)이 과거 기록 전체에 바로 반영된다. 의도한 동작(§12-7)이지만, 공유한 링크나 CSV 스냅샷과는 이름이 달라질 수 있다.
- R5 표시 합계(결과창 값)와 선수 합계가 다를 수 있다. 저장할 때 경고만 했기 때문이다. 세트 줄의 킬 점수는 결과창 표시값을 쓰고, 개인 통계는 선수 행을 쓴다. 숫자가 어긋나 보이면 M6 수정으로 고친다.
- R6 챔피언 카탈로그가 비어 있을 때(첫 기동, Data Dragon 실패) 공개 화면은 키 글자로 대체한다. 방문자 브라우저가 Riot CDN에서 아이콘을 직접 받는다(M4와 같은 구조).
- R7 엑셀이 긴 숫자 닉네임을 바꾸는 CSV 한계(§5).
- R8 와이어프레임을 직접 열 수 없어서 유저플로우·명세·아티팩트로 대신했다. 화면 배치는 사용자 대조가 필요하다.

### 사용자 확인 질문 (추천안 포함)
1. **원본 결과창 이미지**: 문서(유저플로우 §7.3·§8.4, 명세 §3-4)에 이미 "담당자만"으로 정해져 있다. 추천: **방문자에게는 보여 주지 않는다**(친구 닉네임과 캡처한 사람의 화면이 노출되므로). 그리고 **담당자가 로그인했을 때 세트 상세에 "원본 보기"를 M5에 넣는다**(엔드포인트 하나, 저장한 기록을 원본과 눈으로 대조하는 데 M5.5부터 바로 필요). 대안: 원본 보기는 M6 "기록 관리"(§7.3)로 미룬다.
2. **랭킹**: 최소 5세트와 PERFECT 정렬은 이미 정해졌다. 추천: 포지션 탭은 **그 포지션 5세트 이상**에만 순위를 준다(현재 `RankingCalculator` 동작). 정렬은 승률(기본)·KDA·경기 수 세 가지다. 대안: 포지션 탭의 최소 표본을 3세트로 낮춘다(포지션별 표본이 적어 초반에 순위가 거의 없을 수 있다).
3. **홈 구성**: 추천: **요약 숫자 + 최근 내전 묶음 하나(세트 줄 펼치기) + 랭킹 상위 5 + 바로가기**(§1.1). 대안: 최근 세트 5개만(아티팩트 "최근 경기"와 같음).
4. **CSV**: 추천: **파일 하나, 행 = 세트 × 선수, 밴 10개 열을 각 행에 반복**, 기록 화면의 현재 필터 적용, UTF-8 BOM. 대안 (a) 경기 CSV와 밴 CSV 두 파일(정규화되어 밴 분석이 쉽지만 버튼이 두 개), (b) 필터와 관계없이 항상 전체.
5. **티어 조정 추천을 M5에 넣을지**: 추천: **넣지 않는다**(2026-10-21 결정 유지). 라인별 3경기 이상이 쌓여야 의미가 있는데 실사용 데이터는 M5.5부터 생긴다. M5가 만드는 라인별 통계(`PlayerStats.byPosition` 조회)를 그대로 재사용할 수 있으니, 데이터가 쌓인 뒤 담당자 화면 기능으로 검토한다.
6. **기간·선수·챔피언 필터 범위**: 명세 §2-5의 수용 기준이다. 추천: **기록 화면에 기간·선수·챔피언 필터, 랭킹·선수 목록·프로필·챔피언 통계에 같은 기간 필터**(기본 전체, 날짜 두 칸). 대안: M5는 필터 없이 전체 기간만 보여 주고 나중에 더한다(구현량이 약간 줄지만 수용 기준이 미완으로 남는다).
7. **미등록 선수 노출**: 추천: 선수 목록·랭킹에 **포함하고 "미등록" 배지**를 단다(기록 화면에는 이미 닉네임으로 나온다). 대안: 목록·랭킹에서는 빼고 기록에만 보여 준다.
8. **대결 결과 표기**: 추천: **세트 점수만 보여 주고 "승리"는 쓰지 않는다**(대결 종료 확정이 미정, 유저플로우 §11-8).
9. **챔피언 통계 위치**: 추천: **새 메뉴 "챔피언"(`/champions`)**. 대안: 랭킹 화면 안의 탭.
10. (확인) 별칭 목록은 공개하지 않고, 요주 인물 표시는 M5 화면에 넣지 않는다(D8·D17). 괜찮은지.

## 참고 파일
- `C:\Users\ASUS\Desktop\지민\프로젝트\개인 프로젝트\lol-inhouse\backend\src\main\java\com\lolinhouse\domain\stats\{PlayerSetRecord,StatSummary,Kda,PlayerStats,RankingCalculator,RankingEntry,RankingKey,ChampionStats,DuelPersonalResult,DuelResult}.java`
- `C:\Users\ASUS\Desktop\지민\프로젝트\개인 프로젝트\lol-inhouse\backend\src\main\java\com\lolinhouse\domain\set\FearlessChain.java`
- `C:\Users\ASUS\Desktop\지민\프로젝트\개인 프로젝트\lol-inhouse\backend\src\main\java\com\lolinhouse\session\{GameSet,GameSetPlayer,GameSetBan,Duel,GameSetRepository,DuelRepository,InhouseSessionService}.java`
- `C:\Users\ASUS\Desktop\지민\프로젝트\개인 프로젝트\lol-inhouse\backend\src\main\java\com\lolinhouse\registration\RegistrationContextService.java`(체인 계산 참고. 공개 쪽은 이 클래스를 쓰지 않고 메모리 데이터로 같은 계산을 한다)
- `C:\Users\ASUS\Desktop\지민\프로젝트\개인 프로젝트\lol-inhouse\backend\src\main\java\com\lolinhouse\tier\{TierListService,PlayerLaneTierRepository}.java`, `...\teammaker\TeamMakerGuard.java`
- `C:\Users\ASUS\Desktop\지민\프로젝트\개인 프로젝트\lol-inhouse\backend\src\main\java\com\lolinhouse\champion\{ChampionController,ChampionListResponse}.java`, `...\ingest\image\ImageStore.java`
- `C:\Users\ASUS\Desktop\지민\프로젝트\개인 프로젝트\lol-inhouse\backend\src\main\java\com\lolinhouse\common\config\SecurityConfig.java`, `...\common\web\ApiExceptionHandler.java`
- `C:\Users\ASUS\Desktop\지민\프로젝트\개인 프로젝트\lol-inhouse\backend\src\main\resources\db\migration\V2__core_schema.sql`, `...\resources\application.yml`
- `C:\Users\ASUS\Desktop\지민\프로젝트\개인 프로젝트\lol-inhouse\frontend\src\{routes.tsx,layouts\PublicLayout.tsx,api\client.ts,api\queryKeys.ts,api\types.ts,styles\theme.css,pages\tier\TrendMark.tsx,pages\tier\tierSort.ts,pages\admin\register\useObjectUrl.ts}`
- 아티팩트(외부 코드, 읽기 전용): `C:/Users/ASUS/AppData/Local/Temp/claude/C--Users-ASUS-Desktop-----------------lol-inhouse/71177d5a-375e-4642-928e-ac05eca113c4/scratchpad/friend-artifact/app.js` 50~77행, 383~457행. 같은 폴더 `style.css` 183~229행, 315~326행.

참고(검토 중 발견, 범위 밖):
- `ChampionController` 주석에는 "ApiException에는 503 팩토리가 없어"라고 되어 있지만, M4.5에서 `serviceUnavailable`이 이미 있다(m45 §12.4). 주석이 낡았다.
- AGENTS.md §3 표 안의 M0 목록 줄 때문에 표 렌더링이 깨지는 문제(m45 참고 절)가 그대로 있다.
- AGENTS §7 "`spring.jackson.time-zone` 확인은 M5에서" 항목은 M5도 `Instant`를 내보내지 않으므로 여전히 열려 있다. M6 변경 이력 때 다시 본다.

## 9. 사용자 결정 (2026-10-21, 구현은 이 절이 위 내용보다 우선)

- **Q1 원본 결과창**: 방문자에게는 보여 주지 않는다(문서 결정 유지). **담당자 로그인 시 세트 상세 "원본 보기"를 M5에 넣는다** — `GET /api/admin/sets/{id}/image`.
- **Q2 랭킹**: 포지션 탭도 **그 포지션 5세트 이상**에만 순위(현재 `RankingCalculator` 동작). 정렬 승률(기본)·KDA·경기 수.
- **Q3 홈 구성(사용자 지정, §1.1 대체)**: **최근 경기 5개**(세트 줄 컴포넌트, 펼치기 가능) + **라인별 가장 많이 나온 픽**(라인 5개마다 그 라인에서 가장 많이 픽된 챔피언과 픽 횟수 — 같으면 승률, 그다음 한국어 이름순; 전체 기간 기준, 세트가 없으면 빈 상태). 요약 숫자·랭킹 상위 5·바로가기는 넣지 않는다(메뉴로 충분). API: 최근 5세트는 `GET /api/records`를 세트 기준으로 쓰기 어렵다면 `GET /api/home` 하나(`{ recentSets: SetView[≤5], topPicksByPosition: { [Position]: { championKey, picks, wins } | null } }`)로 만든다 — Phase 0에서 계약 확정.
- **Q4 CSV**: 파일 하나, 행 = 세트 × 선수, 밴 10개 열 반복, 기록 화면의 현재 필터 적용, UTF-8 BOM.
- **Q5~Q10(추천안 채택)**: 티어 조정 추천은 넣지 않음, 기간·선수·챔피언 필터 넣음(기록·통계 화면), 미등록 선수는 "미등록" 배지로 포함, 대결은 세트 점수만(승리 단정 없음), 챔피언 통계는 새 메뉴 `/champions`, 별칭 목록·요주 인물 표시는 공개 화면에 넣지 않음.

## 10. Codex(Astra) 설계 리뷰 반영 (2026-10-21, 구현은 §9와 이 절이 최우선)

1. **선수의 챔피언 경기 목록**: 필터에 `championScope`(`ANY`(기본: 세트의 픽 또는 밴) | `PLAYER_PICK`(선수 행 하나가 `playerId`와 `championKey`를 동시에 만족))를 추가한다. 프로필 챔피언 행 링크는 `/records?player={id}&champion={key}&championScope=PLAYER_PICK&from&to`(기간 유지). CSV·기록 API 모두 같은 `RecordFilter`. 테스트: "다른 선수의 픽", "밴만 존재", "본인의 픽" 구분.
2. **밴픽률**: 같은 세트에서 픽과 밴이 겹칠 수 있다(현재 저장 규칙이 허용). `ChampionPickBan`은 세트 ID가 있는 입력(픽 행 + `(setId, championKey)` 밴 행)으로 `picks`, `bans`, `wins`, `losses`와 **`setsWithPickOrBan`(픽이나 밴이 있었던 서로 다른 세트 수)**을 계산하고, 밴픽률 = `setsWithPickOrBan / totalSets`. 응답에 함께 보낸다. 픽·밴이 같은 세트에 겹치는 사례를 테스트에 포함.
3. **세트 화면 조립 공용 계약(Phase 0 확정)**: `record` 패키지에 공개 서비스 `SetViewAssembler.assemble(Collection<Long> setIds) → Map<Long, SetView>`(세트·선수·밴·이름·대결·체인 픽을 IN으로 일괄 로딩, 고정 쿼리 수)를 둔다. 통계 로더 `LoadedRecords`에는 세트 메타(`setId → (startDate, duelId, duelSeq, setNo)`)를 함께 담아 최근 순서를 판별한다. 프로필 최근 5경기·홈 최근 5경기·대결 상세·기록 페이지가 모두 이 진입점을 쓴다(`RecordViews`의 체인·제한 계산은 이 안에서). 체인 계산 입력은 표시 필터로 줄이지 않는다. 테스트: 서로 다른 묶음·CONTINUE 체인에 걸친 최근 5경기, 쿼리 수 고정.
4. **기록 없는 선수**: 선수 목록 대상 = **등록 선수 전체 ∪ 기간 내 참여 선수**(이름은 별도 조회). 프로필은 선수 존재를 경기 기록과 독립적으로 확인(없는 ID → 404, 있으나 기록 없음 → 빈 통계). 테스트: 세트가 전혀 없는 DB, 기간 내 기록만 없는 선수, 없는 ID.
5. **CSV 수식 방어**: 모든 문자열 셀에 같은 함수 적용, 시작 문자 `= + - @`, 전각 `＝ ＋ － ＠`, 탭·CR·LF를 위험으로 보고 앞에 `'`. 따옴표·구분자·줄바꿈 결합 직렬화 테스트.
- 추가: 도메인의 순위 미달 `0` → API `rank: null` 변환 테스트. 금지 키 테스트에 `screenRow`, `aliases`, `updatedAt`, `deletedAt` 추가, 중첩 `recentSets`까지 검사. 커서는 `startDate < before`, 조건에 맞는 묶음 `limit+1`, 마지막 `nextBefore=null` 고정. Hibernate 7 엔티티 조인·record 프로젝션은 지원됨(필드명·생성자 타입 일치는 Phase 0에서 검증).
