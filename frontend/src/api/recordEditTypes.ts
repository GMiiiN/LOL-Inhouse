// M6 세트 삭제·복구 API 타입 (설계 m6-design §14.1)
import type { DuelTeam, Evaluation, Side, TeamInput, TopResult } from './registrationTypes'

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

// ---- 세트 수정 (PR-B) ----

// 수정 요청. 등록 요청과 같은 팀 모양이고, 사유·캡처한 사람 포지션은 없다. duration이 ''이면 경기 시간 "모름".
export type SetEditRequest = {
  topSide: Side
  topResult: TopResult
  shotDate: string
  duration: string
  top: TeamInput
  bottom: TeamInput
  expectedUpdatedAt: string // 폼을 불러온 시점의 updatedAt. 다르면 409 SET_MODIFIED
}

// 수정 검사 결과 = 등록 평가 + 뒤 세트와의 피어리스 충돌(teamChange는 항상 NONE)
export type EditEvaluation = Evaluation & { fearlessConflicts: FearlessConflict[] }

export type SetFormResponse = {
  setId: number
  duelId: number
  duelSeq: number
  sessionId: number
  sessionStartDate: string
  setNo: number
  deleted: boolean
  updatedAt: string
  hasImage: boolean
  form: SetEditRequest
  evaluation: EditEvaluation
  restrictedChampions: string[]
  fearlessConflicts: FearlessConflict[]
}

export type SetEditResponse = {
  setId: number
  duelId: number
  setNo: number
  topTeam: DuelTeam
  warnings: string[]
  fearlessConflicts: FearlessConflict[]
  createdPlayers: { playerId: number; nickname: string }[]
  addedAliases: { playerId: number; nickname: string }[]
}

// 수정 저장 뒤 세트 상세로 넘어갈 때 router state로 실어 보내는 경고
export type SetEditSavedState = { editWarnings: string[]; fearlessConflicts: FearlessConflict[] }
