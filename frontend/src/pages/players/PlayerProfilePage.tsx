// 선수 프로필(/players/:playerId?from&to): 라인 티어, 요약, 라인별 카드, 챔피언별 표, 대결별 결과, 최근 5경기
import { useQuery } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { Link, useParams } from 'react-router-dom'
import { ApiError } from '../../api/client'
import { getPlayer, toQueryString } from '../../api/public'
import { publicPlayerKey } from '../../api/queryKeys'
import type { PlayerRecord } from '../../api/publicTypes'
import { TIER_SUB_LABEL } from '../../api/tierTypes'
import { POSITIONS, POSITION_LABEL } from '../../api/types'
import { ErrorNotice } from '../../components/ErrorNotice'
import { KdaText } from '../../components/stats/KdaText'
import { PeriodFilter } from '../../components/stats/PeriodFilter'
import { WinRate } from '../../components/stats/WinRate'
import { TrendMark } from '../tier/TrendMark'
import { EM_DASH, formatGold, formatPercent, formatPerGame } from '../stats/format'
import { usePeriodParams } from '../stats/usePeriodParams'
import { ChampionTable } from './ChampionTable'
import { DuelResultList } from './DuelResultList'
import { LaneCards } from './LaneCards'
import { RecentSetList } from './RecentSetList'
import '../../styles/stats.css'

function LaneTiers({ player }: { player: PlayerRecord }) {
  return (
    <ul className="stat-lane-tiers" aria-label="라인 티어">
      {POSITIONS.map((position) => {
        const lane = player.laneTiers?.[position] ?? null
        return (
          <li key={position} className="stat-lane-tier">
            {POSITION_LABEL[position]}{' '}
            {lane ? (
              <>
                <b>
                  {lane.tier}티어 {TIER_SUB_LABEL[lane.sub]}
                </b>
                <TrendMark trend={lane.trend} currentTier={lane.tier} />
              </>
            ) : (
              EM_DASH
            )}
          </li>
        )
      })}
    </ul>
  )
}

function Tile({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="stat-tile">
      <div className="stat-tile-label">{label}</div>
      <div className="stat-tile-value">{children}</div>
    </div>
  )
}

function SummaryTiles({ player }: { player: PlayerRecord }) {
  const { summary } = player
  const { sets, kda } = summary
  return (
    <div className="stat-summary">
      <Tile label="참여 세트">{sets}</Tile>
      <Tile label="승 · 패">
        {summary.wins}승 {summary.losses}패
      </Tile>
      <Tile label="승률">
        <WinRate winRate={summary.winRate} sets={sets} />
      </Tile>
      <Tile label="KDA">
        <KdaText kda={kda} sets={sets} />
      </Tile>
      <Tile label="경기당 K/D/A">
        {formatPerGame(kda.kills, sets)} / {formatPerGame(kda.deaths, sets)} / {formatPerGame(kda.assists, sets)}
      </Tile>
      <Tile label="평균 CS">{formatPerGame(summary.cs, sets)}</Tile>
      <Tile label="평균 골드">{sets > 0 ? formatGold(summary.gold / sets) : EM_DASH}</Tile>
      <Tile label="경기 참여율">{formatPercent(player.participationRate)}</Tile>
      <Tile label="뛴 라인">{player.byPosition.length}</Tile>
    </div>
  )
}

export function PlayerProfilePage() {
  const { playerId: rawId } = useParams()
  const { period } = usePeriodParams()
  const playerId = rawId !== undefined && /^\d+$/.test(rawId) ? Number(rawId) : undefined
  const query = useQuery({
    queryKey: publicPlayerKey(playerId ?? 0, period),
    queryFn: () => getPlayer(playerId!, period),
    enabled: playerId !== undefined,
  })
  const player = query.data

  if (playerId === undefined || (query.error instanceof ApiError && query.error.status === 404)) {
    return (
      <section>
        <h1>선수를 찾을 수 없어요</h1>
        <p className="muted-text">
          없는 선수이거나 주소가 잘못됐어요. <Link to="/players">선수 목록으로</Link>
        </p>
      </section>
    )
  }

  return (
    <section>
      <div className="stats-bar">
        <div className="stat-profile-head">
          <h1>
            {player?.displayName ?? '선수 프로필'}
            {player && !player.registered && <span className="stats-badge">미등록</span>}
          </h1>
          {player?.mainPosition && <span className="stat-chip">주력 {POSITION_LABEL[player.mainPosition]}</span>}
        </div>
        <PeriodFilter />
      </div>
      {query.isPending && <p className="muted-text">불러오는 중입니다...</p>}
      <ErrorNotice error={query.error} />
      {player && (
        <>
          <LaneTiers player={player} />
          {player.summary.sets === 0 ? (
            <p className="stat-empty-box">아직 기록된 경기가 없어요.</p>
          ) : (
            <>
              <h2 className="stat-section-title">요약</h2>
              <SummaryTiles player={player} />
              <h2 className="stat-section-title">라인별 기록</h2>
              <LaneCards lanes={player.byPosition} />
              <h2 className="stat-section-title">챔피언별 기록</h2>
              <ChampionTable
                playerId={player.playerId}
                champions={player.byChampion}
                byPosition={player.byChampionByPosition}
                period={period}
              />
              <h2 className="stat-section-title">대결별 개인 결과</h2>
              <DuelResultList duels={player.duels} />
              <div className="stats-bar stat-section-title">
                <h2 style={{ margin: 0 }}>최근 경기</h2>
                <Link className="stats-link" to={`/records${toQueryString({ player: player.playerId })}`}>
                  전체 보기
                </Link>
              </div>
              <RecentSetList sets={player.recentSets} />
            </>
          )}
        </>
      )}
    </section>
  )
}
