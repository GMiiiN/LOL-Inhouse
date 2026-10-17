// 현재 로그인 상태 조회 훅. 데이터 null = 미로그인
import { useQuery } from '@tanstack/react-query'
import { getMe } from '../api/admin'
import { ME_KEY } from '../api/queryKeys'

export function useAdminMe() {
  return useQuery({ queryKey: ME_KEY, queryFn: getMe })
}
