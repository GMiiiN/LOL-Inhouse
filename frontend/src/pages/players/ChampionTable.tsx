// 프로필의 챔피언별 표. 행을 누르면 그 선수가 직접 픽한 그 챔피언의 경기 목록(현재 기간 유지)으로 간다.
import { Link } from 'react-router-dom'
import type { ChampionSummary, Period } from '../../api/publicTypes'
import { toQueryString } from '../../api/public'
import { ChampionIcon } from '../../components/stats/ChampionIcon'
import { KdaText } from '../../components/stats/KdaText'
import { WinRate } from '../../components/stats/WinRate'
import '../../styles/stats.css'

function championRecordsHref(playerId: number, championKey: string, period: Period): string {
  return `/records${toQueryString({
    player: playerId,
    champion: championKey,
    championScope: 'PLAYER_PICK',
    from: period.from,
    to: period.to,
  })}`
}

type ChampionTableProps = { playerId: number; champions: ChampionSummary[]; period: Period }

export function ChampionTable({ playerId, champions, period }: ChampionTableProps) {
  return (
    <div className="stat-table-scroll">
      <table className="stat-table" aria-label="챔피언별 기록">
        <thead>
          <tr>
            <th>챔피언</th>
            <th className="num">판 수</th>
            <th className="num">승</th>
            <th className="num">패</th>
            <th className="num">승률</th>
            <th className="num">KDA</th>
          </tr>
        </thead>
        <tbody>
          {champions.map(({ championKey, summary }) => (
            <tr key={championKey}>
              <td>
                <Link className="stats-link" to={championRecordsHref(playerId, championKey, period)}>
                  <ChampionIcon championKey={championKey} size="sm" showName />
                </Link>
              </td>
              <td className="num">{summary.sets}</td>
              <td className="num">{summary.wins}</td>
              <td className="num">{summary.losses}</td>
              <td className="num">
                <WinRate winRate={summary.winRate} sets={summary.sets} />
              </td>
              <td className="num">
                <KdaText kda={summary.kda} sets={summary.sets} />
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
