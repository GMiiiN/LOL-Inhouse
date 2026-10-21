// 팀 짜기 API 호출 함수(공개). URL·본문은 docs/design/m45-design.md §4, 타입은 tierTypes.ts.
import { apiRequest } from './client'
import type { TeamMakerAutoRequest, TeamMakerManualRequest, TeamMakerResponse, TierListResponse } from './tierTypes'

// 티어리스트 조회(공개). 팀 짜기 화면은 선수 이름·라인 티어를 여기서 받는다.
export const getTeamMakerTierList = () => apiRequest<TierListResponse>('/api/tier-list')

export const postTeamAuto = (request: TeamMakerAutoRequest) =>
  apiRequest<TeamMakerResponse>('/api/team-maker/auto', { method: 'POST', json: request })

export const postTeamManual = (request: TeamMakerManualRequest) =>
  apiRequest<TeamMakerResponse>('/api/team-maker/manual', { method: 'POST', json: request })
