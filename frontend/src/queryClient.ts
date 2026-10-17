// 서버 상태 캐시(TanStack Query)의 기본 동작 설정
import { MutationCache, QueryCache, QueryClient } from '@tanstack/react-query'
import { ApiError } from './api/client'
import { ME_KEY } from './api/queryKeys'

export function createQueryClient(): QueryClient {
  // 어떤 요청이든 401(세션 만료)이 오면 "로그인 안 됨"으로 바꿔 AdminLayout이 로그인 화면으로 보내게 한다.
  // 로그인 실패(LOGIN_FAILED)도 401이지만 세션 만료가 아니므로 제외한다.
  const handleError = (error: unknown) => {
    if (error instanceof ApiError && error.status === 401 && error.code !== 'LOGIN_FAILED') {
      client.setQueryData(ME_KEY, null)
    }
  }

  const client: QueryClient = new QueryClient({
    queryCache: new QueryCache({ onError: handleError }),
    mutationCache: new MutationCache({ onError: handleError }),
    defaultOptions: {
      queries: {
        staleTime: 30_000,
        // 4xx는 다시 해도 같은 결과이므로 재시도하지 않는다. 그 외(서버 불안정)만 1회.
        retry: (failureCount, error) =>
          !(error instanceof ApiError && error.status >= 400 && error.status < 500) && failureCount < 1,
        refetchOnWindowFocus: false,
      },
    },
  })
  return client
}

export const queryClient = createQueryClient()
