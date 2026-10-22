// 저장된 세트 수정 화면의 상태 계산(순수 함수). 팀 표·밴 칸의 편집 함수는 reviewModel을 그대로 쓴다.
// 설계: docs/design/m6-design.md §3.2·§9.2
import type { RowInput } from '../../../api/registrationTypes'
import type { SetEditRequest } from '../../../api/recordEditTypes'
import type { Player } from '../../../api/types'
import { cleanTeam, cloneTeam, type TeamsState } from '../register/reviewModel'

// 폼이 들고 있는 편집 상태. expectedUpdatedAt은 불러온 시점의 값이라 상태가 아니라 요청을 만들 때 붙인다.
export type FormState = TeamsState

export function formFromRequest(form: SetEditRequest): FormState {
  return {
    topSide: form.topSide,
    topResult: form.topResult,
    shotDate: form.shotDate,
    duration: form.duration,
    top: cloneTeam(form.top),
    bottom: cloneTeam(form.bottom),
  }
}

// 서버로 보내고 검사 키로 쓰기 전에 정리한다: 닉네임 공백 제거, 행 순서(결과창 행), resolution과 playerId 일관성
export function cleanFormState(state: FormState): FormState {
  return { ...state, duration: state.duration.trim(), top: cleanTeam(state.top), bottom: cleanTeam(state.bottom) }
}

export function buildEditRequest(state: FormState, expectedUpdatedAt: string): SetEditRequest {
  return { ...cleanFormState(state), expectedUpdatedAt }
}

// 빈 값은 "모름"(지문 ?, ADR 014). 값이 있으면 mm:ss
export function isDurationValid(duration: string): boolean {
  const value = duration.trim()
  return value === '' || /^\d{1,3}:[0-5]\d$/.test(value)
}

// ---------- 초안 없는 선수 칸 ----------

type PlayerRef = Pick<Player, 'id' | 'displayName' | 'aliases'>

export function knownNicknamesOf(player: Pick<Player, 'displayName' | 'aliases'>): string[] {
  return [player.displayName, ...player.aliases.map((alias) => alias.nickname)]
}

const same = (a: string, b: string) => a.trim().toLowerCase() === b.trim().toLowerCase()

// 기존 선수를 고른다. 닉네임은 그 선수의 대표 닉네임으로 맞추고, 별칭 추가는 꺼 둔다.
export function pickExistingPlayer(row: RowInput, player: PlayerRef): RowInput {
  const nickname = player.aliases[0]?.nickname ?? player.displayName
  return { ...row, resolution: 'EXISTING', playerId: player.id, nickname, addAlias: false }
}

// 새 미등록 선수: playerId=null, 이 행의 nickname으로 서버가 선수를 만든다.
export function pickNewPlayer(row: RowInput): RowInput {
  return { ...row, resolution: 'NEW', playerId: null, addAlias: false }
}

export type ManualAliasControl = { visible: boolean; checked: boolean }

// "이 닉네임을 별칭으로 추가"는 기존 선수에게 그 선수가 아직 모르는 닉네임을 적었을 때만 보인다.
export function manualAliasControl(row: RowInput, knownNicknames: readonly string[]): ManualAliasControl {
  const typed = row.nickname.trim()
  if (row.resolution !== 'EXISTING' || row.playerId === null || typed === '') return { visible: false, checked: false }
  if (knownNicknames.some((known) => same(known, typed))) return { visible: false, checked: false }
  return { visible: true, checked: row.addAlias }
}

// 닉네임을 고친다. 별칭 추가 체크박스가 사라지는 입력이면 체크도 함께 끈다(요청에 남지 않게).
export function setManualNickname(row: RowInput, nickname: string, knownNicknames: readonly string[]): RowInput {
  const next = { ...row, nickname }
  return manualAliasControl(next, knownNicknames).visible ? next : { ...next, addAlias: false }
}
