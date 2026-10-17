// 서버 오류 code를 화면에 보여 줄 한국어 문구로 바꾼다
import { ApiError } from './client'

export const LOGIN_FAILED_MESSAGE = '아이디 또는 비밀번호가 맞지 않습니다.'

const MESSAGES: Record<string, string> = {
  LOGIN_FAILED: LOGIN_FAILED_MESSAGE,
  UNAUTHORIZED: '로그인이 필요합니다.',
  CSRF_INVALID: '보안 토큰이 만료되었습니다. 잠시 뒤 다시 시도해 주세요.',
  FORBIDDEN: '이 작업을 할 권한이 없습니다.',
  NETWORK_ERROR: '서버에 연결하지 못했습니다. 잠시 뒤 다시 시도해 주세요.',
  VALIDATION_FAILED: '입력값을 확인해 주세요.',
  MALFORMED_REQUEST: '요청 형식이 올바르지 않습니다.',
  DUPLICATE_NICKNAME_IN_REQUEST: '같은 닉네임이 두 번 들어 있습니다.',
  ALIAS_CONFLICT: '이미 다른 선수에게 연결된 닉네임입니다.',
  LAST_ALIAS: '마지막 닉네임은 삭제할 수 없습니다. 선수에게는 닉네임이 하나 이상 있어야 합니다.',
  REAL_NAME_CANNOT_BE_CLEARED: '등록된 선수의 실명은 비울 수 없습니다.',
  SESSION_DATE_CONFLICT: '같은 시작일의 내전 묶음이 이미 있습니다.',
  DUEL_SEQ_CONFLICT: '다른 곳에서 동시에 대결이 만들어졌습니다. 목록을 새로 고친 뒤 다시 시도해 주세요.',
}

export type ErrorView = {
  message: string
  fieldErrors: string[] // "필드: 메시지"
  conflicts: string[] // 별칭 충돌 설명
}

export function describeError(error: unknown): ErrorView {
  if (!(error instanceof ApiError)) {
    return { message: '알 수 없는 오류가 발생했습니다.', fieldErrors: [], conflicts: [] }
  }
  let message = MESSAGES[error.code]
  if (!message && error.status === 404) message = '대상을 찾을 수 없습니다.'
  if (!message && error.status >= 500) message = '서버에 문제가 생겼습니다. 잠시 뒤 다시 시도해 주세요.'
  return {
    message: message ?? error.detail ?? '요청을 처리하지 못했습니다.',
    fieldErrors: error.errors.map((e) => `${e.field}: ${e.message}`),
    conflicts: error.conflicts.map(
      (c) => `'${c.nickname}' - 이미 ${c.playerDisplayName ?? `선수 #${c.playerId}`} 선수에게 연결되어 있습니다.`,
    ),
  }
}
