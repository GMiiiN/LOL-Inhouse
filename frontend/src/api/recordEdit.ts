// M6 세트 삭제·복구·수정 호출 함수 (설계 m6-design §14.1)
import { apiRequest } from './client'
import type {
  DeletedSetRow,
  EditEvaluation,
  RestoreResult,
  SetEditRequest,
  SetEditResponse,
  SetFormResponse,
} from './recordEditTypes'

export const deleteSet = (setId: number) => apiRequest<void>(`/api/admin/sets/${setId}`, { method: 'DELETE' })

export const restoreSet = (setId: number) =>
  apiRequest<RestoreResult>(`/api/admin/sets/${setId}/restore`, { method: 'POST' })

export const listDeletedSets = () => apiRequest<DeletedSetRow[]>('/api/admin/deleted-sets')

export const getSetForm = (setId: number) => apiRequest<SetFormResponse>(`/api/admin/sets/${setId}/form`)

// 저장 없는 검사. 저장 때 서버가 같은 검사를 한 번 더 한다.
export const checkSetEdit = (setId: number, body: SetEditRequest) =>
  apiRequest<EditEvaluation>(`/api/admin/sets/${setId}/check`, { method: 'POST', json: body })

export const updateSet = (setId: number, body: SetEditRequest) =>
  apiRequest<SetEditResponse>(`/api/admin/sets/${setId}`, { method: 'PUT', json: body })
