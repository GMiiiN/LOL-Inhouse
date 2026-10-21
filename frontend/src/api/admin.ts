// 담당자 API 호출 함수 모음 (URL은 M3 설계 §5)
import { ApiError, apiRequest } from './client'
import type { FearlessMode } from './registrationTypes'
import type {
  AdminMe,
  DuelSummary,
  InhouseSessionDetail,
  InhouseSessionSummary,
  Player,
} from './types'

// ---- 인증 ----
// 미로그인(401)은 오류가 아니라 "로그인 안 됨"이라는 정상 답이므로 null로 바꾼다.
export async function getMe(): Promise<AdminMe | null> {
  try {
    return await apiRequest<AdminMe>('/api/admin/me')
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) return null
    throw error
  }
}

export function login(username: string, password: string): Promise<AdminMe> {
  return apiRequest<AdminMe>('/api/admin/login', { method: 'POST', form: { username, password } })
}

// 204만 확인한다. CSRF 토큰 재발급은 호출한 쪽이 성공 처리 뒤에 따로 한다(실패해도 로그아웃을 되돌리지 않기 위해).
export const logout = () => apiRequest<void>('/api/admin/logout', { method: 'POST' })

// ---- 선수 ----
export const listPlayers = () => apiRequest<Player[]>('/api/admin/players')

// ---- 내전 묶음·대결 ----
export const listSessions = () => apiRequest<InhouseSessionSummary[]>('/api/admin/inhouse-sessions')

export const getSession = (id: number) =>
  apiRequest<InhouseSessionDetail>(`/api/admin/inhouse-sessions/${id}`)

export const createSession = (startDate: string) =>
  apiRequest<InhouseSessionSummary>('/api/admin/inhouse-sessions', { method: 'POST', json: { startDate } })

// 같은 묶음에 새 대결을 만든다. fearlessMode: RESET(제한 없이 시작) 또는 CONTINUE(이전 대결에서 이어가기)
export const createDuel = (sessionId: number, fearlessMode: FearlessMode) =>
  apiRequest<DuelSummary>(`/api/admin/inhouse-sessions/${sessionId}/duels`, {
    method: 'POST',
    json: { fearlessMode },
  })
