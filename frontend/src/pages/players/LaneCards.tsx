// 라인별 기록 카드: 경기 수가 많은 라인부터(같으면 탑→서포터 순)
import { POSITIONS, POSITION_LABEL } from '../../api/types'
import type { PositionSummary } from '../../api/publicTypes'
import { ChampionIcon } from '../../components/stats/ChampionIcon'
import { WinRate } from '../../components/stats/WinRate'
import { StatBody } from './StatBody'
import '../../styles/stats.css'

function sortLanes(lanes: readonly PositionSummary[]): PositionSummary[] {
  return [...lanes].sort(
    (a, b) => b.summary.sets - a.summary.sets || POSITIONS.indexOf(a.position) - POSITIONS.indexOf(b.position),
  )
}

export function LaneCards({ lanes }: { lanes: PositionSummary[] }) {
  return (
    <div className="stat-lane-grid">
      {sortLanes(lanes).map(({ position, summary, topChampions }) => (
        <article key={position} className="stat-lane-card" aria-label={`${POSITION_LABEL[position]} 기록`}>
          <h3>{POSITION_LABEL[position]}</h3>
          <span className="stat-record">
            {summary.sets}경기 {summary.wins}승 · <WinRate winRate={summary.winRate} sets={summary.sets} />
          </span>
          <StatBody summary={summary} />
          <div className="stat-chips">
            {topChampions.map((c) => (
              <span key={c.championKey} title={`${c.sets}판`}>
                <ChampionIcon championKey={c.championKey} size="sm" />
              </span>
            ))}
          </div>
        </article>
      ))}
    </div>
  )
}
