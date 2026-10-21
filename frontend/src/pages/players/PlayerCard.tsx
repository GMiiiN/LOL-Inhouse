// 선수 목록의 카드 하나. 누르면 프로필로 간다(기간 쿼리 유지).
import { Link } from 'react-router-dom'
import { POSITION_LABEL, type Position } from '../../api/types'
import type { PlayerListItem } from '../../api/publicTypes'
import { ChampionIcon } from '../../components/stats/ChampionIcon'
import { WinRate } from '../../components/stats/WinRate'
import { formatPercent } from '../stats/format'
import { StatBody } from './StatBody'
import '../../styles/stats.css'

export function PlayerCard({ player, search }: { player: PlayerListItem; search: string }) {
  const { summary } = player
  const laneChips = (Object.entries(player.positions) as [Position, number][])
    .filter(([, sets]) => sets > 0)
    .sort((a, b) => b[1] - a[1])
  const others = player.championCount - player.topChampions.length

  return (
    <Link className="stat-card" to={`/players/${player.playerId}${search}`}>
      <div className="stat-card-head">
        <span className="stat-name">
          {player.displayName}
          {!player.registered && <span className="stats-badge">미등록</span>}
        </span>
        <span className="stat-record">
          {summary.sets}경기 {summary.wins}승 · <WinRate winRate={summary.winRate} sets={summary.sets} />
        </span>
      </div>
      <StatBody summary={summary} />
      {summary.sets > 0 && (
        <ul className="stat-champs">
          {player.topChampions.map((c) => (
            <li key={c.championKey} className="stat-champ">
              <ChampionIcon championKey={c.championKey} size="sm" />
              <span className="stat-champ-bar">
                <span className="stat-champ-fill" style={{ width: formatPercent(c.sets / summary.sets) }} />
              </span>
              <span>
                {formatPercent(c.sets / summary.sets)} · {c.sets}판
              </span>
            </li>
          ))}
        </ul>
      )}
      {others > 0 && <span className="stat-more">그 밖의 챔피언 {others}개</span>}
      <div className="stat-chips">
        {laneChips.map(([position, sets]) => (
          <span key={position} className="stat-chip">
            {POSITION_LABEL[position]} {sets}
          </span>
        ))}
      </div>
    </Link>
  )
}
