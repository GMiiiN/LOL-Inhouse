// URL의 from·to(내전 시작일 기준 기간)를 읽고 쓰는 훅. 다른 쿼리 파라미터는 건드리지 않는다.
import { useCallback, useMemo } from 'react'
import { useSearchParams } from 'react-router-dom'
import type { Period } from '../../api/publicTypes'

// 'YYYY-MM-DD'이면서 실제로 있는 날짜만 통과(2026-02-30 같은 값은 버린다)
export function isValidDate(text: string | null | undefined): text is string {
  if (!text || !/^\d{4}-\d{2}-\d{2}$/.test(text)) return false
  const date = new Date(`${text}T00:00:00Z`)
  return !Number.isNaN(date.getTime()) && date.toISOString().slice(0, 10) === text
}

export function usePeriodParams() {
  const [searchParams, setSearchParams] = useSearchParams()
  const rawFrom = searchParams.get('from')
  const rawTo = searchParams.get('to')

  // 값이 있을 때만 키를 넣어 쿼리 키·API 호출이 안정적이게 한다
  const period = useMemo<Period>(() => {
    const result: Period = {}
    if (isValidDate(rawFrom)) result.from = rawFrom
    if (isValidDate(rawTo)) result.to = rawTo
    return result
  }, [rawFrom, rawTo])

  // next에 들어 있는 키만 바꾼다. 유효하지 않은 값(undefined·빈 문자열 포함)은 그 파라미터를 지운다. 이력은 쌓지 않는다(replace).
  const setPeriod = useCallback(
    (next: Period) => {
      setSearchParams(
        (prev) => {
          const params = new URLSearchParams(prev)
          for (const key of ['from', 'to'] as const) {
            if (!(key in next)) continue
            const value = next[key]
            if (isValidDate(value)) params.set(key, value)
            else params.delete(key)
          }
          return params
        },
        { replace: true },
      )
    },
    [setSearchParams],
  )

  const clearPeriod = useCallback(() => setPeriod({ from: undefined, to: undefined }), [setPeriod])

  return { period, setPeriod, clearPeriod }
}
