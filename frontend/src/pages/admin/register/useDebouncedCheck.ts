// 확인 화면의 입력이 바뀌면 600ms 쉬었다가 POST …/sets/check로 다시 검사한다.
// 초안 응답에 이미 들어 있는 첫 평가는 그대로 쓰고(요청이 같을 때), 응답이 늦게 도착한 옛 요청은 버린다.
import { useEffect, useState } from 'react'
import { checkSet } from '../../../api/registration'
import type { Evaluation, SetRegistrationRequest } from '../../../api/registrationTypes'

export const CHECK_DEBOUNCE_MS = 600

type CheckResult = { key: string; evaluation: Evaluation | null; error: unknown }

export type DebouncedCheck = {
  evaluation: Evaluation | null // 마지막으로 성공한 검사 결과
  error: unknown // 마지막 검사가 실패했으면 그 오류
  settling: boolean // 입력이 바뀐 뒤 아직 최신 검사 결과가 없음(대기 중이거나 요청 중)
}

export function useDebouncedCheck(
  duelId: number,
  request: SetRegistrationRequest,
  requestKey: string,
  initial: { key: string; evaluation: Evaluation },
  delayMs = CHECK_DEBOUNCE_MS,
): DebouncedCheck {
  const [result, setResult] = useState<CheckResult>({ key: initial.key, evaluation: initial.evaluation, error: null })

  useEffect(() => {
    if (requestKey === result.key) return undefined
    let cancelled = false
    const timer = setTimeout(() => {
      checkSet(duelId, request)
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
  }, [requestKey, result.key, duelId, request, delayMs])

  return { evaluation: result.evaluation, error: result.error, settling: requestKey !== result.key }
}
