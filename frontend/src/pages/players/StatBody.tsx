// 요약 한 덩어리(선수 카드·프로필 공용): KDA 큰 숫자, 경기당 K/D/A, 평균 CS·골드
import type { SummaryView } from '../../api/publicTypes'
import { KdaText } from '../../components/stats/KdaText'
import { EM_DASH, formatGold, formatPerGame } from '../stats/format'
import '../../styles/stats.css'

export function StatBody({ summary }: { summary: SummaryView }) {
  const { sets, kda } = summary
  return (
    <>
      <div className="stat-kda">
        <KdaText kda={kda} sets={sets} />
        <span className="stat-kda-label">KDA</span>
      </div>
      <div className="stat-kv">
        <span>
          경기당 <b>{formatPerGame(kda.kills, sets)}</b> / <b>{formatPerGame(kda.deaths, sets)}</b> /{' '}
          <b>{formatPerGame(kda.assists, sets)}</b>
        </span>
        <span>
          CS <b>{formatPerGame(summary.cs, sets)}</b>
        </span>
        <span>
          골드 <b>{sets > 0 ? formatGold(summary.gold / sets) : EM_DASH}</b>
        </span>
      </div>
    </>
  )
}
