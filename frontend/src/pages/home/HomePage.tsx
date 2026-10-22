// 홈(/): 최근 경기 3개 + 라인별 가장 많이 나온 픽(GET /api/home). 둘 다 비면 빈 상태 한 줄만 보여 준다.
import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { getHome } from '../../api/public'
import { HOME_KEY } from '../../api/queryKeys'
import { POSITIONS, POSITION_LABEL } from '../../api/types'
import { ErrorNotice } from '../../components/ErrorNotice'
import { ChampionIcon } from '../../components/stats/ChampionIcon'
import { EM_DASH } from '../stats/format'
import { DuoColumn } from './DuoColumn'
import { RecentSetList } from '../players/RecentSetList'
import '../../styles/stats.css'

export function HomePage() {
  const home = useQuery({ queryKey: HOME_KEY, queryFn: getHome })
  const data = home.data
  const empty = data !== undefined && data.recentSets.length === 0

  return (
    <section className="home-layout">
      <div className="home-main">
      <h1>홈</h1>
      {home.isPending && <p className="muted-text">불러오는 중입니다...</p>}
      <ErrorNotice error={home.error} />
      {empty && <p className="stat-empty-box">아직 기록된 경기가 없어요.</p>}
      {data && !empty && (
        <>
          <h2 className="stat-section-title">라인별 가장 많이 나온 픽</h2>
          <div className="stat-home-picks">
            {POSITIONS.map((position) => {
              const pick = data.topPicksByPosition?.[position] ?? null
              return (
                <div key={position} className="stat-pick-card" aria-label={`${POSITION_LABEL[position]} 최다 픽`}>
                  <span className="stat-pick-lane">{POSITION_LABEL[position]}</span>
                  {pick ? (
                    <>
                      <ChampionIcon championKey={pick.championKey} showName />
                      <span className="stat-record">
                        {pick.picks}회 · {pick.wins}승
                      </span>
                    </>
                  ) : (
                    <span className="stat-empty">{EM_DASH}</span>
                  )}
                </div>
              )
            })}
          </div>
          <h2 className="stat-section-title">최근 경기</h2>
          <RecentSetList sets={data.recentSets} />
          <p className="home-more">
            <Link className="stats-link" to="/records">
              내전 기록 더 보기
            </Link>
          </p>
        </>
      )}
      </div>
      <DuoColumn />
    </section>
  )
}
