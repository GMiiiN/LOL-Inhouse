// 팀 짜기 입력 상태(모드·참가자·라인별 선택·같은 팀 조건)를 localStorage에 보관하는 훅.
// 참가자에서 빠진 사람의 쌍은 자동으로 지우고, 티어리스트에 없는 선수 id는 걸러 낸다.
import { useEffect, useMemo, useState } from 'react'
import { POSITIONS, type Position } from '../../api/types'
import { MAX_TOGETHER_PAIRS, type TierPlayer, type TogetherPair } from '../../api/tierTypes'

export const TEAM_MAKER_STORAGE_KEY = 'lol-inhouse-team-maker'
export const PAIR_NEEDS_TWO_MESSAGE = '같은 팀으로 묶을 서로 다른 두 사람을 골라 주세요.'
export const PAIR_LIMIT_MESSAGE = `같은 팀 조건은 최대 ${MAX_TOGETHER_PAIRS}쌍까지예요.`
export const PAIR_CONFLICT_MESSAGE = '같은 두 사람을 꼭 같은 팀과 꼭 다른 팀에 함께 넣을 수 없어요.'
export const AUTO_PARTICIPANT_COUNT = 10

export type TeamMode = 'auto' | 'manual'
export type PickState = Record<Position, [number | null, number | null]>
export type TeamMakerState = {
  mode: TeamMode
  chosen: number[] // 자동 모드 참가자. 클릭한 순서를 유지한다(서버 결과가 이 순서에 의존)
  pick: PickState
  together: TogetherPair[]
  apart: TogetherPair[] // 꼭 다른 팀 쌍. together와 같은 쌍은 들어가지 않는다
  fix: Record<number, Position> // 라인 고정(자동 모드). 참가자에서 빠져도 값은 남긴다
}

const emptyPick = (): PickState => ({
  TOP: [null, null],
  JUNGLE: [null, null],
  MID: [null, null],
  ADC: [null, null],
  SUPPORT: [null, null],
})
export const emptyState = (): TeamMakerState => ({ mode: 'auto', chosen: [], pick: emptyPick(), together: [], apart: [], fix: {} })

// 현재 모드 기준 참가자(중복 제거, 순서 유지)
export function participantsOf(state: TeamMakerState): number[] {
  const ids =
    state.mode === 'auto'
      ? state.chosen
      : POSITIONS.flatMap((position) => state.pick[position]).filter((id): id is number => id !== null)
  return [...new Set(ids)]
}

// 요청에 담을 고정: 자동 모드이고 현재 참가자인 사람 것만
export function fixedLanesOf(state: TeamMakerState): Record<number, Position> {
  if (state.mode !== 'auto') return {}
  const chosen = new Set(state.chosen)
  return Object.fromEntries(Object.entries(state.fix).filter(([id]) => chosen.has(Number(id))))
}

function prunePairs(state: TeamMakerState): TeamMakerState {
  const members = new Set(participantsOf(state))
  const keep = (pairs: TogetherPair[]) => pairs.filter(([a, b]) => members.has(a) && members.has(b))
  const together = keep(state.together)
  const apart = keep(state.apart)
  return together.length === state.together.length && apart.length === state.apart.length
    ? state
    : { ...state, together, apart }
}

const samePair = (a: number, b: number, c: number, d: number) => (a === c && b === d) || (a === d && b === c)

const isId = (value: unknown): value is number => typeof value === 'number' && Number.isInteger(value)

// 저장된 값을 믿지 않고 모양을 다시 만든다. players가 있으면 없는 id·그 라인 티어 없는 pick도 제거한다.
export function sanitize(raw: unknown, players: TierPlayer[] | undefined): TeamMakerState {
  const value = (raw && typeof raw === 'object' ? raw : {}) as Record<string, unknown>
  const byId = new Map((players ?? []).map((player) => [player.id, player]))
  const known = (id: unknown): id is number => isId(id) && (!players || byId.has(id))
  const pick = emptyPick()
  const rawPick = (value.pick && typeof value.pick === 'object' ? value.pick : {}) as Record<string, unknown>
  for (const position of POSITIONS) {
    const pair = Array.isArray(rawPick[position]) ? (rawPick[position] as unknown[]) : []
    pick[position] = [0, 1].map((i) => {
      const id = pair[i]
      return known(id) && (!players || byId.get(id)?.lanes[position]) ? id : null
    }) as [number | null, number | null]
  }
  const chosen = Array.isArray(value.chosen)
    ? [...new Set((value.chosen as unknown[]).filter(known))].slice(0, AUTO_PARTICIPANT_COUNT)
    : []
  const pairsOf = (raw: unknown) =>
    (Array.isArray(raw) ? (raw as unknown[]) : []).flatMap((pair): TogetherPair[] =>
      Array.isArray(pair) && pair.length === 2 && known(pair[0]) && known(pair[1]) && pair[0] !== pair[1]
        ? [[pair[0], pair[1]]]
        : [],
    )
  const together = pairsOf(value.together).slice(0, MAX_TOGETHER_PAIRS)
  const apart = pairsOf(value.apart)
    .filter(([a, b]) => !together.some(([x, y]) => samePair(x, y, a, b)))
    .slice(0, MAX_TOGETHER_PAIRS)
  const rawFix = (value.fix && typeof value.fix === 'object' ? value.fix : {}) as Record<string, unknown>
  const fix: Record<number, Position> = {}
  for (const [key, lane] of Object.entries(rawFix)) {
    const id = Number(key)
    if (known(id) && POSITIONS.includes(lane as Position)) fix[id] = lane as Position
  }
  return prunePairs({
    mode: value.mode === 'manual' ? 'manual' : 'auto',
    chosen,
    pick,
    together,
    apart,
    fix,
  })
}

function loadStored(): unknown {
  try {
    const text = localStorage.getItem(TEAM_MAKER_STORAGE_KEY)
    return text ? JSON.parse(text) : null
  } catch {
    return null
  }
}

export function useTeamMakerState(players: TierPlayer[] | undefined) {
  const [raw, setRaw] = useState<unknown>(loadStored)
  // 티어리스트가 도착하거나 바뀌면 없는 선수를 걸러 낸 값을 쓴다(렌더 중에 파생)
  const state = useMemo(() => sanitize(raw, players), [raw, players])

  useEffect(() => {
    try {
      localStorage.setItem(TEAM_MAKER_STORAGE_KEY, JSON.stringify(state))
    } catch {
      // 저장 공간이 막혀 있어도 화면은 동작한다
    }
  }, [state])

  const update = (change: (current: TeamMakerState) => TeamMakerState) => {
    setRaw((current: unknown) => prunePairs(change(sanitize(current, players))))
  }

  const setMode = (mode: TeamMode) => update((s) => ({ ...s, mode }))
  const toggleChosen = (id: number) =>
    update((s) => {
      if (s.chosen.includes(id)) return { ...s, chosen: s.chosen.filter((x) => x !== id) }
      return s.chosen.length >= AUTO_PARTICIPANT_COUNT ? s : { ...s, chosen: [...s.chosen, id] }
    })
  const clearChosen = () => update((s) => ({ ...s, chosen: [] }))
  const setPick = (position: Position, index: 0 | 1, id: number | null) =>
    update((s) => {
      const pair: [number | null, number | null] = [...s.pick[position]]
      pair[index] = id
      return { ...s, pick: { ...s.pick, [position]: pair } }
    })
  const clearPick = () => update((s) => ({ ...s, pick: emptyPick() }))
  // lane이 null이면 고정을 푼다
  const setFix = (id: number, lane: Position | null) =>
    update((s) => {
      const fix = { ...s.fix }
      if (lane) fix[id] = lane
      else delete fix[id]
      return { ...s, fix }
    })
  const removePair = (index: number) => update((s) => ({ ...s, together: s.together.filter((_, i) => i !== index) }))
  const removeApart = (index: number) => update((s) => ({ ...s, apart: s.apart.filter((_, i) => i !== index) }))

  // 꼭 같은 팀(together)·꼭 다른 팀(apart) 추가가 같은 규칙을 쓴다. 오류 문구를 돌려주고, 성공하면 null
  function addTo(kind: 'together' | 'apart', a: number | null, b: number | null): string | null {
    if (a === null || b === null || a === b) return PAIR_NEEDS_TWO_MESSAGE
    const other = kind === 'together' ? state.apart : state.together
    if (other.some(([x, y]) => samePair(x, y, a, b))) return PAIR_CONFLICT_MESSAGE
    if (state[kind].some(([x, y]) => samePair(x, y, a, b))) return null
    if (state[kind].length >= MAX_TOGETHER_PAIRS) return PAIR_LIMIT_MESSAGE
    update((s) => ({ ...s, [kind]: [...s[kind], [a, b]] }))
    return null
  }
  const addPair = (a: number | null, b: number | null) => addTo('together', a, b)
  const addApart = (a: number | null, b: number | null) => addTo('apart', a, b)

  return {
    state,
    participants: participantsOf(state),
    setMode,
    toggleChosen,
    clearChosen,
    setPick,
    clearPick,
    setFix,
    addPair,
    removePair,
    addApart,
    removeApart,
  }
}
