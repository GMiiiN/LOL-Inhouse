// 선수 편집기의 폼 상태 <-> 요청 변환과 검증. 화면(PlayerEditor)은 이 함수들만 부른다.
import type { LaneValue, PlayerProfile, PlayerProfileRequest, Tier, TierSub } from '../../../api/tierTypes'
import { POSITIONS, type Position } from '../../../api/types'

export const REAL_NAME_MAX = 50
export const NICKNAME_MAX = 50
export const NICKNAMES_DEFAULT_MAX = 10

export type LaneForm = { tier: Tier | ''; sub: TierSub } // tier '' = 안 함
export type EditorForm = {
  realName: string
  mainPosition: Position | ''
  nicknames: string // 쉼표로 구분한 한 줄
  flagged: boolean
  lanes: Record<Position, LaneForm>
}

// create: 새 사람 / edit-registered: 실명 필수 / edit-unregistered: 실명을 비우면 미등록 유지
export type EditorMode = 'create' | 'edit-registered' | 'edit-unregistered'

export type EditorErrors = { realName?: string; nicknames?: string; lanes?: string }

export const editorMode = (profile: PlayerProfile | null): EditorMode =>
  !profile ? 'create' : profile.registered ? 'edit-registered' : 'edit-unregistered'

export function initialForm(profile: PlayerProfile | null): EditorForm {
  const lanes = {} as Record<Position, LaneForm>
  for (const position of POSITIONS) {
    const value = profile?.lanes[position]
    lanes[position] = value ? { tier: value.tier, sub: value.sub } : { tier: '', sub: 'MID' } // 새 라인의 기본 우열은 중
  }
  return {
    realName: profile?.realName ?? '',
    mainPosition: profile?.mainPosition ?? '',
    nicknames: profile?.aliases.map((alias) => alias.nickname).join(', ') ?? '',
    flagged: profile?.flagged ?? false,
    lanes,
  }
}

// 쉼표(전각 포함)로 나누고 앞뒤 공백을 지운 뒤 빈 항목은 버린다
export const parseNicknames = (text: string): string[] =>
  text
    .split(/[,，]/)
    .map((part) => part.trim())
    .filter((part) => part !== '')

// 서버는 NFKC·대소문자 무시로 비교하므로 여기서도 같은 쪽으로 근사한다
const nicknameKey = (nickname: string) => nickname.normalize('NFKC').toLowerCase()

export type EditorContext = {
  otherRealNames: string[] // 이 사람을 뺀 다른 선수들의 실명
  existingAliasCount: number // 수정 중인 선수의 현재 닉네임 수(새로 만들면 0)
}

export function validateEditor(form: EditorForm, mode: EditorMode, context: EditorContext): EditorErrors {
  const errors: EditorErrors = {}

  const realName = form.realName.trim()
  if (!realName) {
    if (mode !== 'edit-unregistered') errors.realName = '이름을 입력해 주세요.'
  } else if (realName.length > REAL_NAME_MAX) {
    errors.realName = `이름은 ${REAL_NAME_MAX}자 이하여야 해요.`
  } else if (context.otherRealNames.some((other) => other.trim() === realName)) {
    errors.realName = '같은 이름이 이미 있어요.'
  }

  const nicknames = parseNicknames(form.nicknames)
  // 수정은 기존 개수가 10개를 넘어도 그대로 저장할 수 있다
  const max = mode === 'create' ? NICKNAMES_DEFAULT_MAX : Math.max(NICKNAMES_DEFAULT_MAX, context.existingAliasCount)
  if (nicknames.length === 0) {
    errors.nicknames = '닉네임을 하나 이상 입력해 주세요.'
  } else if (nicknames.length > max) {
    errors.nicknames = `닉네임은 ${max}개 이하여야 해요.`
  } else if (nicknames.some((nickname) => nickname.length > NICKNAME_MAX)) {
    errors.nicknames = `닉네임은 하나당 ${NICKNAME_MAX}자 이하여야 해요.`
  } else if (new Set(nicknames.map(nicknameKey)).size !== nicknames.length) {
    errors.nicknames = '같은 닉네임이 두 번 들어 있어요.'
  }

  // 새로 만들 때만 최소 1라인. 기존 선수는 0라인으로도 저장할 수 있다
  if (mode === 'create' && POSITIONS.every((position) => form.lanes[position].tier === '')) {
    errors.lanes = '최소 한 라인은 티어를 골라 주세요.'
  }
  return errors
}

export const hasEditorErrors = (errors: EditorErrors) => Object.keys(errors).length > 0

// '안 함'은 null로 명시해 보낸다(서버가 그 라인 티어를 지운다)
export function buildRequest(form: EditorForm): PlayerProfileRequest {
  const lanes = {} as Record<Position, LaneValue | null>
  for (const position of POSITIONS) {
    const lane = form.lanes[position]
    lanes[position] = lane.tier === '' ? null : { tier: lane.tier, sub: lane.sub }
  }
  return {
    realName: form.realName.trim() || null,
    mainPosition: form.mainPosition || null,
    lanes,
    nicknames: parseNicknames(form.nicknames),
    flagged: form.flagged,
  }
}
