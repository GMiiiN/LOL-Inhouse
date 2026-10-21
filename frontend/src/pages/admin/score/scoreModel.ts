// 점수 설정 화면의 입력(문자열) <-> 점수표(정수 14칸) 변환과 검증
import { POSITIONS, type Position } from '../../../api/types'
import { SCORE_MAX, SCORE_MIN, TIERS, TIER_SUBS, type ScoreTable, type Tier, type TierSub } from '../../../api/tierTypes'

export type ScoreDraft = {
  base: Record<`${Tier}`, string>
  sub: Record<TierSub, string>
  lane: Record<Position, string>
}

export function draftFromTable(table: ScoreTable): ScoreDraft {
  const base = {} as ScoreDraft['base']
  for (const tier of TIERS) base[`${tier}`] = String(table.base[`${tier}`])
  const sub = {} as ScoreDraft['sub']
  for (const key of TIER_SUBS) sub[key] = String(table.sub[key])
  const lane = {} as ScoreDraft['lane']
  for (const position of POSITIONS) lane[position] = String(table.lane[position])
  return { base, sub, lane }
}

// 한 칸이 -999~999 정수면 그 값, 아니면 null (빈칸·소수·범위 밖)
export function parseCell(text: string): number | null {
  if (!/^-?\d+$/.test(text.trim())) return null
  const value = Number(text.trim())
  return value >= SCORE_MIN && value <= SCORE_MAX ? value + 0 : null // +0: -0을 0으로
}

// 14칸이 모두 올바르면 점수표, 하나라도 아니면 null
export function parseDraft(draft: ScoreDraft): ScoreTable | null {
  const base = {} as ScoreTable['base']
  for (const tier of TIERS) {
    const value = parseCell(draft.base[`${tier}`])
    if (value === null) return null
    base[`${tier}`] = value
  }
  const sub = {} as ScoreTable['sub']
  for (const key of TIER_SUBS) {
    const value = parseCell(draft.sub[key])
    if (value === null) return null
    sub[key] = value
  }
  const lane = {} as ScoreTable['lane']
  for (const position of POSITIONS) {
    const value = parseCell(draft.lane[position])
    if (value === null) return null
    lane[position] = value
  }
  return { base, sub, lane }
}

// 6×3 격자 칸 = 기본 점수 + 우열 보정 (라인 보정 전). 입력이 올바르지 않으면 null
export function gridValue(draft: ScoreDraft, tier: Tier, sub: TierSub): number | null {
  const base = parseCell(draft.base[`${tier}`])
  const bonus = parseCell(draft.sub[sub])
  return base === null || bonus === null ? null : base + bonus
}
