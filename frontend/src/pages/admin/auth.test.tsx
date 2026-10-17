// 로그인·미로그인 리다이렉트·로그아웃 테스트
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { ME_KEY } from '../../api/queryKeys'
import { jsonResponse, mockFetch, noContent, problemResponse } from '../../test/mockFetch'
import { renderApp } from '../../test/renderApp'

describe('담당자 인증 흐름', () => {
  it('미로그인으로 /admin/players에 가면 로그인 화면으로 보낸다', async () => {
    mockFetch({ 'GET /api/admin/me': () => problemResponse(401, 'UNAUTHORIZED') })
    const { router } = renderApp('/admin/players')

    expect(await screen.findByRole('heading', { name: '담당자 로그인' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/admin/login')
  })

  it('로그인에 실패하면 사용자 없음/비밀번호 틀림을 구분하지 않는 같은 메시지를 보여 준다', async () => {
    const user = userEvent.setup()
    mockFetch({
      'GET /api/admin/me': () => problemResponse(401, 'UNAUTHORIZED'),
      'POST /api/admin/login': () => problemResponse(401, 'LOGIN_FAILED'),
    })
    renderApp('/admin/login')

    await user.type(await screen.findByLabelText('아이디'), 'admin')
    await user.type(screen.getByLabelText('비밀번호'), 'wrong')
    await user.click(screen.getByRole('button', { name: '로그인' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('아이디 또는 비밀번호가 맞지 않습니다')
  })

  it('로그인에 성공하면 form-urlencoded로 보내고, 원래 가려던 곳으로 이동한다', async () => {
    const user = userEvent.setup()
    let loggedIn = false
    const { calls } = mockFetch({
      'GET /api/admin/me': () =>
        loggedIn ? jsonResponse(200, { username: 'admin' }) : problemResponse(401, 'UNAUTHORIZED'),
      'POST /api/admin/login': () => {
        loggedIn = true
        return jsonResponse(200, { username: 'admin' })
      },
      'GET /api/admin/players': () => jsonResponse(200, []),
    })
    const { router } = renderApp('/admin/players')

    await user.type(await screen.findByLabelText('아이디'), 'admin')
    await user.type(screen.getByLabelText('비밀번호'), 'pw 1234')
    await user.click(screen.getByRole('button', { name: '로그인' }))

    expect(await screen.findByRole('heading', { name: '선수 관리' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/admin/players')
    const loginCall = calls.find((c) => c.method === 'POST' && c.path === '/api/admin/login')
    expect(loginCall?.headers.get('Content-Type')).toBe('application/x-www-form-urlencoded')
    expect(loginCall?.body).toBe('username=admin&password=pw+1234')
  })

  it('로그아웃하면 POST /api/admin/logout, GET /api/csrf 순으로 부르고 공개 홈으로 간다', async () => {
    const user = userEvent.setup()
    let loggedIn = true
    const { calls } = mockFetch({
      'GET /api/admin/me': () =>
        loggedIn ? jsonResponse(200, { username: 'admin' }) : problemResponse(401, 'UNAUTHORIZED'),
      'GET /api/admin/inhouse-sessions': () => jsonResponse(200, []),
      'POST /api/admin/logout': () => {
        loggedIn = false
        return noContent()
      },
      'GET /api/csrf': () => noContent(),
    })
    const { router } = renderApp('/admin')

    await user.click(await screen.findByRole('button', { name: '로그아웃' }))

    await waitFor(() => expect(router.state.location.pathname).toBe('/'))
    const names = calls.map((c) => `${c.method} ${c.path}`)
    expect(names.indexOf('GET /api/csrf')).toBeGreaterThan(names.indexOf('POST /api/admin/logout'))
  })

  it('로그아웃 204 뒤 CSRF 재발급이 네트워크 오류여도 캐시를 정리하고 홈으로 간다', async () => {
    const user = userEvent.setup()
    let loggedIn = true
    const { calls } = mockFetch({
      'GET /api/admin/me': () =>
        loggedIn ? jsonResponse(200, { username: 'admin' }) : problemResponse(401, 'UNAUTHORIZED'),
      'GET /api/admin/inhouse-sessions': () => jsonResponse(200, []),
      'POST /api/admin/logout': () => {
        loggedIn = false
        return noContent()
      },
      'GET /api/csrf': () => {
        throw new TypeError('network down')
      },
    })
    const { router, client: queryClient } = renderApp('/admin')

    await user.click(await screen.findByRole('button', { name: '로그아웃' }))

    await waitFor(() => expect(router.state.location.pathname).toBe('/'))
    expect(queryClient.getQueryData(ME_KEY)).toBeNull()
    expect(calls.some((c) => c.path === '/api/csrf' && c.method === 'GET')).toBe(true)
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('처음 /api/admin/me 401 응답이 로그인 성공보다 늦게 도착해도 로그인 상태를 덮어쓰지 않는다', async () => {
    const user = userEvent.setup()
    let loggedIn = false
    let releaseFirstMe: () => void = () => {}
    const firstMeGate = new Promise<void>((resolve) => {
      releaseFirstMe = resolve
    })
    let meCalls = 0
    mockFetch({
      'GET /api/admin/me': async () => {
        meCalls += 1
        if (meCalls === 1) {
          await firstMeGate // 첫 조회는 로그인이 끝난 뒤에야 401로 답한다
          return problemResponse(401, 'UNAUTHORIZED')
        }
        return loggedIn ? jsonResponse(200, { username: 'admin' }) : problemResponse(401, 'UNAUTHORIZED')
      },
      'POST /api/admin/login': () => {
        loggedIn = true
        return jsonResponse(200, { username: 'admin' })
      },
      'GET /api/admin/inhouse-sessions': () => jsonResponse(200, []),
    })
    const { router, client: queryClient } = renderApp('/admin/login')

    await user.type(await screen.findByLabelText('아이디'), 'admin')
    await user.type(screen.getByLabelText('비밀번호'), 'pw')
    await user.click(screen.getByRole('button', { name: '로그인' }))
    await waitFor(() => expect(router.state.location.pathname).toBe('/admin'))

    releaseFirstMe()
    await new Promise((resolve) => setTimeout(resolve, 50))

    expect(queryClient.getQueryData(ME_KEY)).toEqual({ username: 'admin' })
    expect(router.state.location.pathname).toBe('/admin')
  })

  it('이미 로그인한 상태로 /admin/login에 가면 /admin으로 보낸다', async () => {
    mockFetch({
      'GET /api/admin/me': () => jsonResponse(200, { username: 'admin' }),
      'GET /api/admin/inhouse-sessions': () => jsonResponse(200, []),
    })
    const { router } = renderApp('/admin/login')

    await waitFor(() => expect(router.state.location.pathname).toBe('/admin'))
  })

  it('작업 중 세션이 만료돼 401이 오면 로그인 화면으로 보낸다', async () => {
    mockFetch({
      'GET /api/admin/me': () => jsonResponse(200, { username: 'admin' }),
      'GET /api/admin/players': () => problemResponse(401, 'UNAUTHORIZED'),
    })
    const { router } = renderApp('/admin/players')

    await waitFor(() => expect(router.state.location.pathname).toBe('/admin/login'))
  })
})
