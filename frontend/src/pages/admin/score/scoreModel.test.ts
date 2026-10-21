// 점수 입력 모델 테스트: 칸 검증(정수, -999~999), 14칸 변환, 6×3 격자 값
import { describe, expect, it } from 'vitest'
import { DEFAULT_SCORE_TABLE } from '../../../test/tierListFixtures'
import { draftFromTable, gridValue, parseCell, parseDraft } from './scoreModel'

describe('parseCell', () => {
  it('-999~999 정수만 받는다', () => {
    expect(parseCell('60')).toBe(60)
    expect(parseCell(' -3 ')).toBe(-3)
    expect(parseCell('999')).toBe(999)
    expect(parseCell('-999')).toBe(-999)
    expect(parseCell('-0')).toBe(0)
    expect(Object.is(parseCell('-0'), -0)).toBe(false)
  })

  it('빈칸·소수·글자·범위 밖은 null이다', () => {
    for (const bad of ['', ' ', '-', '1.5', '1e2', 'abc', '1000', '-1000']) expect(parseCell(bad)).toBeNull()
  })
})

describe('parseDraft', () => {
  it('서버 값 → 입력 문자열 → 같은 점수표로 되돌아온다', () => {
    const { isDefault: _isDefault, ...table } = DEFAULT_SCORE_TABLE

    expect(parseDraft(draftFromTable(table))).toEqual(table)
  })

  it('한 칸이라도 올바르지 않으면 null이다', () => {
    const draft = draftFromTable(DEFAULT_SCORE_TABLE)

    expect(parseDraft({ ...draft, lane: { ...draft.lane, ADC: '' } })).toBeNull()
    expect(parseDraft({ ...draft, base: { ...draft.base, '3': '1000' } })).toBeNull()
  })
})

describe('gridValue', () => {
  it('기본 점수 + 우열 보정이다 (라인 보정 전)', () => {
    const draft = draftFromTable(DEFAULT_SCORE_TABLE)

    expect(gridValue(draft, 1, 'HIGH')).toBe(63)
    expect(gridValue(draft, 3, 'MID')).toBe(44)
    expect(gridValue(draft, 6, 'LOW')).toBe(17)
  })

  it('입력이 올바르지 않으면 null이다', () => {
    const draft = draftFromTable(DEFAULT_SCORE_TABLE)

    expect(gridValue({ ...draft, sub: { ...draft.sub, HIGH: '' } }, 1, 'HIGH')).toBeNull()
  })
})
