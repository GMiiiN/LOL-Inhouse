// 티어리스트 정렬 규칙: 이름은 한국어 가나다순, 한 칸 안에서는 상 → 중 → 하. 팀 짜기 화면(pages/team)도 가져다 쓴다.
import type { Position } from '../../api/types'
import type { LaneView, Tier, TierPlayer, TierSub } from '../../api/tierTypes'

const koreanCollator = new Intl.Collator('ko')

export const compareNames = (a: string, b: string) => koreanCollator.compare(a, b)

export const SUB_ORDER: Record<TierSub, number> = { HIGH: 0, MID: 1, LOW: 2 }

// 이름순으로 정렬한 새 배열(원본은 건드리지 않는다)
export const sortByName = <T extends { displayName: string }>(list: readonly T[]): T[] =>
  [...list].sort((a, b) => compareNames(a.displayName, b.displayName))

export type BoardEntry = { player: TierPlayer; lane: LaneView }

// 티어표의 한 칸(티어 × 라인)에 들어갈 사람들: 상 → 중 → 하, 같으면 이름순
export function boardCell(players: readonly TierPlayer[], tier: Tier, position: Position): BoardEntry[] {
  const entries: BoardEntry[] = []
  for (const player of players) {
    const lane = player.lanes[position]
    if (lane && lane.tier === tier) entries.push({ player, lane })
  }
  return entries.sort(
    (a, b) => SUB_ORDER[a.lane.sub] - SUB_ORDER[b.lane.sub] || compareNames(a.player.displayName, b.player.displayName),
  )
}
