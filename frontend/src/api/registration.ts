// 세트 등록(M4) API 호출 함수 모음. URL·본문은 docs/design/m4-design.md §5, 타입은 registrationTypes.ts.
import { apiRequest } from './client'
import type {
  ChampionList,
  DraftResponse,
  Evaluation,
  ExtractionJobStatus,
  ExtractionStartResponse,
  RegistrationContext,
  SetRegistrationRequest,
  SetRegistrationResponse,
  Side,
} from './registrationTypes'
import type { Position } from './types'

export const getRegistrationContext = (duelId: number) =>
  apiRequest<RegistrationContext>(`/api/admin/duels/${duelId}/registration-context`)

export const listChampions = () => apiRequest<ChampionList>('/api/admin/champions')

export type ExtractionStartInput = {
  image: Blob
  topSide: Side
  capturerPosition: Position
}

// retry=true: 같은 이미지의 기존 작업을 재사용하지 않고 새 작업을 만든다("다시 추출", 설계 §11.2-3)
export function startExtraction(duelId: number, input: ExtractionStartInput, retry = false) {
  const formData = new FormData()
  formData.append('image', input.image)
  formData.append('topSide', input.topSide)
  formData.append('capturerPosition', input.capturerPosition)
  const query = retry ? '?retry=true' : ''
  return apiRequest<ExtractionStartResponse>(`/api/admin/duels/${duelId}/extractions${query}`, {
    method: 'POST',
    formData,
  })
}

export const getExtraction = (jobId: number) => apiRequest<ExtractionJobStatus>(`/api/admin/extractions/${jobId}`)

export const getDraft = (jobId: number) => apiRequest<DraftResponse>(`/api/admin/extractions/${jobId}/draft`)

// 저장 없는 검사. 저장 때 서버가 같은 검사를 한 번 더 한다.
export const checkSet = (duelId: number, body: SetRegistrationRequest) =>
  apiRequest<Evaluation>(`/api/admin/duels/${duelId}/sets/check`, { method: 'POST', json: body })

export const registerSet = (duelId: number, body: SetRegistrationRequest) =>
  apiRequest<SetRegistrationResponse>(`/api/admin/duels/${duelId}/sets`, { method: 'POST', json: body })
