// 승률 표시. 50% 이상은 wgood, 미만은 wbad, 세트가 0이면 "—".
import { EM_DASH, formatPercent } from '../../pages/stats/format'
import '../../styles/stats-common.css'

type WinRateProps = {
  winRate: number // 0~1
  sets: number
}

export function WinRate({ winRate, sets }: WinRateProps) {
  if (sets <= 0) return <span className="stat-empty">{EM_DASH}</span>
  // 색 기준도 화면에 보이는 반올림 값으로 판단한다(49.6%가 "50%"로 보이는데 붉으면 어색하다)
  const rounded = Math.round(winRate * 100)
  return <span className={rounded >= 50 ? 'wgood' : 'wbad'}>{formatPercent(winRate)}</span>
}
