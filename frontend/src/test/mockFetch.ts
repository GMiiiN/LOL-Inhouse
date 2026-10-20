// 테스트용 가짜 fetch. "METHOD /경로" → 응답 함수 표로 라우팅하고, 받은 요청을 calls에 기록한다.
import { vi } from 'vitest'

export type MockRequest = {
  method: string
  path: string
  headers: Headers
  body: string | null
  formData: FormData | null // multipart 업로드 본문
}

export type MockHandler = (request: MockRequest) => Response | Promise<Response> // Promise는 응답 지연 흉내용

export function jsonResponse(status: number, body: unknown, contentType = 'application/json'): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': contentType } })
}

export function problemResponse(status: number, code: string, extra: Record<string, unknown> = {}): Response {
  return jsonResponse(status, { status, code, detail: `서버 detail ${code}`, ...extra }, 'application/problem+json')
}

export const noContent = () => new Response(null, { status: 204 })

export function mockFetch(routes: Record<string, MockHandler>) {
  const calls: MockRequest[] = []
  const fn = vi.fn(async (input: string | URL | Request, init?: RequestInit) => {
    const path = String(input)
    const method = (init?.method ?? 'GET').toUpperCase()
    const request: MockRequest = {
      method,
      path,
      headers: new Headers(init?.headers),
      body: typeof init?.body === 'string' ? init.body : null,
      formData: init?.body instanceof FormData ? init.body : null,
    }
    calls.push(request)
    const handler = routes[`${method} ${path}`]
    if (!handler) throw new Error(`mockFetch: 처리기 없음 ${method} ${path}`)
    return handler(request)
  })
  vi.stubGlobal('fetch', fn)
  return { calls, fn }
}
