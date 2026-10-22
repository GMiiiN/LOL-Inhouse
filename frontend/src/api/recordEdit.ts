// M6 세트 삭제·복구 호출 함수 (설계 m6-design §14.1)
import { apiRequest } from './client'
import type { DeletedSetRow, RestoreResult } from './recordEditTypes'

export const deleteSet = (setId: number) => apiRequest<void>(`/api/admin/sets/${setId}`, { method: 'DELETE' })

export const restoreSet = (setId: number) =>
  apiRequest<RestoreResult>(`/api/admin/sets/${setId}/restore`, { method: 'POST' })

export const listDeletedSets = () => apiRequest<DeletedSetRow[]>('/api/admin/deleted-sets')
