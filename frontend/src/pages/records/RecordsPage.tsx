// 내전 기록(/records?from&to&player&champion&championScope): 묶음 → 대결 → 세트 줄. 10묶음씩 "더 보기".
import { useInfiniteQuery } from '@tanstack/react-query'
import { useMemo } from 'react'
import { useSearchParams } from 'react-router-dom'
import { getRecords, toRecordsQuery } from '../../api/public'
import type { ChampionScope, RecordsPageParams } from '../../api/publicTypes'
import { recordsKey } from '../../api/queryKeys'
import { DuelBlock } from '../../components/record/DuelBlock'
import { ErrorNotice } from '../../components/ErrorNotice'
import { usePeriodParams } from '../stats/usePeriodParams'
import { CsvLink } from './CsvLink'
import { RecordFilters } from './RecordFilters'
import '../../styles/records.css'

export function RecordsPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const { period } = usePeriodParams()
  const player = searchParams.get('player') || undefined
  const champion = searchParams.get('champion') || undefined
  const championScope: ChampionScope | undefined =
    searchParams.get('championScope') === 'PLAYER_PICK' ? 'PLAYER_PICK' : undefined

  // 화면의 player → API의 playerId. 문자열 파라미터가 같으면 같은 객체를 유지해 쿼리 키가 흔들리지 않게 한다.
  const query = useMemo(() => {
    const params: RecordsPageParams = { ...period, player, champion, championScope }
    return toRecordsQuery(params)
  }, [period, player, champion, championScope])

  const records = useInfiniteQuery({
    queryKey: recordsKey(query),
    queryFn: ({ pageParam }) => getRecords({ ...query, before: pageParam }),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (last) => last.nextBefore ?? undefined,
  })

  const hasFilter = Boolean(period.from || period.to || player || champion)
  const clearAll = () => setSearchParams(new URLSearchParams(), { replace: true })
  const sessions = records.data?.pages.flatMap((page) => page.sessions) ?? []

  return (
    <section className="records-page">
      <div className="page-title-row">
        <h1>내전 기록</h1>
        <CsvLink query={query} />
      </div>
      <RecordFilters
        player={player}
        champion={champion}
        championScope={championScope}
        hasFilter={hasFilter}
        onClear={clearAll}
      />
      {records.isError && <ErrorNotice error={records.error} />}
      {records.isPending && <p className="muted-text">불러오는 중…</p>}
      {records.isSuccess && sessions.length === 0 && (
        <div className="records-empty">
          {hasFilter ? (
            <>
              <p>조건에 맞는 경기가 없어요.</p>
              <button type="button" onClick={clearAll}>
                조건 지우기
              </button>
            </>
          ) : (
            <p>아직 기록된 경기가 없어요.</p>
          )}
        </div>
      )}
      {sessions.map((session) => (
        <section key={session.sessionId} className="session-block" aria-label={`${session.startDate} 내전`}>
          <h2 className="session-date">{session.startDate}</h2>
          {session.duels.map((duel) => (
            <DuelBlock key={duel.duelId} duel={duel} />
          ))}
        </section>
      ))}
      {records.hasNextPage && (
        <button
          type="button"
          className="records-more"
          onClick={() => records.fetchNextPage()}
          disabled={records.isFetchingNextPage}
        >
          {records.isFetchingNextPage ? '불러오는 중…' : '더 보기'}
        </button>
      )}
    </section>
  )
}
