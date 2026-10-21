// 챔피언 통계 정렬. 선택한 기준이 같으면 밴+픽 횟수, 그래도 같으면 한국어 이름순.
import type { ChampionStatItem } from '../../api/publicTypes'
import { compareNames } from '../tier/tierSort'

export type ChampionSortKey = 'PICK_BAN_RATE' | 'PICKS' | 'BANS' | 'WIN_RATE'

export const CHAMPION_SORTS: { value: ChampionSortKey; label: string }[] = [
  { value: 'PICK_BAN_RATE', label: '밴픽률' },
  { value: 'PICKS', label: '픽' },
  { value: 'BANS', label: '밴' },
  { value: 'WIN_RATE', label: '승률' },
]

export const winRateOf = (c: ChampionStatItem) => (c.wins + c.losses > 0 ? c.wins / (c.wins + c.losses) : 0)

const primary: Record<ChampionSortKey, (c: ChampionStatItem) => number> = {
  PICK_BAN_RATE: (c) => c.setsWithPickOrBan, // 같은 기간이라 분모(전체 세트)가 같아 개수 비교가 비율 비교와 같다
  PICKS: (c) => c.picks,
  BANS: (c) => c.bans,
  WIN_RATE: winRateOf,
}

// nameOf: 챔피언 키 → 화면에 보이는 한글 이름(카탈로그가 없으면 키)
export function sortChampions(
  list: readonly ChampionStatItem[],
  key: ChampionSortKey,
  nameOf: (championKey: string) => string,
): ChampionStatItem[] {
  const value = primary[key]
  return [...list].sort(
    (a, b) =>
      value(b) - value(a) ||
      b.picks + b.bans - (a.picks + a.bans) ||
      compareNames(nameOf(a.championKey), nameOf(b.championKey)),
  )
}
