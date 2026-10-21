// 라인 티어의 마지막 변경 표시(▲ 상승 / ▼ 하락). 툴팁 문구는 trendTip
import type { Tier, Trend } from '../../api/tierTypes'
import { trendTip } from './trendTip'

export function TrendMark({ trend, currentTier }: { trend: Trend | null; currentTier: Tier }) {
  if (!trend) return null
  const tip = trendTip(trend, currentTier)
  const up = trend.direction === 'UP'
  return (
    <span className={`tier-trend ${up ? 'up' : 'down'}`} title={tip} aria-label={tip}>
      {up ? '▲' : '▼'}
    </span>
  )
}
