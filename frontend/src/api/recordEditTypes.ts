// M6 세트 삭제·복구 API 타입 (설계 m6-design §14.1)
import type { DuelTeam, Side } from './registrationTypes'

export type FearlessConflict = {
  setId: number
  duelId: number
  duelSeq: number
  setNo: number
  championKey: string
}

export type RestoreResult = {
  setId: number
  warnings: string[]
  fearlessConflicts: FearlessConflict[]
}

// 복구를 막는 세트. 둘 다 null이면 막는 것이 없다.
export type RestoreBlockers = {
  fingerprintSetId: number | null // 같은 경기(지문)로 이미 등록된 세트
  setNoSetId: number | null // 같은 대결에 같은 번호로 있는 세트
}

export type DeletedSetRow = {
  setId: number
  duelId: number
  duelSeq: number
  sessionId: number
  sessionStartDate: string
  setNo: number
  shotDate: string | null
  durationSec: number | null
  winnerTeam: DuelTeam
  team1Side: Side
  team1Kills: number
  team2Kills: number
  deletedAt: string
  blockers: RestoreBlockers
  fearlessConflicts: FearlessConflict[]
}
