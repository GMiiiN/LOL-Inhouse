// 백엔드 API 응답·요청 형태를 한곳에 모은 파일. 백엔드(M3 설계 §5)가 확정되면 여기만 맞춘다.
// 아래 필드 이름은 설계 문서의 설명을 바탕으로 한 "가정"이다. 실제 DTO와 다르면 이 파일부터 고친다.

export const POSITIONS = ['TOP', 'JUNGLE', 'MID', 'ADC', 'SUPPORT'] as const
export type Position = (typeof POSITIONS)[number]

export const POSITION_LABEL: Record<Position, string> = {
  TOP: '탑',
  JUNGLE: '정글',
  MID: '미드',
  ADC: '원딜',
  SUPPORT: '서포터',
}

// ---- 오류 (application/problem+json) ----
export type FieldError = { field: string; message: string }

// 별칭 충돌 한 건: 이 닉네임은 이미 이 선수에게 연결되어 있다 (가정)
export type AliasConflict = {
  nickname: string
  playerId: number
  playerDisplayName?: string
}

export type ProblemBody = {
  title?: string
  detail?: string
  code?: string
  errors?: FieldError[]
  conflicts?: AliasConflict[]
  existingSessionId?: number // SESSION_DATE_CONFLICT 때만
}

// ---- 인증 ----
export type AdminMe = { username: string }

// ---- 선수 ----
export type PlayerAlias = { id: number; nickname: string }

export type Player = {
  id: number
  displayName: string // 등록 선수는 실명, 미등록은 게임 닉네임
  realName: string | null // null = 미등록
  registered: boolean
  mainPosition: Position | null
  aliases: PlayerAlias[]
}

// POST /api/admin/players
export type PlayerCreateRequest = {
  realName: string
  mainPosition: Position | null // null = 모름
  nicknames: string[]
}

// PUT /api/admin/players/{id} - 전체 치환. 별칭은 별도 엔드포인트로만 바꾼다고 가정
// realName null/빈 값: 미등록 선수는 미등록 유지, 등록 선수는 서버가 400 REAL_NAME_CANNOT_BE_CLEARED
export type PlayerUpdateRequest = {
  realName: string | null
  mainPosition: Position | null
}

// ---- 내전 묶음·대결 ----
export type InhouseSessionSummary = {
  id: number
  startDate: string // "2026-10-15"
  duelCount: number
}

export type DuelSummary = {
  id: number
  seq: number // 묶음 안 "대결 N"
}

export type InhouseSessionDetail = {
  id: number
  startDate: string
  duels: DuelSummary[]
}
