// 수동 모드: 라인마다 2명을 고른다. 그 라인 티어가 있는 사람을 점수 내림차순(같으면 목록 순서)으로, 티어 없는 사람을 그 뒤에 보여 준다.
// 그 라인에 티어 없는 사람이 골라지면 "더 잘하는 사람 + 티어 차이"를 입력받는다(서버가 추정 티어로 계산).
import { POSITION_LABEL, POSITIONS } from '../../api/types'
import type { TierPlayer } from '../../api/tierTypes'
import { EMPTY_GAP, needsGap, type GapInput, type GapState, type PickState } from './useTeamMakerState'

type Props = {
  players: TierPlayer[] // 서버 응답 순서(id 오름차순)
  pick: PickState
  gaps: GapState
  onGap: (position: (typeof POSITIONS)[number], gap: GapInput) => void
  onPick: (position: (typeof POSITIONS)[number], index: 0 | 1, id: number | null) => void
  onClear: () => void
}

export function ManualPicker({ players, pick, gaps, onGap, onPick, onClear }: Props) {
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
          // 그 라인 티어가 있는 사람(점수 높은 순) 다음에 티어가 없는 사람(라인 미정·신규)을 둔다
          const tiered = players
            .flatMap((player) => (player.lanes[position] ? [{ player, lane: player.lanes[position] }] : []))
            .sort((a, b) => b.lane.score - a.lane.score) // 안정 정렬: 같은 점수는 id 순 유지
          const untiered = players.filter((player) => !player.lanes[position])
          const needsInput = needsGap(position, pick, players)
          const gap = gaps[position] ?? EMPTY_GAP
          const names = pick[position].map((id) => players.find((player) => player.id === id)?.displayName ?? '')
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
                  {tiered.map(({ player, lane }) => (
                    <option key={player.id} value={player.id}>
                      {player.displayName} · {lane.tier}티어 ({lane.score}점)
                    </option>
                  ))}
                  {untiered.map((player) => (
                    <option key={player.id} value={player.id}>
                      {player.displayName} · 티어 없음
                    </option>
                  ))}
                </select>
              ))}
              {needsInput && (
                <div className="tm-gap">
                  <label>
                    더 잘하는 사람{' '}
                    <select
                      aria-label={`${POSITION_LABEL[position]} 더 잘하는 사람`}
                      value={gap.stronger ?? ''}
                      onChange={(event) => {
                        const v = event.target.value
                        onGap(position, { ...gap, stronger: v === '' ? null : v === 'same' ? 'same' : (Number(v) as 0 | 1) })
                      }}
                    >
                      <option value="">선택</option>
                      <option value="0">{names[0]}</option>
                      <option value="1">{names[1]}</option>
                      <option value="same">비슷함</option>
                    </select>
                  </label>
                  {gap.stronger !== 'same' && (
                    <label>
                      티어 차이{' '}
                      <select
                        aria-label={`${POSITION_LABEL[position]} 티어 차이`}
                        value={gap.tiers ?? ''}
                        onChange={(event) => onGap(position, { ...gap, tiers: event.target.value === '' ? null : Number(event.target.value) })}
                      >
                        <option value="">선택</option>
                        {[0, 1, 2, 3, 4, 5].map((n) => (
                          <option key={n} value={n}>
                            {n}티어 차이
                          </option>
                        ))}
                      </select>
                    </label>
                  )}
                </div>
              )}
            </div>
          )
        })}
      </div>
    </>
  )
}
