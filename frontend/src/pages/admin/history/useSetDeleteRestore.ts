// 세트 삭제·복구 뮤테이션. 성공하면 묶음 상세·공개 조회·등록 맥락·삭제 목록·티어 추천 캐시를 함께 무효화한다.
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { deleteSet, restoreSet } from '../../../api/recordEdit'
import { DELETED_SETS_KEY, PUBLIC_KEY, SESSIONS_KEY, TIER_RECOMMENDATIONS_KEY, registrationContextKey } from '../../../api/queryKeys'

export type SetRef = { setId: number; duelId: number }

function useInvalidateAfterEdit() {
  const queryClient = useQueryClient()
  return (duelId: number) =>
    Promise.all([
      queryClient.invalidateQueries({ queryKey: PUBLIC_KEY }),
      queryClient.invalidateQueries({ queryKey: SESSIONS_KEY }),
      queryClient.invalidateQueries({ queryKey: registrationContextKey(duelId) }),
      queryClient.invalidateQueries({ queryKey: DELETED_SETS_KEY }),
      // 추천은 경기 기록으로 계산되므로 세트가 빠지거나 돌아오면 다시 받아야 한다
      queryClient.invalidateQueries({ queryKey: TIER_RECOMMENDATIONS_KEY }),
    ])
}

export function useDeleteSet() {
  const invalidate = useInvalidateAfterEdit()
  return useMutation({
    mutationFn: (ref: SetRef) => deleteSet(ref.setId),
    onSuccess: (_data, ref) => invalidate(ref.duelId),
  })
}

export function useRestoreSet() {
  const invalidate = useInvalidateAfterEdit()
  return useMutation({
    mutationFn: (ref: SetRef) => restoreSet(ref.setId),
    onSuccess: (_data, ref) => invalidate(ref.duelId),
  })
}
