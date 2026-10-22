// useDebouncedCheck 회귀: 검사 함수 인자 일반화 뒤에도 디바운스·첫 평가 재사용·옛 응답 버리기가 그대로인지
import { act, renderHook } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { CHECK_DEBOUNCE_MS, useDebouncedCheck } from './useDebouncedCheck'

beforeEach(() => vi.useFakeTimers())
afterEach(() => vi.useRealTimers())

type Evaluation = { value: string }
const initial = { key: 'a', evaluation: { value: 'initial' } }

describe('useDebouncedCheck', () => {
  it('요청 키가 첫 평가와 같으면 검사 함수를 부르지 않는다', async () => {
    const check = vi.fn(async (request: string) => ({ value: request }))
    const { result } = renderHook(() => useDebouncedCheck<string, Evaluation>(check, 'a', 'a', initial))

    await act(() => vi.advanceTimersByTimeAsync(CHECK_DEBOUNCE_MS * 2))

    expect(check).not.toHaveBeenCalled()
    expect(result.current).toMatchObject({ evaluation: { value: 'initial' }, settling: false })
  })

  it('입력이 바뀌면 600ms 뒤 한 번만 검사하고, 매 렌더 새 함수를 넘겨도 대기가 다시 시작되지 않는다', async () => {
    const check = vi.fn(async (request: string) => ({ value: request }))
    const { result, rerender } = renderHook(
      ({ key }) => useDebouncedCheck<string, Evaluation>((r) => check(r), key, key, initial),
      { initialProps: { key: 'b' } },
    )
    expect(result.current.settling).toBe(true)

    await act(() => vi.advanceTimersByTimeAsync(400))
    rerender({ key: 'b' })
    await act(() => vi.advanceTimersByTimeAsync(CHECK_DEBOUNCE_MS - 400))

    expect(check).toHaveBeenCalledTimes(1)
    expect(check).toHaveBeenCalledWith('b')
    expect(result.current).toMatchObject({ evaluation: { value: 'b' }, settling: false, error: null })
  })

  it('입력이 계속 바뀌면 마지막 입력만 검사한다', async () => {
    const check = vi.fn(async (request: string) => ({ value: request }))
    const { result, rerender } = renderHook(
      ({ key }) => useDebouncedCheck<string, Evaluation>(check, key, key, initial),
      { initialProps: { key: 'b' } },
    )
    await act(() => vi.advanceTimersByTimeAsync(300))
    rerender({ key: 'c' })
    await act(() => vi.advanceTimersByTimeAsync(CHECK_DEBOUNCE_MS))

    expect(check).toHaveBeenCalledTimes(1)
    expect(check).toHaveBeenCalledWith('c')
    expect(result.current.evaluation).toEqual({ value: 'c' })
  })

  it('검사가 실패하면 이전 평가를 남기고 오류를 알린다', async () => {
    const failure = new Error('boom')
    const check = vi.fn(async () => {
      throw failure
    })
    const { result } = renderHook(() => useDebouncedCheck<string, Evaluation>(check, 'b', 'b', initial))

    await act(() => vi.advanceTimersByTimeAsync(CHECK_DEBOUNCE_MS))

    expect(result.current).toMatchObject({ evaluation: { value: 'initial' }, error: failure, settling: false })
  })
})
