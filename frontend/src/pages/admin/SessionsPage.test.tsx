// 내전 관리 화면 테스트: 목록, 묶음 생성 충돌 안내, 새 대결(피어리스 방식 선택)
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { jsonResponse, mockFetch, problemResponse } from '../../test/mockFetch'
import { renderApp } from '../../test/renderApp'

const me = { 'GET /api/admin/me': () => jsonResponse(200, { username: 'admin' }) }

describe('SessionsPage', () => {
  it('묶음 목록을 서버가 준 순서(시작일 내림차순)대로 시작일과 대결 수와 함께 보여 준다', async () => {
    mockFetch({
      ...me,
      'GET /api/admin/inhouse-sessions': () =>
        jsonResponse(200, [
          { id: 2, startDate: '2026-10-15', duelCount: 3 },
          { id: 1, startDate: '2026-10-01', duelCount: 1 },
        ]),
    })
    renderApp('/admin')

    const rows = await screen.findAllByRole('row')
    expect(rows[1]).toHaveTextContent('2026-10-15')
    expect(rows[1]).toHaveTextContent('3')
    expect(rows[2]).toHaveTextContent('2026-10-01')
  })

  it('같은 시작일 묶음이 있으면(409 SESSION_DATE_CONFLICT) 기존 묶음으로 가는 링크를 보여 준다', async () => {
    const user = userEvent.setup()
    mockFetch({
      ...me,
      'GET /api/admin/inhouse-sessions': () => jsonResponse(200, []),
      'POST /api/admin/inhouse-sessions': () =>
        problemResponse(409, 'SESSION_DATE_CONFLICT', { existingSessionId: 42 }),
    })
    renderApp('/admin')

    await user.click(await screen.findByRole('button', { name: '묶음 만들기' }))

    const link = await screen.findByRole('link', { name: '기존 묶음으로 이동' })
    expect(link).toHaveAttribute('href', '/admin/sessions/42')
  })

  it('묶음을 만들면 시작일을 보내고 그 묶음 상세로 이동한다', async () => {
    const user = userEvent.setup()
    const { calls } = mockFetch({
      ...me,
      'GET /api/admin/inhouse-sessions': () => jsonResponse(200, []),
      'POST /api/admin/inhouse-sessions': () => jsonResponse(201, { id: 5, startDate: '2026-10-20', duelCount: 0 }),
      'GET /api/admin/inhouse-sessions/5': () => jsonResponse(200, { id: 5, startDate: '2026-10-20', duels: [] }),
    })
    const { router } = renderApp('/admin')

    const dateInput = await screen.findByLabelText('시작일')
    await user.clear(dateInput)
    await user.type(dateInput, '2026-10-20')
    await user.click(screen.getByRole('button', { name: '묶음 만들기' }))

    await waitFor(() => expect(router.state.location.pathname).toBe('/admin/sessions/5'))
    const post = calls.find((c) => c.method === 'POST' && c.path === '/api/admin/inhouse-sessions')
    expect(JSON.parse(post?.body ?? '')).toEqual({ startDate: '2026-10-20' })
  })
})

describe('SessionDetailPage', () => {
  it('새 대결은 고른 피어리스 방식(기본 초기화)을 본문에 담아 만들고, 대결 목록에 방식을 표시한다', async () => {
    const user = userEvent.setup()
    let duels: { id: number; seq: number; fearlessMode: string }[] = [{ id: 11, seq: 1, fearlessMode: 'RESET' }]
    const { calls } = mockFetch({
      ...me,
      'GET /api/admin/inhouse-sessions/5': () => jsonResponse(200, { id: 5, startDate: '2026-10-20', duels }),
      'POST /api/admin/inhouse-sessions/5/duels': (request) => {
        const mode = JSON.parse(request.body ?? '{}').fearlessMode as string
        duels = [...duels, { id: 12, seq: 2, fearlessMode: mode }]
        return jsonResponse(201, { id: 12, seq: 2, fearlessMode: mode })
      },
    })
    renderApp('/admin/sessions/5')

    expect(await screen.findByText('대결 1')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '세트 등록' })).toHaveAttribute('href', '/admin/duels/11/sets/new')
    expect(screen.getByText('피어리스 초기화')).toBeInTheDocument()

    await user.click(screen.getByRole('radio', { name: '이어가기' }))
    await user.click(screen.getByRole('button', { name: '새 대결' }))

    expect(await screen.findByText('대결 2')).toBeInTheDocument()
    expect(screen.getByText('피어리스 이어가기')).toBeInTheDocument()
    const post = calls.find((c) => c.method === 'POST')
    expect(JSON.parse(post?.body ?? '')).toEqual({ fearlessMode: 'CONTINUE' })
  })

  it('첫 대결은 방식 선택 없이 초기화로만 만든다', async () => {
    const user = userEvent.setup()
    const { calls } = mockFetch({
      ...me,
      'GET /api/admin/inhouse-sessions/5': () => jsonResponse(200, { id: 5, startDate: '2026-10-20', duels: [] }),
      'POST /api/admin/inhouse-sessions/5/duels': () => jsonResponse(201, { id: 11, seq: 1, fearlessMode: 'RESET' }),
    })
    renderApp('/admin/sessions/5')

    await user.click(await screen.findByRole('button', { name: '새 대결' }))

    expect(screen.queryByRole('radio', { name: '이어가기' })).not.toBeInTheDocument()
    await waitFor(() => expect(calls.some((c) => c.method === 'POST')).toBe(true))
    expect(JSON.parse(calls.find((c) => c.method === 'POST')?.body ?? '')).toEqual({ fearlessMode: 'RESET' })
  })
})
