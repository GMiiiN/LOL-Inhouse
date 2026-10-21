import { act, renderHook } from '@testing-library/react'
import type { ReactNode } from 'react'
import { MemoryRouter, useLocation } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import { isValidDate, usePeriodParams } from './usePeriodParams'

function setup(url: string) {
  const wrapper = ({ children }: { children: ReactNode }) => (
    <MemoryRouter initialEntries={[url]}>{children}</MemoryRouter>
  )
  return renderHook(() => ({ ...usePeriodParams(), search: useLocation().search }), { wrapper })
}

describe('usePeriodParams', () => {
  it('URL의 from·to를 읽는다', () => {
    const { result } = setup('/records?from=2026-10-01&to=2026-10-15')
    expect(result.current.period).toEqual({ from: '2026-10-01', to: '2026-10-15' })
  })

  it('잘못된 날짜는 무시한다', () => {
    const { result } = setup('/records?from=abc&to=2026-02-30')
    expect(result.current.period).toEqual({})
    expect(isValidDate('2026-02-28')).toBe(true)
    expect(isValidDate('2026-2-8')).toBe(false)
    expect(isValidDate(null)).toBe(false)
  })

  it('쓸 때 다른 쿼리 파라미터를 보존하고, 넘기지 않은 키는 그대로 둔다', () => {
    const { result } = setup('/records?player=3&to=2026-10-15')
    act(() => result.current.setPeriod({ from: '2026-10-01' }))
    const params = new URLSearchParams(result.current.search)
    expect(params.get('player')).toBe('3')
    expect(params.get('from')).toBe('2026-10-01')
    expect(params.get('to')).toBe('2026-10-15')
  })

  it('지우기는 from·to만 없앤다', () => {
    const { result } = setup('/records?player=3&from=2026-10-01&to=2026-10-15')
    act(() => result.current.clearPeriod())
    expect(result.current.search).toBe('?player=3')
    expect(result.current.period).toEqual({})
  })
})
