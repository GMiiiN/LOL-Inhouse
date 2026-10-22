# M5.6 설계: 실사용 피드백 반영

- 날짜: 2026-10-22, 브랜치 `m56-feedback`(m55-desktop-ops 위. PR #8 머지 후 main으로 PR)
- 출처: 사용자·친구 피드백(2026-10-21~22), 데스크탑 운영 첫날 관찰

## 0. 반영 목록과 결정

| # | 피드백 | 결정 |
|---|---|---|
| F1 | 챔피언 초성으로 찾기 | 공용 `matchesChampion(query, name, key)`(한글 초성·부분 일치·영문 키). 세트 확인 화면 챔피언 선택, 기록 필터 챔피언 선택에 적용 |
| F2 | 관리자 내전·대결 화면 가시성(어디에 뭘 넣었는지) | 목록: 최신 날짜가 위, 묶음마다 대결 수·세트 수. 묶음 상세: 대결마다 **넣은 세트 표**(세트 번호, A팀 진영, 승리 팀, 경기 시간, 결과창 날짜, 양 팀 킬, 등록 방식 결과창/가져오기, 등록 시각, 세트 상세 링크)를 한눈에 |
| F3 | 티어 조정 추천 없음 | 친구 아티팩트 `computeRecs` 그대로 이식(§2). 담당자 선수 관리 화면에 카드, 적용·넘기기 |
| F4 | "꼭 다른 팀" 조건 | 팀 짜기 요청에 `apart` 쌍(최대 5). 같은 쌍이 together와 겹치면 400. apart가 비면 결과는 기존과 완전히 같음(골든 유지) |
| F5 | "랭킹" → "승률" | 메뉴·제목·문구만 변경(경로 `/ranking`과 API는 유지) |
| F6 | 홈 최근 경기 3세트 + 더 보기 | 홈 `recentSets` 3개, 아래 "내전 기록 더 보기" 링크(`/records`) |
| F7 | 최고·최악의 듀오 | 같은 세트 같은 팀이었던 두 사람 전부. 함께 **3세트 이상**만. 최고 3·최악 3을 **홈 화면 왼쪽 열**에(사용자 지정: 5쌍은 많음)(사용자 지정 2026-10-22: 사이트 왼쪽 여백 활용). 좁은 창에서는 본문 아래로 내려감. 기간은 전체 |
| F8 | 세트 상세 크게 | 상세 화면 표·아이콘·글자 확대, 폭 넓게 |
| F9 | 밴 순서 화살표 | B1 → R1 → … → R5 화살표로 순서 표시 |
| F10 | 내전 기록에 총 경기 수/세트 수 | 응답 `totals {duels, sets}`(현재 필터 전체 기준, 페이지와 무관), 기록 화면 맨 위 표시 |
| F11 | 내전 묶음 목록 날짜순 | 관리자 목록 `startDate` 내림차순 |
| F12 | 전체 크기가 작고 여백이 많음 | 기본 글자 크기·본문 최대 폭 확대(토큰 수준), 표·아이콘 기본 크기 상향. 좁은 창에서 가로 스크롤만 생기지 않게 |
| F13 | 새 내전 묶음 날짜 기본값 오늘 → 실수 | 빈칸으로 시작, 날짜를 골라야 버튼 활성 |

## 1. API 계약 (백엔드·프런트 병렬 작업 기준)

- `GET /api/home` → `recentSets` 최대 **3**개(나머지 동일).
- `GET /api/records` 응답에 `totals: { duels: number, sets: number }` 추가(필터·기간 적용, 커서·limit 무관; duels = 조건에 맞는 세트가 1개 이상인 대결 수).
- `GET /api/duos?from&to` (공개 GET) → `{ minSets: 3, best: DuoEntry[≤3], worst: DuoEntry[≤3] }`, `DuoEntry = { players: [{ playerId, displayName, registered }, {…}], sets, wins, losses, winRate }`. players는 playerId 오름차순. best: winRate 내림차순 → sets 내림차순 → 이름. worst: winRate 오름차순 → sets 내림차순 → 이름. 한 쌍이 best와 worst 둘 다에 들어가지 않게(후보가 적으면 worst는 best에 없는 쌍만).
- `GET /api/admin/tier-recommendations` (ADMIN) → `TierRecommendation[]`: `{ playerId, displayName, position, games, wins, kda, laneAvgKda, score, direction: "UP"|"DOWN", from: { tier, sub }, to: { tier, sub } }`, |score| 내림차순. 넘기기는 브라우저 localStorage(`tier-rec-dismissed`: `"playerId:position" → games`, 아티팩트와 같음). 적용은 기존 프로필 PUT(`recordChange: true`).
- `GET /api/admin/inhouse-sessions` → 시작일 내림차순, 각 행 `duelCount`, `setCount`(삭제 표시 제외한 유효 세트 수 `activeSetCount`도 추가; 삭제 버튼 판단은 기존 `setCount`).
- `GET /api/admin/inhouse-sessions/{id}` → 대결마다 `sets: AdminSetSummary[]` 추가: `{ setId, setNo, team1Side, winnerTeam, durationSec|null, shotDate, team1Kills, team2Kills, source: "IMAGE"|"IMPORT", createdAt, deleted }`(setNo 오름차순).
- 팀 짜기 `POST /api/team-maker/auto|manual` 요청에 `apart: [[playerId, playerId], …]`(선택, 최대 5쌍, 참가자만, together와 같은 쌍 금지 → 400 기존 오류 방식). 조건을 만족하는 후보가 없으면 기존 "조건을 만족하는 팀 없음" 응답과 같은 방식.

## 2. 티어 조정 추천(아티팩트 `computeRecs` 이식, 상수 그대로)
- 대상 기록: 유효 세트 전체. 라인 평균 KDA `laneAvg[라인] = (ΣK+ΣA)/ΣD` (ΣD=0이면 2.5).
- 선수·라인마다(티어가 있는 라인만): 기준일 **이후**(세션 시작일 > 기준일) 그 라인으로 뛴 세트만 집계. `g ≥ 3`(REC_MIN)일 때만.
- `kda = (K+A)/max(D,1)`, `swr = (w+2)/(g+4)`, `score = (swr−0.5)×2 + clamp(kda/laneAvg−1, −1, 1)×0.5`. `score ≥ 0.35` → 한 단계 위, `≤ −0.35` → 한 단계 아래, 그 외 없음.
- 한 단계: 하→중→상, 상에서 위로는 티어−1의 하(1티어 상이면 추천 없음), 하에서 아래로는 티어+1의 상(6티어 하면 추천 없음).
- **기준일**: `player_lane_tier.tier_changed_on DATE NULL`(V7) — 티어 값이 **실제로 바뀐** 저장에서만 오늘(Asia/Seoul)로 설정(새 라인 추가 포함). `recordChange:false`(가져오기)는 설정하지 않음. NULL이면 전체 기록.
- 넘기기: 같은 키의 저장된 games 이상이면 숨김(경기가 더 쌓이면 다시 보임).

## 3. 작업 분할 (파일 겹침 없음)
- **B1 통계**: `stats/**`(듀오 서비스·컨트롤러, 홈 3개), `record/RecordService·RecordPageResponse`(totals), 티어 추천(`tier/TierRecommendation*` 새 파일 + V7 + `PlayerProfileService`의 tier_changed_on 설정), SecurityConfig 공개 matcher(`/api/duos`).
- **B2 관리·팀**: `session/**`(목록 정렬·카운트·상세 세트 요약), `domain/team/**`·`teammaker/**`(apart).
- **F1 공개 화면**: 전역 크기(`styles/theme.css`·`global.css`), 홈(왼쪽 열 듀오 + 최근 3세트), 승률 화면(이름 변경), 기록 화면 totals, 세트 상세 확대, 밴 화살표(`components/record/BanStrip`), `PublicLayout` 메뉴 이름.
- **F2 관리·도구 화면**: 초성 검색 유틸 + 적용(세트 확인 화면 챔피언 선택, `pages/records/RecordFilters.tsx`), 관리자 내전·대결 화면(목록·상세), 새 묶음 날짜 빈칸, 팀 짜기 apart UI, 선수 관리 티어 추천 카드.
