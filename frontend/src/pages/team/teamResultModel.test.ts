/// <reference types="node" />
// teamResultModel 테스트: 골든 파일의 swaps 열(원본 res-swap 결과)과 대조하고, 되돌리기·진영 바꾸기·판정을 손계산으로 검증한다.
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'
import type { ScoreMatrix, SlotView, TeamOption } from '../../api/tierTypes'
import {
  laneGapText,
  recalc,
  resultText,
  revert,
  swapPlayers,
  swapSides,
  toCandidates,
  verdictOf,
  type SlotRef,
} from './teamResultModel'

type GoldenScenario = {
  id: string
  expected: { ok: boolean; options?: TeamOption[] }
  scoreMatrix: ScoreMatrix
  swaps?: {
    optionIndex: number
    steps: { a: SlotRef; b: SlotRef; result: Omit<TeamOption, 'total'> }[]
  }
}
// vitest는 frontend/에서 실행한다
const goldenPath = resolve(process.cwd(), '../backend/src/test/resources/team-maker/golden-v2.json')
const golden = JSON.parse(readFileSync(goldenPath, 'utf8')) as { scenarios: GoldenScenario[] }

describe('골든 swaps 대조', () => {
  const withSwaps = golden.scenarios.filter((s) => s.swaps)

  it('교체 시나리오가 있다', () => {
    expect(withSwaps.length).toBeGreaterThan(100)
  })

  it('모든 단계의 슬롯·합계·diff·gap·cost가 원본과 같다 (total은 blueSum+redSum)', () => {
    let steps = 0
    for (const scenario of withSwaps) {
      const swaps = scenario.swaps!
      let candidate = toCandidates(scenario.expected.options!)[swaps.optionIndex]
      for (const step of swaps.steps) {
        candidate = swapPlayers(candidate, step.a, step.b, scenario.scoreMatrix)
        const { total, ...rest } = candidate.current
        expect({ id: scenario.id, ...rest }).toEqual({ id: scenario.id, ...step.result })
        expect(total).toBe(rest.blueSum + rest.redSum)
        steps += 1
      }
    }
    expect(steps).toBeGreaterThan(withSwaps.length - 1)
  })
})

const slot = (playerId: number, position: SlotView['position'], score: number, tier: number | null = 3): SlotView => ({
  playerId,
  position,
  tier: tier as SlotView['tier'],
  sub: tier ? 'MID' : null,
  score,
})
const blue = [slot(1, 'TOP', 50), slot(2, 'JUNGLE', 40), slot(3, 'MID', 40), slot(4, 'ADC', 30), slot(5, 'SUPPORT', 30)]
const red = [slot(6, 'TOP', 45), slot(7, 'JUNGLE', 45), slot(8, 'MID', 38), slot(9, 'ADC', 33), slot(10, 'SUPPORT', 29)]
const matrix: ScoreMatrix = {
  '1': { TOP: { score: 50, tier: 3, sub: 'MID', hasTier: true }, JUNGLE: { score: 17, tier: null, sub: null, hasTier: false }, MID: { score: 41, tier: 3, sub: 'MID', hasTier: true }, ADC: { score: 0, tier: null, sub: null, hasTier: false }, SUPPORT: { score: 0, tier: null, sub: null, hasTier: false } },
  '3': { TOP: { score: 44, tier: 3, sub: 'MID', hasTier: true }, JUNGLE: { score: 0, tier: null, sub: null, hasTier: false }, MID: { score: 40, tier: 3, sub: 'MID', hasTier: true }, ADC: { score: 0, tier: null, sub: null, hasTier: false }, SUPPORT: { score: 0, tier: null, sub: null, hasTier: false } },
}
const base = toCandidates([recalc(blue, red)])[0]

describe('교체', () => {
  it('블루 탑(1)과 블루 미드(3)를 바꾸면 각자 새 라인 점수·티어 없음이 반영된다', () => {
    const next = swapPlayers(base, { side: 'blue', i: 0 }, { side: 'blue', i: 2 }, matrix)

    expect(next.current.blue[0]).toMatchObject({ playerId: 3, position: 'TOP', score: 44 })
    expect(next.current.blue[2]).toMatchObject({ playerId: 1, position: 'MID', score: 41 })
    expect(next.current.blueSum).toBe(44 + 40 + 41 + 30 + 30)
    expect(next.current.total).toBe(next.current.blueSum + next.current.redSum)
    expect(next.original).toBe(base.current)
  })

  it('티어 없는 라인으로 가면 tier·sub가 null이다', () => {
    const next = swapPlayers(base, { side: 'blue', i: 0 }, { side: 'blue', i: 1 }, matrix)

    expect(next.current.blue[1]).toMatchObject({ playerId: 1, position: 'JUNGLE', tier: null, sub: null, score: 17 })
  })

  it('같은 칸을 고르면 그대로, 두 번 교체해도 original은 처음 방안이다', () => {
    expect(swapPlayers(base, { side: 'red', i: 1 }, { side: 'red', i: 1 }, matrix)).toBe(base)
    const once = swapPlayers(base, { side: 'blue', i: 0 }, { side: 'blue', i: 2 }, matrix)
    const twice = swapPlayers(once, { side: 'blue', i: 0 }, { side: 'blue', i: 2 }, matrix)

    expect(twice.original).toBe(base.current)
  })

  it('되돌리기는 처음 방안으로, 수정 안 한 방안은 그대로', () => {
    const edited = swapPlayers(base, { side: 'blue', i: 0 }, { side: 'blue', i: 2 }, matrix)

    expect(revert(edited)).toEqual(base)
    expect(revert(base)).toBe(base)
  })
})

describe('진영 바꾸기·판정·글', () => {
  it('블루와 레드를 맞바꾸고 합계도 따라간다. 점수 차는 같다', () => {
    const flipped = swapSides(base)

    expect(flipped.current.blue).toBe(base.current.red)
    expect(flipped.current.blueSum).toBe(base.current.redSum)
    expect(flipped.current.diff).toBe(base.current.diff)
    expect(swapSides(flipped).current).toEqual(base.current)
  })

  it('수정된 방안은 되돌리기 기준도 같이 맞바꾼다', () => {
    const edited = swapPlayers(base, { side: 'blue', i: 0 }, { side: 'blue', i: 2 }, matrix)

    expect(swapSides(edited).original!.blue).toBe(base.current.red)
  })

  it('판정 세 구간', () => {
    const at = (diff: number, gap: number) => verdictOf({ ...base.current, diff, gap })

    expect(at(3, 12)).toMatchObject({ tone: 'good', text: '점수도 라인 맞대결도 고르게 맞춰졌어요.' })
    expect(at(3, 13)).toMatchObject({ tone: 'good', text: '괜찮은 밸런스예요.' })
    expect(at(8, 99)).toMatchObject({ tone: 'good' })
    expect(at(9, 0)).toMatchObject({ tone: 'warn' })
  })

  it('라인 가운데 글', () => {
    expect(laneGapText(5, 5)).toBe('동점')
    expect(laneGapText(8, 5)).toBe('◀ 3')
    expect(laneGapText(5, 8)).toBe('3 ▶')
  })

  it('복사 글은 A팀 · 블루 / B팀 · 레드', () => {
    const text = resultText(base.current, (id) => `선수${id}`)

    expect(text).toContain('A팀 · 블루 (190점)')
    expect(text).toContain('탑 선수1 / 정글 선수2 / 미드 선수3 / 원딜 선수4 / 서포터 선수5')
    expect(text).toContain('B팀 · 레드 (190점)')
    expect(text).toContain('점수 차: 0')
  })
})
