// KDA 비율 표시. ratio가 null일 때 sets > 0이면 데스 0(PERFECT), sets = 0이면 기록 없음("—").
import type { KdaView } from '../../api/publicTypes'
import { EM_DASH } from '../../pages/stats/format'
import '../../styles/stats-common.css'

type KdaTextProps = {
  kda: KdaView
  sets: number // SummaryView.sets: ratio null의 두 의미를 가른다
}

export function KdaText({ kda, sets }: KdaTextProps) {
  if (sets <= 0) return <span className="stat-empty">{EM_DASH}</span>
  if (kda.ratio === null) return <span className="kda-perfect">PERFECT</span>
  return <span>{kda.ratio.toFixed(2)}</span>
}
