// 수동 모드: 라인마다 2명을 고른다. 그 라인 티어가 있는 사람만, 그 라인 점수 내림차순(같으면 목록 순서)으로 보여 준다.
import { POSITION_LABEL, POSITIONS } from '../../api/types'
import type { TierPlayer } from '../../api/tierTypes'
import type { PickState } from './useTeamMakerState'

type Props = {
  players: TierPlayer[] // 서버 응답 순서(id 오름차순)
  pick: PickState
  onPick: (position: (typeof POSITIONS)[number], index: 0 | 1, id: number | null) => void
  onClear: () => void
}

export function ManualPicker({ players, pick, onPick, onClear }: Props) {
  return (
    <>
      <div className="tm-bar">
        <h3>라인별로 2명씩</h3>
        <button type="button" className="tm-btn small" onClick={onClear}>
          비우기
        </button>
      </div>
      <p className="tm-help">
        라인이 이미 정해졌을 때 쓰세요. 같은 라인의 두 사람을 블루·레드로 나누는 경우를 모두 비교해요.
      </p>
      <div className="tm-picker">
        {POSITIONS.map((position) => {
          const candidates = players
            .flatMap((player) => (player.lanes[position] ? [{ player, lane: player.lanes[position] }] : []))
            .sort((a, b) => b.lane.score - a.lane.score) // 안정 정렬: 같은 점수는 id 순 유지
          return (
            <div key={position} className="tm-pickrow">
              <span className="tm-lane">{POSITION_LABEL[position]}</span>
              {([0, 1] as const).map((index) => (
                <select
                  key={index}
                  aria-label={`${POSITION_LABEL[position]} ${index + 1}번째`}
                  value={pick[position][index] ?? ''}
                  onChange={(event) => onPick(position, index, event.target.value ? Number(event.target.value) : null)}
                >
                  <option value="">선택</option>
                  {candidates.map(({ player, lane }) => (
                    <option key={player.id} value={player.id}>
                      {player.displayName} · {lane.tier}티어 ({lane.score}점)
                    </option>
                  ))}
                </select>
              ))}
            </div>
          )
        })}
      </div>
    </>
  )
}
