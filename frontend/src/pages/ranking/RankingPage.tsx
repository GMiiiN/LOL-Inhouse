// 랭킹(/ranking?position&sort&from&to): 포지션 탭 + 정렬. 순서는 서버가 정한 그대로 그린다(rank null = 표본 미달 → "—").
import { useQuery } from '@tanstack/react-query'
import { Link, useSearchParams } from 'react-router-dom'
import { getRankings } from '../../api/public'
import { rankingsKey } from '../../api/queryKeys'
import type { RankingPosition, RankingSort } from '../../api/publicTypes'
import { POSITIONS, POSITION_LABEL } from '../../api/types'
import { ErrorNotice } from '../../components/ErrorNotice'
import { KdaText } from '../../components/stats/KdaText'
import { PeriodFilter } from '../../components/stats/PeriodFilter'
import { WinRate } from '../../components/stats/WinRate'
import { EM_DASH } from '../stats/format'
import { usePeriodParams } from '../stats/usePeriodParams'
import '../../styles/stats.css'

const TABS: RankingPosition[] = ['ALL', ...POSITIONS]
const TAB_LABEL = (tab: RankingPosition) => (tab === 'ALL' ? '전체' : POSITION_LABEL[tab])
const SORTS: { value: RankingSort; label: string }[] = [
  { value: 'WIN_RATE', label: '승률' },
  { value: 'KDA', label: 'KDA' },
  { value: 'SETS', label: '경기 수' },
]
const DEFAULT_TAB: RankingPosition = 'ALL'
const DEFAULT_SORT: RankingSort = 'WIN_RATE'

export function RankingPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const { period } = usePeriodParams()
  const rawPosition = searchParams.get('position')
  const rawSort = searchParams.get('sort')
  const position = TABS.find((t) => t === rawPosition) ?? DEFAULT_TAB
  const sort = SORTS.find((s) => s.value === rawSort)?.value ?? DEFAULT_SORT

  const query = { position, sort, ...period }
  const rankings = useQuery({ queryKey: rankingsKey(query), queryFn: () => getRankings(query) })

  // 기본값으로 돌아가면 파라미터를 지워 URL을 짧게 둔다. 기간 등 다른 파라미터는 건드리지 않는다.
  const setParam = (key: 'position' | 'sort', value: string, defaultValue: string) =>
    setSearchParams(
      (prev) => {
        const next = new URLSearchParams(prev)
        if (value === defaultValue) next.delete(key)
        else next.set(key, value)
        return next
      },
      { replace: true },
    )

  const entries = rankings.data?.entries ?? []

  return (
    <section>
      <div className="stats-bar">
        <h1>승률</h1>
        <PeriodFilter />
      </div>
      <div className="stats-bar">
        <div className="stats-seg" role="group" aria-label="포지션">
          {TABS.map((tab) => (
            <button key={tab} type="button" aria-pressed={tab === position} onClick={() => setParam('position', tab, DEFAULT_TAB)}>
              {TAB_LABEL(tab)}
            </button>
          ))}
        </div>
        <div className="stats-seg" role="group" aria-label="정렬">
          {SORTS.map((s) => (
            <button key={s.value} type="button" aria-pressed={s.value === sort} onClick={() => setParam('sort', s.value, DEFAULT_SORT)}>
              {s.label}
            </button>
          ))}
        </div>
      </div>
      {rankings.isPending && <p className="muted-text">불러오는 중입니다...</p>}
      <ErrorNotice error={rankings.error} />
      {rankings.data && (
        <p className="stats-help">
          {rankings.data.minSets}세트 미만은 순위 없이 기록만 보여요. 전체 {rankings.data.totalSets}세트 기준이에요.
        </p>
      )}
      {rankings.data && entries.length === 0 && <p className="stat-empty-box">아직 기록된 경기가 없어요.</p>}
      {entries.length > 0 && (
        <div className="stat-table-scroll">
          <table className="stat-table" aria-label="승률">
            <thead>
              <tr>
                <th className="num">순위</th>
                <th>이름</th>
                <th className="num">경기</th>
                <th className="num">승 · 패</th>
                <th className="num">승률</th>
                <th className="num">KDA</th>
              </tr>
            </thead>
            <tbody>
              {entries.map((entry) => (
                <tr key={entry.playerId}>
                  <td className="num">{entry.rank ?? EM_DASH}</td>
                  <td>
                    <Link className="stats-link" to={`/players/${entry.playerId}`}>
                      {entry.displayName}
                    </Link>
                    {!entry.registered && <span className="stats-badge">미등록</span>}
                  </td>
                  <td className="num">{entry.summary.sets}</td>
                  <td className="num">
                    {entry.summary.wins}승 {entry.summary.losses}패
                  </td>
                  <td className="num">
                    <WinRate winRate={entry.summary.winRate} sets={entry.summary.sets} />
                  </td>
                  <td className="num">
                    <KdaText kda={entry.summary.kda} sets={entry.summary.sets} />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  )
}
