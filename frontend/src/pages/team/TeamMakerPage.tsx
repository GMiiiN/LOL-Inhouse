// 팀 짜기 화면(공개). 입력(자동/수동·같은 팀 조건)은 localStorage에 보관하고, 계산은 서버가 한다.
import { useMutation, useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { getTeamMakerTierList, postTeamAuto, postTeamManual } from '../../api/teamMaker'
import { describeError } from '../../api/errorMessages'
import { TIER_LIST_KEY } from '../../api/queryKeys'
import { POSITIONS, type Position } from '../../api/types'
import type { TeamMakerManualRequest, TeamMakerResponse } from '../../api/tierTypes'
import '../../styles/team.css'
import { AutoRoster } from './AutoRoster'
import { FixedLaneBox } from './FixedLaneBox'
import { ManualPicker } from './ManualPicker'
import { TeamResults } from './TeamResults'
import { TogetherPairs } from './TogetherPairs'
import { AUTO_PARTICIPANT_COUNT, fixedLanesOf, useTeamMakerState } from './useTeamMakerState'

type Props = {
  // 테스트에서 코인토스의 난수·지연을 주입한다
  random?: () => number
  coinDelayMs?: number
}

export function TeamMakerPage({ random, coinDelayMs }: Props = {}) {
  const tierList = useQuery({ queryKey: TIER_LIST_KEY, queryFn: getTeamMakerTierList })
  const players = tierList.data?.players
  const team = useTeamMakerState(players)
  const { state } = team
  const [run, setRun] = useState<{ id: number; response: TeamMakerResponse; fixedLanes: Record<number, Position> } | null>(null)

  const nameById = new Map((players ?? []).map((player) => [player.id, player.displayName]))
  const nameOf = (playerId: number) => nameById.get(playerId) ?? `선수 #${playerId}`

  const make = useMutation({
    mutationFn: () => {
      if (state.mode === 'auto') {
        const fixedLanes = fixedLanesOf(state)
        return postTeamAuto({
          participantIds: state.chosen,
          together: state.together,
          ...(state.apart.length > 0 && { apart: state.apart }),
          ...(Object.keys(fixedLanes).length > 0 && { fixedLanes }),
        })
      }
      const picks = Object.fromEntries(POSITIONS.map((position) => [position, state.pick[position]])) as TeamMakerManualRequest['picks']
      return postTeamManual({ picks, together: state.together, ...(state.apart.length > 0 && { apart: state.apart }) })
    },
    onSuccess: (response) =>
      setRun((current) => ({ id: (current?.id ?? 0) + 1, response, fixedLanes: fixedLanesOf(state) })),
  })

  // 고정을 바꾸면 옛 결과·오류는 더 이상 맞지 않으므로 지운다
  const changeFix = (id: number, lane: Position | null) => {
    team.setFix(id, lane)
    setRun(null)
    make.reset()
  }

  const ready =
    state.mode === 'auto'
      ? state.chosen.length === AUTO_PARTICIPANT_COUNT
      : POSITIONS.every((position) => state.pick[position].every((id) => id !== null))

  if (tierList.isPending || tierList.isError) {
    return (
      <div className="tm-page">
        <h1>팀 짜기</h1>
        {tierList.isPending ? (
          <p className="muted">티어리스트를 불러오는 중…</p>
        ) : (
          <p className="tm-err" role="alert">
            {describeError(tierList.error).message}
          </p>
        )}
      </div>
    )
  }

  return (
    <div className="tm-page">
      <h1>팀 짜기</h1>
      <section className="tm-panel">
        <div className="tm-seg" role="tablist" aria-label="라인 정하는 방식">
          <button type="button" role="tab" aria-selected={state.mode === 'auto'} onClick={() => team.setMode('auto')}>
            라인 자동 배정
          </button>
          <button type="button" role="tab" aria-selected={state.mode === 'manual'} onClick={() => team.setMode('manual')}>
            라인 직접 지정
          </button>
        </div>
        {state.mode === 'auto' ? (
          <>
            <AutoRoster players={players ?? []} chosen={state.chosen} onToggle={team.toggleChosen} onClear={team.clearChosen} />
            <FixedLaneBox
              players={(players ?? []).filter((player) => state.chosen.includes(player.id))}
              fix={state.fix}
              onChange={changeFix}
            />
          </>
        ) : (
          <ManualPicker players={players ?? []} pick={state.pick} onPick={team.setPick} onClear={team.clearPick} />
        )}
      </section>

      <section className="tm-panel">
        <TogetherPairs
          participants={team.participants}
          pairs={state.together}
          nameOf={nameOf}
          onAdd={team.addPair}
          onRemove={team.removePair}
        />
      </section>

      <section className="tm-panel">
        <TogetherPairs
          kind="apart"
          participants={team.participants}
          pairs={state.apart}
          nameOf={nameOf}
          onAdd={team.addApart}
          onRemove={team.removeApart}
        />
      </section>

      <section className="tm-panel">
        {make.isError && (
          <p className="tm-err" role="alert">
            {describeError(make.error).message}
          </p>
        )}
        {!ready && (
          <p className="tm-help">
            {state.mode === 'auto' ? `참가자를 10명 골라 주세요. 지금 ${state.chosen.length}명이에요.` : '모든 라인에 2명씩 골라 주세요.'}
          </p>
        )}
        <div>
          <button type="button" className="tm-btn primary" disabled={!ready || make.isPending} onClick={() => make.mutate()}>
            {make.isPending ? '계산하는 중…' : '팀 짜기'}
          </button>
        </div>
      </section>

      {run && (
        <TeamResults
          key={run.id}
          options={run.response.options}
          scoreMatrix={run.response.scoreMatrix}
          nameOf={nameOf}
          fixedLanes={run.fixedLanes}
          random={random}
          coinDelayMs={coinDelayMs}
        />
      )}
    </div>
  )
}
