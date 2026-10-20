// 세트 등록 2단계(EXTRACTING): 추출 작업 상태를 2초마다 확인한다. 끝나면 확인 단계로, 실패하면 다시 추출을 안내한다.
// 6분이 지나도 안 끝나면 폴링을 멈추고 "상태 다시 확인"(기존 작업을 다시 본다)과 "다시 추출"(새 작업)을 구분해서 보여 준다.
// 설계: docs/design/m4-design.md §6, §11.2-3
import { useQuery } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { describeExtractionFailure } from '../../../api/errorMessages'
import { extractionKey } from '../../../api/queryKeys'
import { getExtraction } from '../../../api/registration'
import { ErrorNotice } from '../../../components/ErrorNotice'

export const POLL_INTERVAL_MS = 2000
export const GIVE_UP_AFTER_MS = 6 * 60 * 1000

type ExtractionProgressProps = {
  jobId: number
  startedAt: number // Date.now() 기준. 서버는 시각 필드를 주지 않으므로 경과 시간은 이쪽에서 잰다
  retrying: boolean
  onSucceeded: () => void
  onRetry: () => void
  pollMs?: number
  giveUpMs?: number
}

function formatElapsed(ms: number): string {
  const total = Math.floor(ms / 1000)
  return `${Math.floor(total / 60)}분 ${String(total % 60).padStart(2, '0')}초`
}

export function ExtractionProgress({
  jobId,
  startedAt,
  retrying,
  onSucceeded,
  onRetry,
  pollMs = POLL_INTERVAL_MS,
  giveUpMs = GIVE_UP_AFTER_MS,
}: ExtractionProgressProps) {
  const [now, setNow] = useState(() => Date.now())
  const [resumedAt, setResumedAt] = useState(startedAt) // "상태 다시 확인"을 누른 시각. 포기 판정의 기준
  const gaveUp = now - resumedAt >= giveUpMs

  const query = useQuery({
    queryKey: extractionKey(jobId),
    queryFn: () => getExtraction(jobId),
    staleTime: 0,
    retry: false,
    refetchInterval: (q) => {
      const status = q.state.data?.status
      return status === 'SUCCEEDED' || status === 'FAILED' || gaveUp ? false : pollMs
    },
  })
  const job = query.data
  const finished = job?.status === 'SUCCEEDED' || job?.status === 'FAILED'

  useEffect(() => {
    if (finished) return undefined
    const timer = setInterval(() => setNow(Date.now()), Math.min(1000, pollMs))
    return () => clearInterval(timer)
  }, [finished, pollMs])

  useEffect(() => {
    if (job?.status === 'SUCCEEDED') onSucceeded()
  }, [job?.status, onSucceeded])

  if (job?.status === 'FAILED') {
    return (
      <div className="panel extraction-progress">
        <h2>추출 실패</h2>
        <div className="error-notice" role="alert">
          <p>{describeExtractionFailure(job.errorCode)}</p>
        </div>
        <button type="button" className="primary-button" disabled={retrying} onClick={onRetry}>
          다시 추출
        </button>
      </div>
    )
  }

  return (
    <div className="panel extraction-progress">
      <h2>결과창을 읽는 중입니다</h2>
      <p>
        경과 시간 <strong>{formatElapsed(now - startedAt)}</strong>
        {job && ` · ${job.status === 'QUEUED' ? '대기 중' : `추출 중(시도 ${job.attemptCount}회)`}`}
      </p>
      <p className="muted-text">보통 20초에서 1분 반쯤 걸립니다. 이 화면을 닫지 마세요.</p>
      <ErrorNotice error={query.error} />

      {(gaveUp || query.isError) && (
        <div className="button-row">
          {gaveUp && <p className="muted-text">6분이 지나도 끝나지 않았습니다.</p>}
          <button
            type="button"
            className="header-button"
            onClick={() => {
              setResumedAt(Date.now())
              setNow(Date.now())
              void query.refetch()
            }}
          >
            상태 다시 확인
          </button>
          <button type="button" className="header-button" disabled={retrying} onClick={onRetry}>
            다시 추출
          </button>
        </div>
      )}
    </div>
  )
}
