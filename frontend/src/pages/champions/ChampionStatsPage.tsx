// 챔피언 통계(/champions?sort&from&to): 밴픽률·픽·밴·승률. 기본 15개, "전체 보기"로 모두. 행을 누르면 그 챔피언의 경기 목록.
import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { getChampionStats, toQueryString } from '../../api/public'
import { championStatsKey } from '../../api/queryKeys'
import { ErrorNotice } from '../../components/ErrorNotice'
import { ChampionIcon } from '../../components/stats/ChampionIcon'
import { PeriodFilter } from '../../components/stats/PeriodFilter'
import { useChampionLookup } from '../../components/stats/useChampionCatalog'
import { WinRate } from '../../components/stats/WinRate'
import { formatPercent } from '../stats/format'
import { usePeriodParams } from '../stats/usePeriodParams'
import { CHAMPION_SORTS, sortChampions, winRateOf, type ChampionSortKey } from './championSort'
import '../../styles/stats.css'

const DEFAULT_LIMIT = 15
const DEFAULT_SORT: ChampionSortKey = 'PICK_BAN_RATE'

export function ChampionStatsPage() {
  const { period } = usePeriodParams()
  const [searchParams, setSearchParams] = useSearchParams()
  const rawSort = searchParams.get('sort')
  const sortKey = CHAMPION_SORTS.find((s) => s.value === rawSort)?.value ?? DEFAULT_SORT
  const [showAll, setShowAll] = useState(false)
  const lookup = useChampionLookup()
  const query = useQuery({ queryKey: championStatsKey(period), queryFn: () => getChampionStats(period) })

  // 정렬은 URL에 두어 새로고침·공유해도 유지한다. 기본값이면 파라미터를 지운다.
  const setSort = (value: ChampionSortKey) =>
    setSearchParams(
      (prev) => {
        const next = new URLSearchParams(prev)
        if (value === DEFAULT_SORT) next.delete('sort')
        else next.set('sort', value)
        return next
      },
      { replace: true },
    )

  const totalSets = query.data?.totalSets ?? 0
  const sorted = sortChampions(query.data?.champions ?? [], sortKey, (key) => lookup(key)?.name ?? key)
  const shown = showAll ? sorted : sorted.slice(0, DEFAULT_LIMIT)

  return (
    <section>
      <div className="stats-bar">
        <div className="stats-group">
          <h1>챔피언</h1>
          <span className="muted-text">총 {totalSets}경기</span>
        </div>
        <PeriodFilter />
      </div>
      <div className="stats-bar">
        <div className="stats-seg" role="group" aria-label="정렬 기준">
          {CHAMPION_SORTS.map((s) => (
            <button key={s.value} type="button" aria-pressed={s.value === sortKey} onClick={() => setSort(s.value)}>
              {s.label}
            </button>
          ))}
        </div>
      </div>
      <p className="stats-help">밴픽률은 전체 경기 중 그 챔피언이 밴이나 픽된 비율이에요.</p>
      {query.isPending && <p className="muted-text">불러오는 중입니다...</p>}
      <ErrorNotice error={query.error} />
      {query.data && sorted.length === 0 && <p className="stat-empty-box">아직 기록된 경기가 없어요.</p>}
      {shown.length > 0 && (
        <div className="stat-table-scroll">
          <table className="stat-table" aria-label="챔피언 통계">
            <thead>
              <tr>
                <th>챔피언</th>
                <th>밴픽률</th>
                <th className="num">픽</th>
                <th className="num">밴</th>
                <th className="num">승률</th>
                <th className="num">승 · 패</th>
              </tr>
            </thead>
            <tbody>
              {shown.map((c) => {
                const rate = totalSets > 0 ? c.setsWithPickOrBan / totalSets : 0
                return (
                  <tr key={c.championKey}>
                    <td>
                      <Link
                        className="stats-link"
                        to={`/records${toQueryString({ champion: c.championKey, from: period.from, to: period.to })}`}
                      >
                        <ChampionIcon championKey={c.championKey} size="sm" showName />
                      </Link>
                    </td>
                    <td>
                      <span className="stat-pbbar">
                        <span className="stat-pbbar-track">
                          <span className="stat-pbbar-fill" style={{ width: formatPercent(rate) }} />
                        </span>
                        <span>{formatPercent(rate)}</span>
                      </span>
                    </td>
                    <td className="num">{c.picks}</td>
                    <td className="num">{c.bans}</td>
                    <td className="num">
                      <WinRate winRate={winRateOf(c)} sets={c.wins + c.losses} />
                    </td>
                    <td className="num">
                      {c.wins}승 {c.losses}패
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}
      {!showAll && sorted.length > DEFAULT_LIMIT && (
        <button type="button" className="header-button" onClick={() => setShowAll(true)}>
          전체 {sorted.length}개 보기
        </button>
      )}
    </section>
  )
}
