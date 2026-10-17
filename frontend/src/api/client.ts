// fetch 래퍼: 같은 출처 쿠키, CSRF 헤더 자동 첨부, 403 CSRF_INVALID 1회 재시도, ProblemDetail 파싱.
import type { AliasConflict, FieldError, ProblemBody } from './types'

const CSRF_COOKIE = 'XSRF-TOKEN'
const CSRF_HEADER = 'X-XSRF-TOKEN'
const MUTATING_METHODS = new Set(['POST', 'PUT', 'PATCH', 'DELETE'])

export class ApiError extends Error {
  status: number // HTTP 상태. 네트워크 실패는 0
  code: string // 서버가 준 code. 없으면 HTTP_<상태>
  detail: string | undefined
  errors: FieldError[]
  conflicts: AliasConflict[]
  existingSessionId: number | undefined

  constructor(status: number, body: ProblemBody = {}) {
    super(body.detail ?? body.title ?? `HTTP ${status}`)
    this.name = 'ApiError'
    this.status = status
    this.code = body.code ?? `HTTP_${status}`
    this.detail = body.detail
    this.errors = body.errors ?? []
    this.conflicts = body.conflicts ?? []
    this.existingSessionId = body.existingSessionId
  }
}

export type RequestOptions = {
  method?: string
  json?: unknown // JSON 본문
  form?: Record<string, string> // x-www-form-urlencoded 본문 (로그인)
}

function readCsrfCookie(): string | null {
  for (const part of document.cookie.split('; ')) {
    const eq = part.indexOf('=')
    if (eq > 0 && part.slice(0, eq) === CSRF_COOKIE) {
      return decodeURIComponent(part.slice(eq + 1))
    }
  }
  return null
}

async function send(path: string, options: RequestOptions): Promise<Response> {
  const method = (options.method ?? 'GET').toUpperCase()
  const headers = new Headers({ Accept: 'application/json, application/problem+json' })
  let body: string | undefined
  if (options.json !== undefined) {
    headers.set('Content-Type', 'application/json')
    body = JSON.stringify(options.json)
  } else if (options.form) {
    headers.set('Content-Type', 'application/x-www-form-urlencoded')
    body = new URLSearchParams(options.form).toString()
  }
  if (MUTATING_METHODS.has(method)) {
    const token = readCsrfCookie()
    if (token) headers.set(CSRF_HEADER, token)
  }
  try {
    return await fetch(path, { method, headers, body, credentials: 'same-origin' })
  } catch {
    throw new ApiError(0, { code: 'NETWORK_ERROR', detail: '서버에 연결하지 못했습니다.' })
  }
}

async function toApiError(response: Response): Promise<ApiError> {
  let body: ProblemBody = {}
  if ((response.headers.get('Content-Type') ?? '').includes('json')) {
    try {
      body = (await response.json()) as ProblemBody
    } catch {
      // 본문이 깨져 있으면 상태 코드만으로 오류를 만든다
    }
  }
  return new ApiError(response.status, body)
}

// 서버에 CSRF 토큰 쿠키(XSRF-TOKEN)를 발급받는다. 앱 시작·로그아웃 후·CSRF 재시도 때 부른다.
export async function fetchCsrf(): Promise<void> {
  await send('/api/csrf', {})
}

// 앱 시작 때 한 번. 백엔드가 꺼져 있어도 앱은 떠야 하므로 실패는 무시한다(변경 요청 때 재시도가 보완).
export async function initCsrf(): Promise<void> {
  try {
    await fetchCsrf()
  } catch {
    // 무시
  }
}

export async function apiRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const mutating = MUTATING_METHODS.has((options.method ?? 'GET').toUpperCase())
  let response = await send(path, options)

  if (!response.ok) {
    const error = await toApiError(response)
    if (!(mutating && error.status === 403 && error.code === 'CSRF_INVALID')) throw error
    // 토큰이 만료·교체됐다. 새 토큰을 받아 딱 한 번만 다시 보낸다(CSRF 실패 요청은 서버가 처리하지 않았으므로 안전).
    await fetchCsrf()
    response = await send(path, options)
    if (!response.ok) throw await toApiError(response)
  }

  if (response.status === 204) return undefined as T
  const text = await response.text()
  return (text ? JSON.parse(text) : undefined) as T
}
