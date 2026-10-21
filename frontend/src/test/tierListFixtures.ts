// 티어리스트·점수표·선수 프로필 테스트용 자료 (가상 이름만 쓴다)
import type { LaneView, PlayerProfile, ScoreTableResponse, Tier, TierPlayer, TierSub, Trend } from '../api/tierTypes'
import type { Position } from '../api/types'

export const DEFAULT_SCORE_TABLE: ScoreTableResponse = {
  base: { '1': 60, '2': 52, '3': 44, '4': 36, '5': 28, '6': 20 },
  sub: { HIGH: 3, MID: 0, LOW: -3 },
  lane: { TOP: 0, JUNGLE: 0, MID: 0, ADC: -4, SUPPORT: -3 },
  isDefault: true,
}

export const lane = (tier: Tier, sub: TierSub, score: number, trend: Trend | null = null): LaneView => ({
  tier,
  sub,
  score,
  trend,
})

export const NO_LANES: Record<Position, LaneView | null> = { TOP: null, JUNGLE: null, MID: null, ADC: null, SUPPORT: null }

export const tierPlayer = (id: number, displayName: string, lanes: Partial<Record<Position, LaneView>>, flagged = false): TierPlayer => ({
  id,
  displayName,
  lanes: { ...NO_LANES, ...lanes },
  flagged,
})

export const profile = (overrides: Partial<PlayerProfile> & { id: number; displayName: string }): PlayerProfile => ({
  realName: overrides.displayName,
  registered: true,
  flagged: false,
  mainPosition: null,
  aliases: [{ id: overrides.id * 10, nickname: `${overrides.displayName}닉` }],
  lanes: { TOP: null, JUNGLE: null, MID: null, ADC: null, SUPPORT: null },
  ...overrides,
})
