// M5 공개 조회 API 계약(백엔드 record/stats DTO와 필드가 같다). 설명: docs/design/m5-design.md §3, §9, §10
// 날짜는 'YYYY-MM-DD' 문자열뿐이고 Instant는 없다. 공개 응답에는 이미지 키·해시·지문·top_team·screen_row·별칭·생성/수정/삭제 시각이 없다.
import type { DuelTeam, FearlessMode, Side } from './registrationTypes'
import type { Tier, TierSub, Trend } from './tierTypes'
import type { Position } from './types'

// ---- 공통 ----
export type KdaView = {
  kills: number
  deaths: number
  assists: number
  ratio: number | null // null = PERFECT(데스 0) 또는 sets = 0. 화면은 sets를 먼저 보고 구분한다
}
export type SummaryView = {
  sets: number
  wins: number
  losses: number
  winRate: number // 0~1
  kda: KdaView
  cs: number // 합계. 평균은 화면이 sets로 나눈다
  gold: number
}

// ---- 세트 (기록 목록 펼침, 세트 상세, 프로필 최근 경기, 홈이 모두 같은 모양) ----
export type PlayerLine = {
  playerId: number
  displayName: string // 등록 선수는 실명, 미등록은 게임 닉네임
  registered: boolean
  position: Position
  championKey: string
  kills: number
  deaths: number
  assists: number
  cs: number
  gold: number
}
export type DisplayedTotals = { kills: number; deaths: number; assists: number; gold: number } // 결과창 표시값
export type TeamView = {
  team: DuelTeam
  side: Side
  won: boolean
  displayed: DisplayedTotals
  players: PlayerLine[] // TOP→SUPPORT
}
export type BanView = { turn: number; side: Side; championKey: string } // turn 1~10 = B1 R1 ... B5 R5
export type SetView = {
  setId: number
  setNo: number
  duelId: number
  duelSeq: number
  sessionId: number
  sessionDate: string
  shotDate: string
  durationSec: number | null
  team1Side: Side
  winnerTeam: DuelTeam
  teams: [TeamView, TeamView] // [TEAM1, TEAM2]
  bans: BanView[] // 10개
  fearlessRestricted: string[] // 이 세트 시점의 피어리스 제한 챔피언 키(오름차순)
}

// ---- 내전 기록 (GET /api/records) ----
export type DuelScore = Record<DuelTeam, number> // 세트 점수만. '승리'라고 단정하지 않는다
export type DuelHead = {
  duelId: number
  seq: number
  fearlessMode: FearlessMode
  score: DuelScore // 필터와 무관한 그 대결 유효 세트 전체 기준
  totalSets: number
}
export type DuelBlock = DuelHead & { sets: SetView[] } // sets는 필터에 맞는 세트만
export type SessionBlock = { sessionId: number; startDate: string; duels: DuelBlock[] }
export type RecordTotals = { duels: number; sets: number } // 현재 필터 전체 기준(페이지와 무관)
export type RecordPage = { sessions: SessionBlock[]; nextBefore: string | null; totals: RecordTotals }

// ---- 대결 상세 (GET /api/duels/{id}) ----
export type DuelResult = 'WIN' | 'DRAW' | 'LOSS'
export type DuelPersonal = {
  playerId: number
  displayName: string
  registered: boolean
  teams: DuelTeam[] // 맞라인 교환이 있으면 둘일 수 있다
  wins: number
  losses: number
  result: DuelResult
}
export type DuelDetail = DuelHead & {
  sessionId: number
  sessionDate: string
  sets: SetView[]
  personal: DuelPersonal[]
  fearless: { inherited: string[]; picked: string[] } // 다음 세트 기준 제한: 이전 대결에서 이어온 것 / 이 대결에서 픽된 것
}

// ---- 선수 목록 (GET /api/players) ----
export type TopChampion = { championKey: string; sets: number }
export type PlayerListItem = {
  playerId: number
  displayName: string
  registered: boolean
  mainPosition: Position | null
  summary: SummaryView // 기록이 없으면 summary.sets = 0
  positions: Partial<Record<Position, number>> // 뛴 라인별 세트 수
  topChampions: TopChampion[] // 최대 5
  championCount: number
}
export type PlayerList = { totalSets: number; players: PlayerListItem[] }

// ---- 선수 프로필 (GET /api/players/{id}) ----
export type LaneTierView = { tier: Tier; sub: TierSub; trend: Trend | null }
export type PositionSummary = { position: Position; summary: SummaryView; topChampions: TopChampion[] }
export type ChampionSummary = { championKey: string; summary: SummaryView }
export type DuelResultItem = {
  duelId: number
  sessionDate: string
  seq: number
  wins: number
  losses: number
  result: DuelResult
}
export type PlayerRecord = {
  playerId: number
  displayName: string
  registered: boolean
  mainPosition: Position | null
  laneTiers: Record<Position, LaneTierView | null> // 5라인 키 모두, 티어 없는 라인은 null
  totalSets: number
  participationRate: number // 0~1
  summary: SummaryView
  byPosition: PositionSummary[]
  byChampion: ChampionSummary[]
  byChampionByPosition: Partial<Record<Position, ChampionSummary[]>> // 그 라인으로 뛴 기록만의 챔피언별(뛴 라인만 키)
  duels: { wins: number; draws: number; losses: number; items: DuelResultItem[] }
  recentSets: SetView[] // 최대 3
}

// ---- 랭킹 (GET /api/rankings) ----
export type RankingPosition = 'ALL' | Position
export type RankingSort = 'WIN_RATE' | 'KDA' | 'SETS'
export type RankingEntry = {
  playerId: number
  displayName: string
  registered: boolean
  rank: number | null // null = 최소 표본(5세트) 미달. 서버 정렬 순서 그대로 그린다
  summary: SummaryView
}
export type Ranking = {
  position: RankingPosition
  sort: RankingSort
  minSets: number
  totalSets: number
  entries: RankingEntry[]
}

// ---- 챔피언 통계 (GET /api/champion-stats) ----
export type ChampionStatItem = {
  championKey: string
  picks: number
  bans: number
  wins: number
  losses: number
  setsWithPickOrBan: number // 밴픽률 = setsWithPickOrBan / totalSets
}
export type ChampionStats = { totalSets: number; champions: ChampionStatItem[] }

// ---- 홈 (GET /api/home) ----
export type TopPick = { championKey: string; picks: number; wins: number }
export type Home = {
  recentSets: SetView[] // 최대 5
  topPicksByPosition: Record<Position, TopPick | null> // 5라인 키 모두
}

// ---- 듀오 (GET /api/duos) ----
export type DuoPlayer = { playerId: number; displayName: string; registered: boolean }
export type DuoEntry = { players: [DuoPlayer, DuoPlayer]; sets: number; wins: number; losses: number; winRate: number }
export type Duos = { minSets: number; best: DuoEntry[]; worst: DuoEntry[] } // 각 최대 3쌍

// ---- 필터 ----
export type ChampionScope = 'ANY' | 'PLAYER_PICK'
export type Period = { from?: string; to?: string } // 내전 시작일 기준, 양끝 포함

// 화면(URL 검색 파라미터) 쪽 이름: /records?player=3&champion=Ahri&championScope=PLAYER_PICK&from=&to=
export type RecordsPageParams = Period & {
  player?: string
  champion?: string
  championScope?: ChampionScope
}
// API 쪽 이름: GET /api/records?playerId=3&champion=Ahri&championScope=PLAYER_PICK&from=&to=&before=&limit=
export type RecordsQuery = Period & {
  playerId?: number
  champion?: string
  championScope?: ChampionScope
  before?: string // 이 시작일보다 앞선 묶음부터(커서). 이전 응답의 nextBefore
  limit?: number // 1~20, 기본 10
}
export type RankingQuery = Period & { position?: RankingPosition; sort?: RankingSort }
