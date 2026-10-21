// 공개 챔피언 카탈로그(이름·아이콘) 조회 훅
import { useQuery } from '@tanstack/react-query'
import { getPublicChampions } from '../../api/public'
import { PUBLIC_CHAMPIONS_KEY } from '../../api/queryKeys'
import type { ChampionInfo } from '../../api/registrationTypes'

// 카탈로그는 거의 안 바뀌므로 길게 캐시한다. 같은 키를 쓰는 모든 아이콘이 요청 하나를 공유한다.
export function usePublicChampions() {
  return useQuery({
    queryKey: PUBLIC_CHAMPIONS_KEY,
    queryFn: getPublicChampions,
    staleTime: 60 * 60 * 1000,
  })
}

// 챔피언 키(Data Dragon id) → 카탈로그 정보. 카탈로그를 못 받았거나 없는 키면 undefined.
export function useChampionLookup(): (championKey: string) => ChampionInfo | undefined {
  const { data } = usePublicChampions()
  return (championKey) => data?.champions.find((c) => c.id === championKey)
}
