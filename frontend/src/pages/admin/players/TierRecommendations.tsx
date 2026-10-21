// 티어 조정 추천 카드(선수 관리). 서버가 라인별 승률·KDA로 한 칸씩 추천하고, 적용은 프로필 PUT(recordChange true)이다.
// 넘기기는 이 브라우저(localStorage)에만 기록한다: "선수:라인" → 넘길 때의 경기 수. 경기가 더 쌓이면 다시 보인다.
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { PLAYERS_KEY, PLAYER_PROFILES_KEY, PUBLIC_KEY, TIER_LIST_KEY, TIER_RECOMMENDATIONS_KEY } from '../../../api/queryKeys'
import { listTierRecommendations, updateProfile } from '../../../api/tier'
import { TIER_SUB_LABEL, type LaneValue, type PlayerProfile, type TierRecommendation } from '../../../api/tierTypes'
import { POSITION_LABEL } from '../../../api/types'
import { ErrorNotice } from '../../../components/ErrorNotice'

export const TIER_REC_DISMISSED_KEY = 'tier-rec-dismissed'
export const TIER_REC_DEFAULT_COUNT = 6

type Dismissed = Record<string, number>

const recKey = (rec: TierRecommendation) => `${rec.playerId}:${rec.position}`
const laneText = (lane: LaneValue) => `${lane.tier}티어 ${TIER_SUB_LABEL[lane.sub]}`

function loadDismissed(): Dismissed {
  try {
    const parsed: unknown = JSON.parse(localStorage.getItem(TIER_REC_DISMISSED_KEY) ?? '{}')
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? (parsed as Dismissed) : {}
  } catch {
    return {}
  }
}

function saveDismissed(value: Dismissed) {
  try {
    localStorage.setItem(TIER_REC_DISMISSED_KEY, JSON.stringify(value))
  } catch {
    // 저장소가 막혀 있으면 이번 화면에서만 숨긴다
  }
}

// editing: 편집기가 열려 있으면 적용을 막는다(열린 폼이 이전 티어로 다시 저장해 적용을 되돌리지 않게)
export function TierRecommendations({ profiles, editing = false }: { profiles: PlayerProfile[]; editing?: boolean }) {
  const queryClient = useQueryClient()
  const recs = useQuery({ queryKey: TIER_RECOMMENDATIONS_KEY, queryFn: listTierRecommendations })
  const [dismissed, setDismissed] = useState(loadDismissed)
  const [showAll, setShowAll] = useState(false)

  const apply = useMutation({
    mutationFn: (rec: TierRecommendation) => {
      const profile = profiles.find((p) => p.id === rec.playerId)
      if (!profile) throw new Error('선수 정보를 찾을 수 없어요.')
      return updateProfile(profile.id, {
        realName: profile.realName,
        mainPosition: profile.mainPosition,
        lanes: { ...profile.lanes, [rec.position]: rec.to },
        nicknames: profile.aliases.map((alias) => alias.nickname),
        flagged: profile.flagged,
        recordChange: true,
      })
    },
    onSuccess: () =>
      Promise.all(
        [TIER_RECOMMENDATIONS_KEY, TIER_LIST_KEY, PLAYER_PROFILES_KEY, PLAYERS_KEY, PUBLIC_KEY].map((queryKey) =>
          queryClient.invalidateQueries({ queryKey }),
        ),
      ),
  })

  // 추천을 받은 뒤 티어가 이미 바뀌었으면 오래된 추천으로 덮어쓰지 않고 새로 불러온다
  const [staleNotice, setStaleNotice] = useState(false)
  function applyRec(rec: TierRecommendation) {
    const current = profiles.find((p) => p.id === rec.playerId)?.lanes[rec.position]
    if (!current || current.tier !== rec.from.tier || current.sub !== rec.from.sub) {
      setStaleNotice(true)
      void queryClient.invalidateQueries({ queryKey: TIER_RECOMMENDATIONS_KEY })
      return
    }
    setStaleNotice(false)
    apply.mutate(rec)
  }

  function dismiss(rec: TierRecommendation) {
    const next = { ...dismissed, [recKey(rec)]: rec.games }
    setDismissed(next)
    saveDismissed(next)
  }

  const visible = (recs.data ?? []).filter((rec) => !((dismissed[recKey(rec)] ?? -1) >= rec.games))
  const shown = showAll ? visible : visible.slice(0, TIER_REC_DEFAULT_COUNT)

  return (
    <section className="panel tier-rec" aria-label="티어 조정 추천">
      <h2>티어 조정 추천</h2>
      <p className="tier-help">
        라인별로 3경기 이상 쌓이면 승률과 KDA를 보고 한 칸씩 추천해요. 티어를 바꾼 뒤에는 그다음 날 경기부터 다시 계산해요. 넘기면 그 라인에
        경기가 더 쌓일 때까지 다시 안 떠요.
      </p>
      {recs.isPending && <p className="muted-text">불러오는 중입니다...</p>}
      <ErrorNotice error={recs.error} />
      <ErrorNotice error={apply.error} />
      {staleNotice && (
        <p className="error-notice" role="alert">
          티어가 이미 바뀌었어요. 추천을 새로 불러올게요.
        </p>
      )}
      {recs.data && visible.length === 0 && <p className="muted-text">지금은 추천할 사람이 없어요</p>}
      <ul className="tier-rec-list">
        {shown.map((rec) => {
          const losses = rec.games - rec.wins
          const percent = Math.round((rec.wins / rec.games) * 100)
          return (
            <li key={recKey(rec)} className={`tier-rec-card ${rec.direction === 'UP' ? 'tier-rec-up' : 'tier-rec-down'}`}>
              <div className="tier-rec-head">
                <strong>{rec.displayName}</strong>
                <span className="badge">{POSITION_LABEL[rec.position]}</span>
                <span className="tier-rec-direction">{rec.direction === 'UP' ? '▲ 상향' : '▼ 하향'}</span>
              </div>
              <p className="tier-rec-change">
                {laneText(rec.from)} → <strong>{laneText(rec.to)}</strong>
              </p>
              <p className="muted-text">
                {rec.games}경기 {rec.wins}승 {losses}패 ({percent}%) · KDA {rec.kda.toFixed(2)} (라인 평균{' '}
                {rec.laneAvgKda.toFixed(2)})
              </p>
              <div className="tier-rec-actions">
                <button
                  type="button"
                  className="primary-button"
                  aria-label={`${rec.displayName} ${POSITION_LABEL[rec.position]} 적용`}
                  disabled={apply.isPending || editing}
                  title={editing ? '편집기를 닫은 뒤 적용할 수 있어요' : undefined}
                  onClick={() => applyRec(rec)}
                >
                  적용
                </button>
                <button
                  type="button"
                  className="header-button"
                  aria-label={`${rec.displayName} ${POSITION_LABEL[rec.position]} 넘기기`}
                  onClick={() => dismiss(rec)}
                >
                  넘기기
                </button>
              </div>
            </li>
          )
        })}
      </ul>
      {visible.length > TIER_REC_DEFAULT_COUNT && (
        <button type="button" className="header-button" onClick={() => setShowAll(!showAll)}>
          {showAll ? '접기' : `전체 보기 (${visible.length})`}
        </button>
      )}
    </section>
  )
}
