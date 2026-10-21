// M4.5 API 계약(라인 티어·점수표·팀 짜기·선수 프로필). 백엔드 DTO와 필드가 같다.
// 설명: docs/design/m45-design.md §4, §10, §11. 회차·변동 이력은 없다. ▲▼는 라인마다 마지막 변경 하나만 온다.
import type { Position } from './types'

export const TIER_SUBS = ['HIGH', 'MID', 'LOW'] as const
export type TierSub = (typeof TIER_SUBS)[number]
export const TIER_SUB_LABEL: Record<TierSub, string> = { HIGH: '상', MID: '중', LOW: '하' }

export const TIERS = [1, 2, 3, 4, 5, 6] as const
export type Tier = (typeof TIERS)[number]

// ---- 점수표 ----
// base 키는 JSON에서 문자열 "1"~"6"이다.
export type ScoreTable = {
  base: Record<`${Tier}`, number>
  sub: Record<TierSub, number>
  lane: Record<Position, number>
}
export type ScoreTableResponse = ScoreTable & { isDefault: boolean }
export type ScoreTableRequest = ScoreTable // PUT /api/admin/score-table. 14칸 모두, 정수 -999~999

export const SCORE_MIN = -999
export const SCORE_MAX = 999

// ---- 티어리스트 (GET /api/tier-list, 공개) ----
export type TrendDirection = 'UP' | 'DOWN'
export type Trend = { direction: TrendDirection; fromTier: Tier; fromSub: TierSub }
export type LaneView = { tier: Tier; sub: TierSub; score: number; trend: Trend | null }
export type TierPlayer = {
  id: number
  displayName: string // 실명
  lanes: Record<Position, LaneView | null> // 5라인 키가 모두 있고 티어 없는 라인은 null
  flagged: boolean // 요주 인물(벌 아이콘). 계산에는 영향 없음
}
export type TierListResponse = {
  scoreTable: ScoreTableResponse
  players: TierPlayer[] // id 오름차순. 한국어 정렬은 화면이 한다
}

// ---- 선수 프로필 (담당자, /api/admin/player-profiles) ----
export type LaneValue = { tier: Tier; sub: TierSub }
export type ProfileAlias = { id: number; nickname: string }
export type PlayerProfile = {
  id: number
  displayName: string
  realName: string | null
  registered: boolean
  flagged: boolean
  mainPosition: Position | null
  aliases: ProfileAlias[]
  lanes: Record<Position, LaneValue | null>
}
// 키가 없거나 null인 라인 = 티어 없음. 생성은 닉네임 1~10개, 수정은 1~max(10, 기존 개수)개
export type PlayerProfileRequest = {
  realName: string | null
  mainPosition: Position | null
  lanes: Partial<Record<Position, LaneValue | null>>
  nicknames: string[]
  flagged?: boolean // 생략하면 생성은 false, 수정은 기존 값 유지. 화면은 항상 보낸다
  recordChange?: boolean // 생략하면 true. false면 이번 저장은 ▲▼ 변동으로 치지 않는다(가져오기 도구용, 화면은 보내지 않는다)
}

// ---- 팀 짜기 (POST /api/team-maker/auto|manual, 공개) ----
export const MAX_TOGETHER_PAIRS = 5
export type TogetherPair = [number, number]
export type TeamMakerAutoRequest = {
  participantIds: number[] // 클릭한 순서 그대로 10개. 정렬해서 보내면 결과가 달라진다
  together: TogetherPair[]
  fixedLanes?: Record<number, Position> // 라인 고정(자동만). JSON 키는 문자열 선수 id. 생략·빈 맵 = 고정 없음
}
export type TeamMakerManualRequest = {
  picks: Record<Position, [number, number]> // 라인마다 [첫째, 둘째]
  together: TogetherPair[]
}
// 블루 칸이 A팀이다. tier·sub가 null이면 티어 없는 라인(점수는 대체 점수)
export type SlotView = { playerId: number; position: Position; tier: Tier | null; sub: TierSub | null; score: number }
export type TeamOption = {
  blue: SlotView[] // 탑~서폿 순서 5개
  red: SlotView[]
  blueSum: number
  redSum: number
  diff: number
  gap: number
  cost: number
  total: number // 교체 후에는 프런트가 blueSum + redSum으로 다시 계산한다
}
export type MatrixCell = { score: number; tier: Tier | null; sub: TierSub | null; hasTier: boolean }
export type ScoreMatrix = Record<string, Record<Position, MatrixCell>> // 키는 선수 id 문자열
export type TeamMakerResponse = {
  options: TeamOption[] // 1~4개, 좌우 교체가 이미 반영됨
  scoreMatrix: ScoreMatrix
}

// ---- 새 오류 코드 (errorMessages.ts에 문구가 있다) ----
export const TIER_TEAM_ERROR_CODES = [
  'INVALID_LANE_TIER',
  'LANE_TIER_REQUIRED',
  'NICKNAME_REQUIRED',
  'DUPLICATE_REAL_NAME',
  'PLAYER_HAS_RECORDS',
  'INVALID_SCORE_TABLE',
  'TEAM_PARTICIPANT_COUNT',
  'TEAM_DUPLICATE_PARTICIPANT',
  'TEAM_LANE_PICK_INCOMPLETE',
  'TEAM_LANE_TIER_MISSING',
  'TEAM_PLAYER_NOT_FOUND',
  'TEAM_PAIR_INVALID',
  'TEAM_NO_VALID_SPLIT',
  'TEAM_LANE_FIX_INVALID',
  'TEAM_FIXED_LANE_BLOCKED',
  'TEAM_PAIR_UNSATISFIABLE',
  'TEAM_MAKER_BUSY',
  'RATE_LIMITED',
] as const
export type TierTeamErrorCode = (typeof TIER_TEAM_ERROR_CODES)[number]
