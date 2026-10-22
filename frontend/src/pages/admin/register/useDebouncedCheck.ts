// 입력이 바뀌면 600ms 쉬었다가 검사 함수(등록: POST …/sets/check, 수정: POST …/sets/{id}/check)로 다시 검사한다.
// 첫 평가(초안·폼 응답에 이미 들어 있음)는 요청이 같을 때 그대로 쓰고, 응답이 늦게 도착한 옛 요청은 버린다.
import { useEffect, useRef, useState } from 'react'

export const CHECK_DEBOUNCE_MS = 600

type CheckResult<E> = { key: string; evaluation: E | null; error: unknown }

export type DebouncedCheck<E> = {
  evaluation: E | null // 마지막으로 성공한 검사 결과
  error: unknown // 마지막 검사가 실패했으면 그 오류
  settling: boolean // 입력이 바뀐 뒤 아직 최신 검사 결과가 없음(대기 중이거나 요청 중)
}

export function useDebouncedCheck<Req, E>(
  checkFn: (request: Req) => Promise<E>,
  request: Req,
  requestKey: string,
  initial: { key: string; evaluation: E },
  delayMs = CHECK_DEBOUNCE_MS,
): DebouncedCheck<E> {
  const [result, setResult] = useState<CheckResult<E>>({ key: initial.key, evaluation: initial.evaluation, error: null })
  // 호출하는 쪽이 매 렌더 새 함수를 넘겨도 대기 시간이 다시 시작되지 않게 최신 함수만 ref에 둔다.
  const checkRef = useRef(checkFn)
  useEffect(() => {
    checkRef.current = checkFn
  })

  useEffect(() => {
    if (requestKey === result.key) return undefined
    let cancelled = false
    const timer = setTimeout(() => {
      checkRef
        .current(request)
        .then((evaluation) => {
          if (!cancelled) setResult({ key: requestKey, evaluation, error: null })
        })
        .catch((error: unknown) => {
          if (!cancelled) setResult((previous) => ({ key: requestKey, evaluation: previous.evaluation, error }))
        })
    }, delayMs)
    return () => {
      cancelled = true
      clearTimeout(timer)
    }
  }, [requestKey, result.key, request, delayMs])

  return { evaluation: result.evaluation, error: result.error, settling: requestKey !== result.key }
}
