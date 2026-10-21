// 사람별 표: 이름순, 라인마다 "N티어 ▲ 점수"(상·중·하 글자는 숨김). 티어가 없으면 "—".
// onEdit이 있으면 수정 열, onDelete가 있으면 삭제 열(두 번 눌러야 삭제: "삭제" → "정말 삭제")이 생긴다.
import { useState, type CSSProperties } from 'react'
import { POSITIONS, POSITION_LABEL } from '../../api/types'
import type { TierPlayer } from '../../api/tierTypes'
import { FlaggedMark } from '../../components/FlaggedMark'
import { TrendMark } from './TrendMark'
import { sortByName } from './tierSort'

type TierPeopleTableProps = {
  players: TierPlayer[]
  onEdit?: (playerId: number) => void
  onDelete?: (playerId: number) => void
}

export function TierPeopleTable({ players, onEdit, onDelete }: TierPeopleTableProps) {
  const [confirmId, setConfirmId] = useState<number | null>(null)
  const hasActions = Boolean(onEdit || onDelete)

  return (
    <div className="tier-scroll">
      <table className="tier-people" aria-label="사람별 티어">
        <thead>
          <tr>
            <th scope="col">이름</th>
            {POSITIONS.map((position) => (
              <th key={position} scope="col">
                {POSITION_LABEL[position]}
              </th>
            ))}
            {hasActions && <th scope="col" />}
          </tr>
        </thead>
        <tbody>
          {sortByName(players).map((player) => (
            <tr key={player.id}>
              <td className="tier-person-name">{player.displayName}
                <FlaggedMark flagged={player.flagged} />
              </td>
              {POSITIONS.map((position) => {
                const lane = player.lanes[position]
                return (
                  <td key={position}>
                    {lane ? (
                      <span className="tier-tag" style={{ '--tc': `var(--t${lane.tier})` } as CSSProperties}>
                        {lane.tier}티어
                        <TrendMark trend={lane.trend} currentTier={lane.tier} />
                        <span className="tier-tag-score">{lane.score}</span>
                      </span>
                    ) : (
                      <span className="tier-empty">—</span>
                    )}
                  </td>
                )
              })}
              {hasActions && (
                <td className="tier-actions">
                  {onEdit && (
                    <button
                      type="button"
                      className="header-button"
                      aria-label={`${player.displayName} 수정`}
                      onClick={() => onEdit(player.id)}
                    >
                      수정
                    </button>
                  )}{' '}
                  {onDelete &&
                    (confirmId === player.id ? (
                      <button
                        type="button"
                        className="header-button tier-danger"
                        aria-label={`${player.displayName} 정말 삭제`}
                        onClick={() => {
                          setConfirmId(null)
                          onDelete(player.id)
                        }}
                      >
                        정말 삭제
                      </button>
                    ) : (
                      <button
                        type="button"
                        className="header-button tier-danger"
                        aria-label={`${player.displayName} 삭제`}
                        onClick={() => setConfirmId(player.id)}
                      >
                        삭제
                      </button>
                    ))}
                </td>
              )}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
