// 담당자 전용 원본 결과창 이미지 받기. apiRequest는 JSON만 읽으므로 blob은 여기서 직접 받는다.
import { ApiError } from './client'
import { adminSetImagePath } from './public'
import type { ProblemBody } from './types'

export async function fetchAdminSetImage(setId: number): Promise<Blob> {
  let response: Response
  try {
    response = await fetch(adminSetImagePath(setId), { credentials: 'same-origin' })
  } catch {
    throw new ApiError(0, { code: 'NETWORK_ERROR', detail: '서버에 연결하지 못했습니다.' })
  }
  if (!response.ok) {
    let body: ProblemBody = {}
    if ((response.headers.get('Content-Type') ?? '').includes('json')) {
      try {
        body = (await response.json()) as ProblemBody
      } catch {
        // 본문이 깨져 있으면 상태 코드만 쓴다
      }
    }
    throw new ApiError(response.status, body)
  }
  return response.blob()
}
