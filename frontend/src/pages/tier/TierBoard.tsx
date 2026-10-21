// 티어표: 6개 티어 행 × 5개 라인 열. 칸 안에는 이름·변동 표시·점수 칩. onEdit이 있으면 칩이 수정 버튼이 된다.
import type { CSSProperties } from 'react'
import { POSITIONS, POSITION_LABEL } from '../../api/types'
import { TIERS, type TierPlayer } from '../../api/tierTypes'
import { FlaggedMark } from '../../components/FlaggedMark'
import { TrendMark } from './TrendMark'
import { boardCell } from './tierSort'

type TierBoardProps = {
  players: TierPlayer[]
  onEdit?: (playerId: number) => void
}

export function TierBoard({ players, onEdit }: TierBoardProps) {
  return (
    <div className="tier-scroll">
      <table className="tier-board" aria-label="라인별 티어표">
        <thead>
          <tr>
            <th className="tier-corner" />
            {POSITIONS.map((position) => (
              <th key={position} scope="col">
                {POSITION_LABEL[position]}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {TIERS.map((tier) => (
            <tr key={tier} style={{ '--tc': `var(--t${tier})` } as CSSProperties}>
              <th scope="row">
                <span className="tier-big">{tier}</span>
                <span className="tier-lbl">티어</span>
              </th>
              {POSITIONS.map((position) => (
                <td key={position} data-testid={`cell-${tier}-${position}`}>
                  <div className="tier-cell">
                    {boardCell(players, tier, position).map(({ player, lane }) => {
                      const inner = (
                        <>
                          <span className="tier-chip-name">
                            <span className="tier-chip-text">{player.displayName}</span>
                            <FlaggedMark flagged={player.flagged} />
                            <TrendMark trend={lane.trend} currentTier={lane.tier} />
                          </span>
                          <span className="tier-chip-score">{lane.score}</span>
                        </>
                      )
                      return onEdit ? (
                        <button
                          key={player.id}
                          type="button"
                          className="tier-chip"
                          aria-label={`${player.displayName} 수정`}
                          title={`${player.displayName} 수정`}
                          onClick={() => onEdit(player.id)}
                        >
                          {inner}
                        </button>
                      ) : (
                        <div key={player.id} className="tier-chip">
                          {inner}
                        </div>
                      )
                    })}
                  </div>
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
