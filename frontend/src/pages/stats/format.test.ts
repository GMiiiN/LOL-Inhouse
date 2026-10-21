import { describe, expect, it } from 'vitest'
import { EM_DASH, formatDuration, formatGold, formatPerGame, formatPercent } from './format'

describe('format', () => {
  it('초를 mm:ss로 바꾼다', () => {
    expect(formatDuration(0)).toBe('00:00')
    expect(formatDuration(65)).toBe('01:05')
    expect(formatDuration(1925)).toBe('32:05')
    expect(formatDuration(3725)).toBe('62:05')
    expect(formatDuration(null)).toBe(EM_DASH)
  })

  it('경기당 평균은 소수 1자리이고 0세트면 대시다', () => {
    expect(formatPerGame(50, 3)).toBe('16.7')
    expect(formatPerGame(10, 2)).toBe('5.0')
    expect(formatPerGame(0, 0)).toBe(EM_DASH)
  })

  it('골드는 천 단위 쉼표를 붙인다', () => {
    expect(formatGold(0)).toBe('0')
    expect(formatGold(999)).toBe('999')
    expect(formatGold(1234567)).toBe('1,234,567')
  })

  it('비율은 반올림 %다', () => {
    expect(formatPercent(0.5)).toBe('50%')
    expect(formatPercent(0.666)).toBe('67%')
    expect(formatPercent(0)).toBe('0%')
    expect(formatPercent(1)).toBe('100%')
  })
})
