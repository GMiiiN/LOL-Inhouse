// 선수 등록·수정 폼의 클라이언트 검증(서버 규칙과 같은 한도)
import type { Position } from '../../api/types'

export const REAL_NAME_MAX = 50
export const NICKNAME_MAX = 50
export const NICKNAMES_MIN_COUNT = 1
export const NICKNAMES_MAX_COUNT = 10

export type PlayerFormValues = {
  realName: string
  mainPosition: Position | ''
  nicknames: string[]
}

export type PlayerFormErrors = {
  realName?: string
  nicknames?: string
}

// create: 실명 필수 + 닉네임 1~10개 / edit-registered: 실명 필수 / edit-unregistered: 실명 비우면 미등록 유지.
// 주 포지션은 세 경우 모두 선택(비우면 "모름" = null).
export type PlayerFormMode = 'create' | 'edit-registered' | 'edit-unregistered'

// 닉네임 중복 비교용. 서버는 NFKC·대소문자 무시로 비교하므로 여기서는 가장 단순한 근사(공백 제거·소문자)만 한다.
const dupKey = (s: string) => s.trim().toLowerCase()

export function validateRealName(realName: string): string | undefined {
  const trimmed = realName.trim()
  if (!trimmed) return '실명을 입력해 주세요.'
  if (trimmed.length > REAL_NAME_MAX) return `실명은 ${REAL_NAME_MAX}자 이하여야 합니다.`
  return undefined
}

export function validateNickname(nickname: string): string | undefined {
  const trimmed = nickname.trim()
  if (!trimmed) return '닉네임을 입력해 주세요.'
  if (trimmed.length > NICKNAME_MAX) return `닉네임은 ${NICKNAME_MAX}자 이하여야 합니다.`
  return undefined
}

// 수정 모드에서는 닉네임을 검사하지 않는다(별칭은 따로 관리)
export function validatePlayerForm(values: PlayerFormValues, mode: PlayerFormMode): PlayerFormErrors {
  const errors: PlayerFormErrors = {}

  // 미등록 선수 수정에서 빈 실명은 "계속 미등록"이므로 오류가 아니다(길이 초과만 막는다).
  const blankAllowed = mode === 'edit-unregistered' && !values.realName.trim()
  const realNameError = blankAllowed ? undefined : validateRealName(values.realName)
  if (realNameError) errors.realName = realNameError

  if (mode === 'create') {
    const { nicknames } = values
    if (nicknames.length < NICKNAMES_MIN_COUNT || nicknames.length > NICKNAMES_MAX_COUNT) {
      errors.nicknames = `게임 닉네임은 ${NICKNAMES_MIN_COUNT}~${NICKNAMES_MAX_COUNT}개여야 합니다.`
    } else {
      const firstBad = nicknames.map(validateNickname).find((m) => m !== undefined)
      if (firstBad) {
        errors.nicknames = firstBad
      } else if (new Set(nicknames.map(dupKey)).size !== nicknames.length) {
        errors.nicknames = '같은 닉네임이 두 번 들어 있습니다.'
      }
    }
  }
  return errors
}

export const hasErrors = (errors: PlayerFormErrors) => Object.keys(errors).length > 0
