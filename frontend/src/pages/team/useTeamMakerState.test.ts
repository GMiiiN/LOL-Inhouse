// useTeamMakerState 테스트: 클릭 순서 유지, 10명 상한, 쌍 자동 제거, localStorage 복원과 없는 id 제거
import { act, renderHook } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { TierPlayer } from '../../api/tierTypes'
import {
  fixedLanesOf,
  PAIR_LIMIT_MESSAGE,
  PAIR_NEEDS_TWO_MESSAGE,
  TEAM_MAKER_STORAGE_KEY,
  useTeamMakerState,
} from './useTeamMakerState'

const lanes = (top: boolean): TierPlayer['lanes'] => ({
  TOP: top ? { tier: 3, sub: 'MID', score: 44, trend: null } : null,
  JUNGLE: null,
  MID: null,
  ADC: null,
  SUPPORT: null,
})
const players: TierPlayer[] = Array.from({ length: 12 }, (_, i) => ({
  id: i + 1,
  displayName: `선수${i + 1}`,
  lanes: lanes(i < 6),
  flagged: false,
}))
const stored = () => JSON.parse(localStorage.getItem(TEAM_MAKER_STORAGE_KEY)!)

describe('useTeamMakerState', () => {
  it('참가자는 클릭한 순서를 유지하고(정렬하지 않음) 10명까지만 담긴다', () => {
    const { result } = renderHook(() => useTeamMakerState(players))

    for (const id of [5, 2, 9, 1]) act(() => result.current.toggleChosen(id))
    expect(result.current.state.chosen).toEqual([5, 2, 9, 1])

    for (const id of [3, 4, 6, 7, 8, 10, 11, 12]) act(() => result.current.toggleChosen(id))
    expect(result.current.state.chosen).toHaveLength(10)
    expect(result.current.state.chosen).not.toContain(11)

    act(() => result.current.toggleChosen(2))
    expect(result.current.state.chosen).not.toContain(2)
    expect(stored().chosen).toEqual(result.current.state.chosen)
  })

  it('같은 팀 조건: 두 사람이 달라야 하고, 최대 5쌍, 빠진 사람의 쌍은 자동으로 사라진다', () => {
    const { result } = renderHook(() => useTeamMakerState(players))
    for (const id of [1, 2, 3, 4, 5, 6, 7, 8, 9, 10]) act(() => result.current.toggleChosen(id))

    let message: string | null = ''
    act(() => {
      message = result.current.addPair(1, 1)
    })
    expect(message).toBe(PAIR_NEEDS_TWO_MESSAGE)
    act(() => {
      message = result.current.addPair(null, 2)
    })
    expect(message).toBe(PAIR_NEEDS_TWO_MESSAGE)

    for (const [a, b] of [
      [1, 2],
      [3, 4],
      [5, 6],
      [7, 8],
      [9, 10],
    ]) {
      act(() => {
        message = result.current.addPair(a, b)
      })
    }
    expect(message).toBeNull()
    expect(result.current.state.together).toHaveLength(5)
    act(() => {
      message = result.current.addPair(1, 3)
    })
    expect(message).toBe(PAIR_LIMIT_MESSAGE)
    act(() => {
      message = result.current.addPair(2, 1) // 순서만 다른 같은 쌍은 조용히 무시
    })
    expect(message).toBeNull()
    expect(result.current.state.together).toHaveLength(5)

    act(() => result.current.toggleChosen(3))
    expect(result.current.state.together).toEqual([
      [1, 2],
      [5, 6],
      [7, 8],
      [9, 10],
    ])
  })

  it('수동 모드 참가자는 라인 선택에서 오고, 라인에서 빠지면 쌍도 사라진다', () => {
    const { result } = renderHook(() => useTeamMakerState(players))
    act(() => result.current.setMode('manual'))
    act(() => result.current.setPick('TOP', 0, 2))
    act(() => result.current.setPick('TOP', 1, 4))
    expect(result.current.participants).toEqual([2, 4])

    act(() => {
      result.current.addPair(2, 4)
    })
    expect(result.current.state.together).toEqual([[2, 4]])
    act(() => result.current.setPick('TOP', 1, null))
    expect(result.current.state.together).toEqual([])
  })

  it('localStorage에서 복원하고 티어리스트에 없는 id는 제거한다(그 라인 티어 없는 사람은 남긴다)', () => {
    localStorage.setItem(
      TEAM_MAKER_STORAGE_KEY,
      JSON.stringify({
        mode: 'manual',
        chosen: [3, 99, 1],
        pick: { TOP: [1, 8], MID: [2, null] },
        together: [
          [1, 3],
          [1, 99],
        ],
      }),
    )
    const { result, rerender } = renderHook(({ list }) => useTeamMakerState(list), {
      initialProps: { list: undefined as TierPlayer[] | undefined },
    })
    expect(result.current.state.mode).toBe('manual')
    expect(result.current.state.chosen).toEqual([3, 99, 1])

    rerender({ list: players })
    expect(result.current.state.chosen).toEqual([3, 1])
    expect(result.current.state.pick.TOP).toEqual([1, 8]) // 8번은 탑 티어 없어도 고를 수 있다
    expect(result.current.state.pick.MID).toEqual([2, null]) // 2번은 미드 티어 없어도 고를 수 있다
    expect(result.current.state.together).toEqual([]) // 수동 모드 참가자는 {1}뿐이라 쌍이 사라진다
  })

  it('깨진 저장값은 빈 상태로 시작한다', () => {
    localStorage.setItem(TEAM_MAKER_STORAGE_KEY, '{not json')

    const { result } = renderHook(() => useTeamMakerState(players))

    expect(result.current.state).toMatchObject({ mode: 'auto', chosen: [], together: [] })
  })

  it('라인 고정: 저장·복원하고 잘못된 값·없는 id는 버리며, 참가자에서 빠져도 값은 남지만 요청에는 참가자 것만 담긴다', () => {
    localStorage.setItem(
      TEAM_MAKER_STORAGE_KEY,
      JSON.stringify({ chosen: [1, 2], fix: { '1': 'MID', '2': 'BOTTOM', '99': 'TOP', abc: 'TOP', '3': 'ADC' } }),
    )
    const { result } = renderHook(() => useTeamMakerState(players))
    expect(result.current.state.fix).toEqual({ 1: 'MID', 3: 'ADC' })
    expect(fixedLanesOf(result.current.state)).toEqual({ 1: 'MID' }) // 3번은 참가자가 아니다

    act(() => result.current.setFix(2, 'TOP'))
    expect(stored().fix).toEqual({ '1': 'MID', '2': 'TOP', '3': 'ADC' })
    act(() => result.current.setFix(1, null))
    expect(result.current.state.fix).toEqual({ 2: 'TOP', 3: 'ADC' })
    act(() => result.current.toggleChosen(2)) // 참가자에서 빠져도 값은 둔다
    expect(result.current.state.fix[2]).toBe('TOP')
    expect(fixedLanesOf(result.current.state)).toEqual({})
  })

  it('수동 모드에서는 라인 고정을 요청에 담지 않는다', () => {
    const { result } = renderHook(() => useTeamMakerState(players))
    act(() => result.current.toggleChosen(1))
    act(() => result.current.setFix(1, 'TOP'))
    act(() => result.current.setMode('manual'))

    expect(fixedLanesOf(result.current.state)).toEqual({})
  })
})
