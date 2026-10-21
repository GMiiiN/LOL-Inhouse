// 자동 모드 참가자 카드: 클릭하면 담기고(순서 유지) n/10을 보여 준다. 이름은 한국어 정렬.
import { FlaggedMark } from '../../components/FlaggedMark'
import { POSITION_LABEL, POSITIONS } from '../../api/types'
import type { TierPlayer } from '../../api/tierTypes'
import { AUTO_PARTICIPANT_COUNT } from './useTeamMakerState'

const collator = new Intl.Collator('ko')

type Props = {
  players: TierPlayer[]
  chosen: number[]
  onToggle: (id: number) => void
  onClear: () => void
}

export function AutoRoster({ players, chosen, onToggle, onClear }: Props) {
  const sorted = [...players].sort((a, b) => collator.compare(a.displayName, b.displayName))
  const count = chosen.length
  return (
    <>
      <div className="tm-bar">
        <h3>
          참가자 10명 고르기{' '}
          <span className={`tm-count${count === AUTO_PARTICIPANT_COUNT ? ' ok' : ''}`} aria-live="polite">
            {count}/{AUTO_PARTICIPANT_COUNT}
          </span>
        </h3>
        <button type="button" className="tm-btn small" onClick={onClear}>
          모두 해제
        </button>
      </div>
      <p className="tm-help">
        10명을 고르면 각자 티어리스트에 적힌 라인 안에서만 라인을 정하고, 점수가 비슷해지도록 팀을 나눠요.
      </p>
      <div className="tm-roster">
        {sorted.map((player) => {
          const selected = chosen.includes(player.id)
          const lanes = POSITIONS.flatMap((position) => {
            const lane = player.lanes[position]
            return lane ? [`${POSITION_LABEL[position]} ${lane.tier}티어`] : []
          }).join(' · ')
          return (
            <button
              key={player.id}
              type="button"
              className="tm-pick"
              aria-pressed={selected}
              disabled={!selected && count >= AUTO_PARTICIPANT_COUNT}
              onClick={() => onToggle(player.id)}
            >
              <b>
                {player.displayName}
                <FlaggedMark flagged={player.flagged} />
              </b>
              <small>{lanes || '티어 없음'}</small>
            </button>
          )
        })}
      </div>
    </>
  )
}
