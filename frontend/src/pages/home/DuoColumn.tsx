// 홈 왼쪽 열: 최고·최악의 듀오 각 3쌍(GET /api/duos, 기간 전체). 함께 minSets 세트 이상인 쌍만 서버가 준다.
import { useQuery } from '@tanstack/react-query'
import { getDuos } from '../../api/public'
import { duosKey } from '../../api/queryKeys'
import type { DuoEntry } from '../../api/publicTypes'
import { ErrorNotice } from '../../components/ErrorNotice'
import { WinRate } from '../../components/stats/WinRate'
import '../../styles/stats.css'

const ALL_TIME = {}

// tone: 1위 카드 테두리 강조 색(최고 = 노랑, 최악 = 빨강)
function DuoList({ title, entries, tone }: { title: string; entries: DuoEntry[]; tone: 'best' | 'worst' }) {
  return (
    <section aria-label={title}>
      <h2>{title}</h2>
      {entries.length === 0 ? (
        <p className="stat-empty">아직 없어요.</p>
      ) : (
        <ol className="duo-list">
          {entries.map((duo, index) => (
            <li
              key={`${duo.players[0].playerId}-${duo.players[1].playerId}`}
              className={`duo-item${index === 0 ? ` duo-top duo-top-${tone}` : ''}`}
            >
              <div className="duo-names">{duo.players[0].displayName} + {duo.players[1].displayName}</div>
              <div className="duo-record">
                {duo.sets}세트 · {duo.wins}승 {duo.losses}패 · <WinRate winRate={duo.winRate} sets={duo.sets} />
              </div>
            </li>
          ))}
        </ol>
      )}
    </section>
  )
}

export function DuoColumn() {
  const duos = useQuery({ queryKey: duosKey(ALL_TIME), queryFn: () => getDuos(ALL_TIME) })
  const data = duos.data
  return (
    <aside className="home-duos" aria-label="듀오">
      <ErrorNotice error={duos.error} />
      {data && data.best.length === 0 && data.worst.length === 0 && (
        <p className="stat-empty-box">아직 함께 {data.minSets}세트 이상 뛴 듀오가 없어요.</p>
      )}
      {data && (data.best.length > 0 || data.worst.length > 0) && (
        <>
          <p className="stats-help">같은 팀으로 함께 {data.minSets}세트 이상 뛴 두 사람만 보여요.</p>
          <DuoList title="최고의 듀오" entries={data.best} tone="best" />
          <DuoList title="최악의 듀오" entries={data.worst} tone="worst" />
        </>
      )}
    </aside>
  )
}
