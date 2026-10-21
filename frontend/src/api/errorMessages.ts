// 서버 오류 code를 화면에 보여 줄 한국어 문구로 바꾼다
import { ApiError } from './client'
import { POSITION_LABEL } from './types'

export const LOGIN_FAILED_MESSAGE = '아이디 또는 비밀번호가 맞지 않습니다.'

// 값이 함수인 항목은 오류에 실린 확장 필드(existingSetNo 등)로 문구를 만든다.
const MESSAGES: Record<string, string | ((error: ApiError) => string)> = {
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
  FEARLESS_CONTINUE_WITHOUT_PREVIOUS: '내전 묶음의 첫 대결은 이어갈 이전 대결이 없어 초기화로만 만들 수 있습니다.',
  CONCURRENT_REQUEST_CONFLICT: '같은 시각에 다른 요청과 충돌했습니다. 잠시 뒤 다시 시도해 주세요.',

  // ---- M4 세트 등록 요청 오류 ----
  DUPLICATE_IMAGE: (e) =>
    e.existingSetNo !== undefined
      ? `이미 ${e.existingSetNo}세트로 등록된 결과창입니다.`
      : '이미 세트로 등록된 결과창입니다.',
  IMAGE_REQUIRED: '결과창 이미지를 붙여 넣거나 선택해 주세요.',
  UNSUPPORTED_IMAGE: 'PNG 또는 JPEG 이미지만 올릴 수 있습니다.',
  IMAGE_DIMENSIONS_EXCEEDED: '이미지 크기가 너무 큽니다(최대 4096x4096).',
  IMAGE_TOO_LARGE: '이미지가 너무 큽니다(최대 10MB).',
  EXTRACTION_BUSY: '다른 추출이 진행 중이라 대기열이 가득 찼습니다. 잠시 뒤 다시 시도해 주세요.',
  EXTRACTION_NOT_CONFIGURED: 'AI 추출이 설정되어 있지 않습니다(Gemini API 키 없음). 서버 설정을 확인해 주세요.',
  CHAMPION_CATALOG_UNAVAILABLE: '챔피언 목록을 불러올 수 없습니다. 잠시 뒤 다시 시도해 주세요.',
  EXTRACTION_NOT_FOUND: '추출 작업을 찾을 수 없습니다.',
  EXTRACTION_NOT_READY: '아직 추출이 끝나지 않았습니다.',
  EXTRACTION_DUEL_MISMATCH: '이 대결의 추출 작업이 아닙니다.',
  EXTRACTION_ALREADY_SAVED: '이 추출 결과는 이미 세트로 저장되었습니다.',
  SET_RULES_VIOLATED: '세트 규칙을 지키지 않은 항목이 있어 저장하지 못했습니다.',
  DUPLICATE_SET: (e) =>
    e.existingSetNo !== undefined
      ? `이미 ${e.existingSetNo}세트로 등록된 같은 경기입니다.`
      : '이미 등록된 같은 경기입니다(날짜·경기 시간·킬·골드가 같음).',
  SET_NO_CONFLICT:
    '다른 곳에서 세트가 먼저 등록되어 세트 번호가 바뀌었습니다. 등록 현황을 새로 불러왔으니 확인한 뒤 다시 저장해 주세요.',
  PLAYER_NOT_FOUND: '선택한 선수를 찾을 수 없습니다. 선수를 다시 골라 주세요.',
  DUEL_NOT_FOUND: '대결을 찾을 수 없습니다.',

  // ---- M4.5 라인 티어·점수표·팀 짜기 ----
  INVALID_LANE_TIER: '라인 티어 값이 올바르지 않습니다. 티어는 1~6, 우열은 상·중·하 중에서 골라 주세요.',
  LANE_TIER_REQUIRED: '최소 한 라인은 티어를 골라 주세요.',
  NICKNAME_REQUIRED: '닉네임을 하나 이상 입력해 주세요.',
  DUPLICATE_REAL_NAME: '같은 이름이 이미 있어요.',
  PLAYER_HAS_RECORDS: '세트 기록이 있는 선수는 삭제할 수 없습니다.',
  INVALID_SCORE_TABLE: '점수는 빈칸 없이 -999~999 사이의 정수로 입력해 주세요.',
  TEAM_PARTICIPANT_COUNT: '참가자를 정확히 10명 골라 주세요.',
  TEAM_DUPLICATE_PARTICIPANT: '같은 사람이 두 번 선택됐어요.',
  TEAM_LANE_PICK_INCOMPLETE: '모든 라인에 2명씩 골라 주세요.',
  TEAM_LANE_TIER_MISSING: '그 라인 티어가 없는 사람이 들어 있어요. 티어가 있는 사람만 고를 수 있어요.',
  TEAM_PLAYER_NOT_FOUND: '선택한 선수를 찾을 수 없어요. 티어리스트를 새로 불러온 뒤 다시 골라 주세요.',
  TEAM_PAIR_INVALID: '같은 팀 조건이 올바르지 않아요. 참가자 중 서로 다른 두 사람만, 최대 5쌍까지 묶을 수 있어요.',
  TEAM_NO_VALID_SPLIT:
    '가능한 조합이 없어요. 고른 사람들의 라인이 겹쳐서 양 팀 모두 5개 라인을 채울 수 없거나, 같은 팀 조건 때문에 막혀요.',
  TEAM_LANE_FIX_INVALID: (e) =>
    e.position !== undefined && e.count !== undefined
      ? `${POSITION_LABEL[e.position]}에 ${e.count}명이 고정돼 있어요. 한 라인은 양 팀 합쳐 2명까지만 고정할 수 있어요.`
      : '라인 고정이 올바르지 않아요. 참가자만 고정할 수 있고, 한 라인은 양 팀 합쳐 2명까지만 고정할 수 있어요.',
  TEAM_FIXED_LANE_BLOCKED:
    '라인 고정 때문에 가능한 조합이 없어요. 고정한 라인 때문에 다른 사람들이 갈 라인이 모자라요. 고정을 하나씩 풀어 보세요.',
  TEAM_PAIR_UNSATISFIABLE: '같은 팀 조건을 만족하는 조합이 없어요. 같은 라인의 두 사람은 같은 팀이 될 수 없어요.',
  TEAM_MAKER_BUSY: '지금 다른 팀 짜기 계산이 진행 중이에요. 잠시 뒤 다시 시도해 주세요.',
  RATE_LIMITED: '요청이 너무 많아요. 잠시 뒤 다시 시도해 주세요.',

  // ---- M5 공개 조회 ----
  SET_NOT_FOUND: '세트를 찾을 수 없어요. 삭제되었거나 없는 세트예요.',
  SET_IMAGE_NOT_FOUND: '원본 결과창 이미지가 없어요.',
  INVALID_QUERY: '조회 조건이 올바르지 않아요. 기간의 앞뒤 날짜와 선택한 조건을 확인해 주세요.',
}

// 추출 작업이 FAILED가 된 이유(작업 상태의 errorCode). 요청 오류와 달리 HTTP 오류가 아니라 작업 상태 값이다.
const EXTRACTION_FAILURE_MESSAGES: Record<string, string> = {
  EXTRACTION_RATE_LIMITED: 'AI 추출 요청 한도에 걸렸습니다. 잠시 기다렸다가 다시 추출해 주세요.',
  EXTRACTION_UPSTREAM_UNAVAILABLE: 'AI 서버가 응답하지 않습니다. 잠시 뒤 다시 추출해 주세요.',
  EXTRACTION_REJECTED: 'AI 서버가 요청을 거절했습니다. 서버 설정(API 키)을 확인해 주세요.',
  EXTRACTION_TIMEOUT: '추출이 제한 시간 안에 끝나지 않았습니다. 다시 추출해 주세요.',
  EXTRACTION_INVALID_RESPONSE: 'AI가 결과창을 제대로 읽지 못했습니다. 이미지를 확인하고 다시 추출해 주세요.',
  EXTRACTION_NOT_CONFIGURED: 'AI 추출이 설정되어 있지 않습니다(Gemini API 키 없음).',
  IMAGE_UNREADABLE: '저장된 이미지를 읽을 수 없습니다. 이미지를 다시 붙여 넣어 주세요.',
  CHAMPION_CATALOG_UNAVAILABLE: '챔피언 목록을 불러오지 못해 추출하지 못했습니다. 잠시 뒤 다시 추출해 주세요.',
  BUSY: '다른 추출이 진행 중이라 대기열이 가득 찼습니다. 잠시 뒤 다시 추출해 주세요.',
  INTERRUPTED: '서버가 재시작되어 추출이 중단되었습니다. 다시 추출해 주세요.',
  INTERNAL_ERROR: '추출 중 서버에 문제가 생겼습니다. 다시 추출해 주세요.',
  EXPIRED: '추출이 시작되지 못한 채 시간이 지났습니다. 다시 추출해 주세요.',
  SUPERSEDED: '같은 이미지로 새 추출이 시작되어 이 작업은 닫혔습니다.',
}

export function describeExtractionFailure(errorCode: string | null | undefined): string {
  return (errorCode && EXTRACTION_FAILURE_MESSAGES[errorCode]) || '추출에 실패했습니다. 다시 추출해 주세요.'
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
  const entry = MESSAGES[error.code]
  let message = typeof entry === 'function' ? entry(error) : entry
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
