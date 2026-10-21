// ▲▼ 툴팁 문구: 이전 티어 → 현재 티어, 같은 티어 안에서의 변화면 "같은 티어 안에서 상승/하락"
import type { Tier, Trend } from '../../api/tierTypes'

export function trendTip(trend: Trend, currentTier: Tier): string {
  if (trend.fromTier !== currentTier) return `${trend.fromTier}티어 → ${currentTier}티어`
  return trend.direction === 'UP' ? '같은 티어 안에서 상승' : '같은 티어 안에서 하락'
}
