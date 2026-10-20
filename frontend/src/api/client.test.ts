// API 래퍼 테스트: CSRF 헤더 첨부, 403 CSRF_INVALID 1회 재시도, ProblemDetail 파싱
import { describe, expect, it } from 'vitest'
import { jsonResponse, mockFetch, noContent, problemResponse } from '../test/mockFetch'
import { ApiError, apiRequest, initCsrf } from './client'

describe('apiRequest', () => {
  it('변경 요청에는 XSRF-TOKEN 쿠키 값을 X-XSRF-TOKEN 헤더로 보낸다', async () => {
    document.cookie = 'XSRF-TOKEN=token-1; path=/'
    const { calls } = mockFetch({ 'POST /api/x': () => jsonResponse(200, { ok: true }) })

    await apiRequest('/api/x', { method: 'POST', json: { a: 1 } })

    expect(calls[0].headers.get('X-XSRF-TOKEN')).toBe('token-1')
    expect(calls[0].headers.get('Content-Type')).toBe('application/json')
    expect(calls[0].body).toBe('{"a":1}')
  })

  it('GET에는 CSRF 헤더를 붙이지 않는다', async () => {
    document.cookie = 'XSRF-TOKEN=token-1; path=/'
    const { calls } = mockFetch({ 'GET /api/x': () => jsonResponse(200, {}) })

    await apiRequest('/api/x')

    expect(calls[0].headers.has('X-XSRF-TOKEN')).toBe(false)
  })

  it('403 CSRF_INVALID이면 /api/csrf를 다시 불러 새 토큰으로 한 번만 재시도한다', async () => {
    document.cookie = 'XSRF-TOKEN=old; path=/'
    let attempt = 0
    const { calls } = mockFetch({
      'GET /api/csrf': () => {
        document.cookie = 'XSRF-TOKEN=new; path=/'
        return noContent()
      },
      'POST /api/x': (request) => {
        attempt += 1
        return request.headers.get('X-XSRF-TOKEN') === 'new'
          ? jsonResponse(200, { done: attempt })
          : problemResponse(403, 'CSRF_INVALID')
      },
    })

    const result = await apiRequest<{ done: number }>('/api/x', { method: 'POST' })

    expect(result.done).toBe(2)
    expect(calls.map((c) => `${c.method} ${c.path}`)).toEqual(['POST /api/x', 'GET /api/csrf', 'POST /api/x'])
    expect(calls[2].headers.get('X-XSRF-TOKEN')).toBe('new')
  })

  it('재시도해도 CSRF_INVALID이면 더 시도하지 않고 오류를 던진다 (요청 2번 + 토큰 1번)', async () => {
    const { calls } = mockFetch({
      'GET /api/csrf': () => noContent(),
      'POST /api/x': () => problemResponse(403, 'CSRF_INVALID'),
    })

    await expect(apiRequest('/api/x', { method: 'POST' })).rejects.toMatchObject({ code: 'CSRF_INVALID' })

    expect(calls).toHaveLength(3)
  })

  it('CSRF가 아닌 403은 재시도하지 않는다', async () => {
    const { calls } = mockFetch({ 'POST /api/x': () => problemResponse(403, 'FORBIDDEN') })

    await expect(apiRequest('/api/x', { method: 'POST' })).rejects.toMatchObject({ status: 403, code: 'FORBIDDEN' })

    expect(calls).toHaveLength(1)
  })

  it('ProblemDetail의 code, errors, conflicts를 ApiError로 옮긴다', async () => {
    mockFetch({
      'POST /api/x': () =>
        problemResponse(409, 'ALIAS_CONFLICT', {
          errors: [{ field: 'nicknames', message: '중복' }],
          conflicts: [{ nickname: '닉', playerId: 7, playerDisplayName: '홍길동' }],
        }),
    })

    const error = await apiRequest('/api/x', { method: 'POST' }).catch((e: unknown) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect(error).toMatchObject({
      status: 409,
      code: 'ALIAS_CONFLICT',
      errors: [{ field: 'nicknames', message: '중복' }],
      conflicts: [{ nickname: '닉', playerId: 7, playerDisplayName: '홍길동' }],
    })
  })

  it('FormData 본문은 Content-Type을 지정하지 않고(브라우저가 boundary를 붙임) CSRF 헤더와 함께 보낸다', async () => {
    document.cookie = 'XSRF-TOKEN=token-1; path=/'
    const { calls } = mockFetch({ 'POST /api/up': () => jsonResponse(202, { jobId: 1 }) })
    const formData = new FormData()
    formData.append('topSide', 'BLUE')

    await apiRequest('/api/up', { method: 'POST', formData })

    expect(calls[0].headers.has('Content-Type')).toBe(false)
    expect(calls[0].headers.get('X-XSRF-TOKEN')).toBe('token-1')
    expect(calls[0].formData?.get('topSide')).toBe('BLUE')
  })

  it('M4 확장 오류 필드(violations, warnings, existing*, savedSetId)를 ApiError에 보존한다', async () => {
    mockFetch({
      'POST /api/a': () =>
        problemResponse(422, 'SET_RULES_VIOLATED', { violations: ['밴 부족'], warnings: ['합계 불일치'] }),
      'POST /api/b': () =>
        problemResponse(409, 'DUPLICATE_SET', { existingSetId: 7, existingDuelId: 3, existingSetNo: 2 }),
      'POST /api/c': () => problemResponse(409, 'EXTRACTION_ALREADY_SAVED', { savedSetId: 9 }),
    })

    await expect(apiRequest('/api/a', { method: 'POST' })).rejects.toMatchObject({
      violations: ['밴 부족'],
      warnings: ['합계 불일치'],
    })
    await expect(apiRequest('/api/b', { method: 'POST' })).rejects.toMatchObject({
      existingSetId: 7,
      existingDuelId: 3,
      existingSetNo: 2,
    })
    await expect(apiRequest('/api/c', { method: 'POST' })).rejects.toMatchObject({ savedSetId: 9 })
  })

  it('확장 필드가 없으면 빈 목록·undefined다', async () => {
    mockFetch({ 'POST /api/a': () => problemResponse(400, 'VALIDATION_FAILED') })

    const error = (await apiRequest('/api/a', { method: 'POST' }).catch((e: unknown) => e)) as ApiError

    expect(error.violations).toEqual([])
    expect(error.warnings).toEqual([])
    expect(error.existingSetId).toBeUndefined()
  })

  it('204 응답은 undefined를 돌려주고, 본문이 JSON이 아닌 오류도 상태 코드로 표현한다', async () => {
    mockFetch({
      'DELETE /api/a': () => noContent(),
      'GET /api/b': () => new Response('<html>bad gateway</html>', { status: 502 }),
    })

    await expect(apiRequest('/api/a', { method: 'DELETE' })).resolves.toBeUndefined()
    await expect(apiRequest('/api/b')).rejects.toMatchObject({ status: 502, code: 'HTTP_502' })
  })

  it('네트워크 실패는 status 0, code NETWORK_ERROR로 바꾼다', async () => {
    mockFetch({}) // 처리기 없음 -> fetch가 예외를 던진다

    await expect(apiRequest('/api/x')).rejects.toMatchObject({ status: 0, code: 'NETWORK_ERROR' })
  })
})

describe('initCsrf', () => {
  it('/api/csrf를 호출하고, 서버가 꺼져 있어도 예외를 던지지 않는다', async () => {
    const { calls } = mockFetch({})

    await expect(initCsrf()).resolves.toBeUndefined()

    expect(calls[0]).toMatchObject({ method: 'GET', path: '/api/csrf' })
  })
})
