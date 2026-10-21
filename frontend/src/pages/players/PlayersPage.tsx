// 선수 목록(/players?from&to): 기간 안 전적 카드 격자. 경기 수 내림차순, 같으면 이름순. 기록 없는 선수는 맨 뒤에 접어 둔다.
import { useQuery } from '@tanstack/react-query'
import { getPlayers, toQueryString } from '../../api/public'
import { publicPlayersKey } from '../../api/queryKeys'
import type { PlayerListItem } from '../../api/publicTypes'
import { ErrorNotice } from '../../components/ErrorNotice'
import { PeriodFilter } from '../../components/stats/PeriodFilter'
import { compareNames } from '../tier/tierSort'
import { usePeriodParams } from '../stats/usePeriodParams'
import { PlayerCard } from './PlayerCard'
import '../../styles/stats.css'

function sortPlayers(players: readonly PlayerListItem[]): PlayerListItem[] {
  return [...players].sort((a, b) => b.summary.sets - a.summary.sets || compareNames(a.displayName, b.displayName))
}

export function PlayersPage() {
  const { period } = usePeriodParams()
  const query = useQuery({ queryKey: publicPlayersKey(period), queryFn: () => getPlayers(period) })
  const sorted = sortPlayers(query.data?.players ?? [])
  const played = sorted.filter((p) => p.summary.sets > 0)
  const idle = sorted.filter((p) => p.summary.sets === 0)
  const linkSearch = toQueryString({ from: period.from, to: period.to }) // 프로필 링크에 기간을 이어 붙인다

  return (
    <section>
      <div className="stats-bar">
        <h1>선수</h1>
        <PeriodFilter />
      </div>
      {query.isPending && <p className="muted-text">불러오는 중입니다...</p>}
      <ErrorNotice error={query.error} />
      {query.data && sorted.length === 0 && <p className="stat-empty-box">아직 등록된 선수가 없어요.</p>}
      {played.length > 0 && (
        <div className="stat-grid">
          {played.map((p) => (
            <PlayerCard key={p.playerId} player={p} search={linkSearch} />
          ))}
        </div>
      )}
      {query.data && sorted.length > 0 && played.length === 0 && (
        <p className="stat-empty-box">이 기간에 기록된 경기가 없어요.</p>
      )}
      {idle.length > 0 && (
        <details className="stat-fold">
          <summary>기록 없음 ({idle.length}명)</summary>
          <div className="stat-grid">
            {idle.map((p) => (
              <PlayerCard key={p.playerId} player={p} search={linkSearch} />
            ))}
          </div>
        </details>
      )}
    </section>
  )
}
