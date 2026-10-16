// 서버 상태 캐시(TanStack Query)의 기본 동작 설정. 노트: docs/study/03-frontend-skeleton.md
import { QueryClient } from '@tanstack/react-query'

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      retry: 1,
      refetchOnWindowFocus: false,
    },
  },
})
