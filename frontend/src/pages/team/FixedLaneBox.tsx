// 자동 모드의 "라인 고정 (선택)": 참가자마다 자동/라인을 고른다. 고정한 사람이 있으면 펼친 채로 둔다.
import { POSITION_LABEL, POSITIONS, type Position } from '../../api/types'
import type { TierPlayer } from '../../api/tierTypes'

const collator = new Intl.Collator('ko')

type Props = {
  players: TierPlayer[] // 현재 참가자
  fix: Record<number, Position>
  onChange: (id: number, lane: Position | null) => void
}

export function FixedLaneBox({ players, fix, onChange }: Props) {
  if (players.length === 0) return null
  const sorted = [...players].sort((a, b) => collator.compare(a.displayName, b.displayName))
  const anyFixed = players.some((player) => fix[player.id])
  return (
    // key로 고정 유무가 바뀔 때 기본 펼침 상태를 다시 따르게 한다
    <details className="tm-fixbox" open={anyFixed || undefined} key={anyFixed ? 'open' : 'closed'}>
      <summary>
        <h3>
          라인 고정 <span className="tm-fix-sub">(선택)</span>
        </h3>
      </summary>
      <p className="tm-help">
        꼭 이 라인으로 가야 하는 사람만 정하세요. 나머지는 자동으로 배정해요. 티어가 없는 라인으로 고정하면 6티어 하 점수로
        계산해요.
      </p>
      <div className="tm-fixgrid">
        {sorted.map((player) => {
          const lane = fix[player.id]
          return (
            <label key={player.id} className={`tm-fixrow${lane ? ' on' : ''}`} htmlFor={`fix-${player.id}`}>
              <b>{player.displayName}</b>
              <select
                id={`fix-${player.id}`}
                value={lane ?? ''}
                onChange={(e) => onChange(player.id, e.target.value === '' ? null : (e.target.value as Position))}
              >
                <option value="">자동</option>
                {POSITIONS.map((position) => {
                  const view = player.lanes[position]
                  return (
                    <option key={position} value={position}>
                      {POSITION_LABEL[position]} · {view ? `${view.tier}티어` : '티어 없음'}
                    </option>
                  )
                })}
              </select>
            </label>
          )
        })}
      </div>
    </details>
  )
}
