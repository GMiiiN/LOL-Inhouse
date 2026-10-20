// 세트 등록(M4) API의 요청·응답 형태. 계약 출처: docs/design/m4-design.md §4.1, §5, §11.1, §11.2.
// 서버 구현(작업자 B·C)과 화면 구현(작업자 D)이 이 파일을 기준으로 맞춘다. 바꿀 때는 양쪽에 알린다.

import type { Position } from './types'

export type Side = 'BLUE' | 'RED'
export type DuelTeam = 'TEAM1' | 'TEAM2'
export type TopResult = 'WIN' | 'LOSS' // 결과창 위쪽 팀의 승패
export type FearlessMode = 'RESET' | 'CONTINUE' // RESET: 제한 없이 시작, CONTINUE: 직전 초기화 이후 픽 이어서 제한

// ---- 공통 조각 ----

// [ymin, xmin, ymax, xmax], 원본 이미지 전체 기준 0~1000 정규화(y가 먼저)
export type Box4 = [number, number, number, number]

// ---- 오류 확장 필드 (ProblemDetail에 추가로 실려 오는 값; ApiError가 보존해야 한다) ----
export type RegistrationProblemExtras = {
  violations?: string[] // 422 SET_RULES_VIOLATED: 규칙 위반 목록(check 응답의 errors와 같은 성격)
  warnings?: string[] // 422/201: 저장을 막지 않는 경고
  existingSetId?: number // 409 DUPLICATE_IMAGE · DUPLICATE_SET
  existingDuelId?: number
  existingSetNo?: number
  savedSetId?: number // 409 EXTRACTION_ALREADY_SAVED
}

// ---- 챔피언 (GET /api/admin/champions) ----
export type ChampionInfo = {
  id: string // Data Dragon 식별자(예: Ahri). DB champion_key에 그대로 저장된다
  key: string // Data Dragon 숫자 키(문자열)
  name: string // 한글 이름
  iconUrl: string // Data Dragon CDN 주소
}

export type ChampionList = {
  version: string
  champions: ChampionInfo[] // 한글 이름순
}

// ---- 등록 맥락 (GET /api/admin/duels/{duelId}/registration-context) ----
export type RegisteredSetSummary = {
  id: number
  setNo: number
  team1Side: Side
  winnerTeam: DuelTeam
  topTeam: DuelTeam | null
}

export type RegistrationContext = {
  duelId: number
  sessionId: number
  sessionStartDate: string // "2026-10-15"
  duelSeq: number
  fearlessMode: FearlessMode // 이 대결의 피어리스 방식
  nextSetNo: number // 서버가 정한 다음 세트 번호(유효 세트 최댓값 + 1)
  defaultTopSide: Side | null // 직전 위쪽 팀을 모르면 null
  restrictedChampions: string[] // 피어리스로 쓸 수 없는 챔피언 id
  registeredSets: RegisteredSetSummary[]
}

// ---- 추출 작업 ----
export type ExtractionState = 'QUEUED' | 'RUNNING' | 'SUCCEEDED' | 'FAILED'

// FAILED 작업의 errorCode. 화면 문구는 errorMessages.ts에서 대응시킨다
export type ExtractionErrorCode =
  | 'EXTRACTION_RATE_LIMITED'
  | 'EXTRACTION_UPSTREAM_UNAVAILABLE'
  | 'EXTRACTION_REJECTED'
  | 'EXTRACTION_TIMEOUT'
  | 'EXTRACTION_INVALID_RESPONSE'
  | 'EXTRACTION_NOT_CONFIGURED'
  | 'IMAGE_UNREADABLE'
  | 'CHAMPION_CATALOG_UNAVAILABLE'
  | 'BUSY'
  | 'INTERRUPTED'
  | 'INTERNAL_ERROR'
  | 'EXPIRED'
  | 'SUPERSEDED'

// POST /api/admin/duels/{duelId}/extractions (multipart: image, topSide, capturerPosition; 다시 추출은 ?retry=true)
// 202 새 작업(reused=false) 또는 200 기존 작업 재사용(reused=true)
export type ExtractionStartResponse = {
  jobId: number
  status: ExtractionState
  reused: boolean
}

// GET /api/admin/extractions/{jobId}. 시각(Instant) 필드는 없다: 경과 시간은 프런트가 직접 잰다
export type ExtractionJobStatus = {
  jobId: number
  duelId: number
  status: ExtractionState
  errorCode: ExtractionErrorCode | null
  attemptCount: number
  topSide: Side
  capturerPosition: Position
  savedSetId: number | null
}

// ---- 저장 명령 (POST …/sets/check 와 POST …/sets 가 같은 본문) ----
// 선수 확정 방식: UNRESOLVED(아직 안 골랐음 → 저장 거부), EXISTING(playerId 필수), NEW(playerId=null, nickname으로 미등록 선수 생성)
export type RowResolution = 'UNRESOLVED' | 'EXISTING' | 'NEW'

export type RowInput = {
  screenRow: number // 팀 안 결과창 줄 번호 1~5
  position: Position
  nickname: string // 확인된 표기(읽은 값 또는 담당자가 고친 전체 닉네임)
  resolution: RowResolution
  playerId: number | null
  addAlias: boolean // true면 nickname을 선택한 선수의 별칭으로 추가(playerId 필수)
  championKey: string | null // Data Dragon id
  kills: number
  deaths: number
  assists: number
  cs: number
  gold: number
}

export type TeamInput = {
  displayedKills: number // 결과창 팀 이름 옆 합계(행 합과 달라도 그대로 저장, 불일치는 경고)
  displayedDeaths: number
  displayedAssists: number
  displayedGold: number
  rows: RowInput[] // 길이 5, 화면 순서
  bans: (string | null)[] // 길이 5, 화면 순서(위 줄 3 → 아래 줄 2). 턴(B1~R5)은 서버가 정한다
}

// "새 대결로 등록": 팀 구성이 바뀐 세트를 새 대결의 1세트로 저장한다(서버가 같은 트랜잭션에서 대결을 만든다)
export type NewDuelOption = { fearlessMode: FearlessMode }

export type SetRegistrationRequest = {
  extractionJobId: number // M4 필수(수동 입력은 M6에서 null 허용 예정)
  topSide: Side
  capturerPosition: Position
  topResult: TopResult
  shotDate: string // "2026-10-12"
  duration: string // "25:42"
  top: TeamInput
  bottom: TeamInput
  newDuel?: NewDuelOption | null // 있으면 새 대결 기준으로 평가·저장한다(check에도 같이 보낸다)
}

// ---- 평가 결과 (POST …/sets/check 응답, 초안 응답 안에도 들어 있음) ----
export type TeamChangeKind = 'NONE' | 'SAME_LANE_SWAP' | 'POSITION_CHANGE' | 'RESHUFFLE' | 'PENDING'

export type TeamChange = {
  kind: TeamChangeKind // PENDING: 선수 미확정이라 감지 보류. 1세트는 항상 NONE
  swaps: number // SAME_LANE_SWAP일 때 맞바뀐 쌍의 수(그 외 0)
  details: string[] // 화면에 보여줄 설명(없으면 빈 배열)
}

export type DetectionMethod = 'FIRST_SET' | 'OVERLAP' | 'PENDING'

export type DuplicateSet = {
  setId: number
  duelId: number
  setNo: number
}

export type Evaluation = {
  setNo: number
  topTeam: DuelTeam | null // 판별 보류면 null
  // overlap은 method가 OVERLAP일 때만 숫자. uncertain은 팀 재구성(RESHUFFLE)이라 판별이 불확실할 때 true
  detection: { method: DetectionMethod; overlap: number | null; uncertain: boolean }
  team1Side: Side | null
  fingerprint: string | null // 입력이 형식 오류면 null
  duplicateSet: DuplicateSet | null // 같은 지문의 유효 세트가 이미 있으면 채워짐(저장하면 409)
  errors: string[] // 저장을 막는 위반
  warnings: string[] // 저장을 막지 않는 경고
  teamChange: TeamChange
}

// ---- 초안 (GET /api/admin/extractions/{jobId}/draft) ----
export type MatchType = 'EXACT' | 'TRUNCATED_AUTO' | 'ONE_CHAR_AUTO' | 'CANDIDATES' | 'NEW_UNREGISTERED'

export type PlayerCandidate = {
  playerId: number
  displayName: string
  nickname: string
  similarity: number
}

export type NicknameMatch = {
  type: MatchType
  playerId: number | null
  playerDisplayName: string | null
  similarity: number
  candidates: PlayerCandidate[]
  fullNicknameRequired: boolean // 잘린 닉네임이라 전체 닉네임 입력이 필요함
  newPlayerName: string | null
}

export type ChampionCandidate = { id: string; score: number }

export type ChampionGuess = {
  candidates: ChampionCandidate[] // 점수 내림차순, 최대 3개. 잘라내기 실패면 빈 배열
  margin: number
  uncertain: boolean // margin < 서버 설정 uncertain-margin
  cropFailed: boolean
}

export type DraftRow = {
  screenRow: number
  position: Position
  readNickname: string // 이미지에서 읽은 그대로
  truncated: boolean
  match: NicknameMatch
  kills: number
  deaths: number
  assists: number
  cs: number
  gold: number
  portraitBox: Box4
  champion: ChampionGuess
}

export type DraftBan = {
  slot: number // 1~5, 화면 순서
  box: Box4
  champion: ChampionGuess
}

export type DraftTeam = {
  displayedKills: number
  displayedDeaths: number
  displayedAssists: number
  displayedGold: number
  rows: DraftRow[] // 길이 5
  bans: DraftBan[] // 길이 5
}

export type DraftResponse = {
  jobId: number
  duelId: number
  topSide: Side // 작업의 초기 선택값
  capturerPosition: Position
  topResult: TopResult
  shotDate: string
  duration: string
  top: DraftTeam
  bottom: DraftTeam
  defaults: SetRegistrationRequest // 기본값으로 채운 저장 명령(확인 화면의 초기 상태)
  evaluation: Evaluation
}

// ---- 저장 응답 (POST …/sets, 201) ----
export type CreatedPlayerRef = { playerId: number; nickname: string }

export type SetRegistrationResponse = {
  setId: number
  duelId: number // "새 대결로 등록"이면 새로 만든 대결의 id
  setNo: number
  topTeam: DuelTeam
  warnings: string[]
  createdPlayers: CreatedPlayerRef[]
  addedAliases: CreatedPlayerRef[]
}
