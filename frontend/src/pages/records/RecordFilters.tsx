// 기록 필터: 기간(from~to) · 선수 · 챔피언 · 챔피언 범위. 값은 모두 URL 쿼리(player, champion, championScope)에 저장된다.
import { useQuery } from '@tanstack/react-query'
import { useSearchParams } from 'react-router-dom'
import { getPlayers } from '../../api/public'
import type { ChampionScope } from '../../api/publicTypes'
import { publicPlayersKey } from '../../api/queryKeys'
import { PeriodFilter } from '../../components/stats/PeriodFilter'
import { usePublicChampions } from '../../components/stats/useChampionCatalog'
import '../../styles/records.css'

type RecordFiltersProps = {
  player: string | undefined
  champion: string | undefined
  championScope: ChampionScope | undefined
  hasFilter: boolean
  onClear: () => void
}

export function RecordFilters({ player, champion, championScope, hasFilter, onClear }: RecordFiltersProps) {
  const [, setSearchParams] = useSearchParams()
  const players = useQuery({ queryKey: publicPlayersKey({}), queryFn: () => getPlayers({}) })
  const champions = usePublicChampions()

  // 값이 비면 그 파라미터를 지운다. 이력은 쌓지 않는다.
  // 선수나 챔피언을 지우면 범위 선택기가 사라지므로 숨은 championScope도 함께 지운다.
  const setParam = (key: string, value: string) =>
    setSearchParams(
      (prev) => {
        const params = new URLSearchParams(prev)
        if (value) params.set(key, value)
        else params.delete(key)
        if (!value && (key === 'player' || key === 'champion')) params.delete('championScope')
        return params
      },
      { replace: true },
    )

  const playerList = players.data?.players ?? []
  const championList = champions.data?.champions ?? []

  return (
    <div className="record-filters" role="search" aria-label="기록 필터">
      <PeriodFilter />
      <label>
        선수
        <select value={player ?? ''} onChange={(e) => setParam('player', e.target.value)}>
          <option value="">전체</option>
          {player && !playerList.some((p) => String(p.playerId) === player) && <option value={player}>선수 #{player}</option>}
          {playerList.map((p) => (
            <option key={p.playerId} value={String(p.playerId)}>
              {p.displayName}
            </option>
          ))}
        </select>
      </label>
      <label>
        챔피언
        <select value={champion ?? ''} onChange={(e) => setParam('champion', e.target.value)}>
          <option value="">전체</option>
          {champion && !championList.some((c) => c.id === champion) && <option value={champion}>{champion}</option>}
          {championList.map((c) => (
            <option key={c.id} value={c.id}>
              {c.name}
            </option>
          ))}
        </select>
      </label>
      {player && champion && (
        <label>
          범위
          <select
            value={championScope ?? 'ANY'}
            onChange={(e) => setParam('championScope', e.target.value === 'PLAYER_PICK' ? 'PLAYER_PICK' : '')}
          >
            <option value="ANY">세트의 픽 또는 밴</option>
            <option value="PLAYER_PICK">이 선수가 픽한 경기</option>
          </select>
        </label>
      )}
      <button type="button" className="record-filters-clear" onClick={onClear} disabled={!hasFilter}>
        조건 지우기
      </button>
    </div>
  )
}
