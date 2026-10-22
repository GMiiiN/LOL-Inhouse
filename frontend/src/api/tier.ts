// M4.5 티어리스트·점수표·선수 프로필 호출 함수 (URL은 docs/design/m45-design.md §4, §12)
import { apiRequest } from './client'
import type {
  PlayerProfile,
  PlayerProfileRequest,
  ScoreTableRequest,
  ScoreTableResponse,
  TierRecommendation,
  TierListResponse,
} from './tierTypes'

// 공개 조회(로그인 없이)
export const getTierList = () => apiRequest<TierListResponse>('/api/tier-list')
export const getScoreTable = () => apiRequest<ScoreTableResponse>('/api/score-table')

// 담당자: 선수 프로필(미등록 포함)
export const listProfiles = () => apiRequest<PlayerProfile[]>('/api/admin/player-profiles')

export const createProfile = (body: PlayerProfileRequest) =>
  apiRequest<PlayerProfile>('/api/admin/player-profiles', { method: 'POST', json: body })

export const updateProfile = (id: number, body: PlayerProfileRequest) =>
  apiRequest<PlayerProfile>(`/api/admin/player-profiles/${id}`, { method: 'PUT', json: body })

export const deleteProfile = (id: number) =>
  apiRequest<void>(`/api/admin/player-profiles/${id}`, { method: 'DELETE' })

// 담당자: 미등록 선수(source)의 기록·닉네임을 기존 선수(target)에게 합친다. 되돌릴 수 없다.
export const mergePlayer = (sourceId: number, targetId: number) =>
  apiRequest<PlayerProfile>(`/api/admin/players/${sourceId}/merge`, { method: 'POST', json: { targetId } })

// 담당자: 점수표. 14칸을 모두 보내는 전체 치환이다.
export const putScoreTable = (body: ScoreTableRequest) =>
  apiRequest<ScoreTableResponse>('/api/admin/score-table', { method: 'PUT', json: body })

export const resetScoreTable = () =>
  apiRequest<ScoreTableResponse>('/api/admin/score-table/reset', { method: 'POST' })

export const listTierRecommendations = () =>
  apiRequest<TierRecommendation[]>('/api/admin/tier-recommendations')
