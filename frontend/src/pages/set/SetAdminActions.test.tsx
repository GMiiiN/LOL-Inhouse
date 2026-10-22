// 세트 상세의 담당자 삭제 버튼 테스트
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { jsonResponse, mockFetch, noContent, problemResponse } from '../../test/mockFetch'
import { makeSet } from '../../test/recordFixtures'
import { renderApp } from '../../test/renderApp'

const setDetail = makeSet({ setId: 7, duelId: 4, duelSeq: 2, setNo: 3, sessionId: 9 })

afterEach(() => {
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})

describe('SetAdminActions', () => {
  it('방문자에게는 삭제 버튼이 없다', async () => {
    const { calls } = mockFetch({
      'GET /api/admin/me': () => jsonResponse(401, { status: 401, code: 'UNAUTHENTICATED' }),
      'GET /api/sets/7': () => jsonResponse(200, setDetail),
    })
    renderApp('/sets/7')
    await waitFor(() => expect(calls.some((c) => c.path === '/api/admin/me')).toBe(true))
    await screen.findByText(/결과창 날짜/)
    expect(screen.queryByRole('button', { name: '삭제' })).toBeNull()
  })

  it('담당자가 확인하면 삭제하고 관리자 묶음 상세로 이동한다', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    const { calls } = mockFetch({
      'GET /api/admin/me': () => jsonResponse(200, { username: 'admin' }),
      'GET /api/sets/7': () => jsonResponse(200, setDetail),
      'DELETE /api/admin/sets/7': noContent,
      'GET /api/admin/inhouse-sessions/9': () => jsonResponse(200, { id: 9, startDate: '2026-10-15', duels: [] }),
    })
    const { router } = renderApp('/sets/7')

    await userEvent.click(await screen.findByRole('button', { name: '삭제' }))

    await waitFor(() => expect(router.state.location.pathname).toBe('/admin/sessions/9'))
    expect(window.confirm).toHaveBeenCalledWith('대결 2 · 3세트를 삭제할까요? 기록 관리에서 복구할 수 있어요.')
    expect(calls.some((c) => c.method === 'DELETE' && c.path === '/api/admin/sets/7')).toBe(true)
  })

  it('확인을 취소하면 아무것도 보내지 않는다', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(false)
    const { calls } = mockFetch({
      'GET /api/admin/me': () => jsonResponse(200, { username: 'admin' }),
      'GET /api/sets/7': () => jsonResponse(200, setDetail),
    })
    renderApp('/sets/7')
    await userEvent.click(await screen.findByRole('button', { name: '삭제' }))
    expect(calls.some((c) => c.method === 'DELETE')).toBe(false)
  })

  it('이미 삭제된 세트면 오류 문구를 보여 준다', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    mockFetch({
      'GET /api/admin/me': () => jsonResponse(200, { username: 'admin' }),
      'GET /api/sets/7': () => jsonResponse(200, setDetail),
      'DELETE /api/admin/sets/7': () => problemResponse(409, 'SET_ALREADY_DELETED'),
    })
    renderApp('/sets/7')
    await userEvent.click(await screen.findByRole('button', { name: '삭제' }))
    expect(await screen.findByText('이미 삭제된 세트예요.')).toBeInTheDocument()
  })
})
