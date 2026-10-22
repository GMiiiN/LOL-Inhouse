// 묶음 상세의 세트 삭제·복구 버튼 테스트
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { AdminSetSummary } from '../../api/types'
import { jsonResponse, mockFetch, noContent } from '../../test/mockFetch'
import { renderApp } from '../../test/renderApp'

const me = { 'GET /api/admin/me': () => jsonResponse(200, { username: 'admin' }) }

function set(setId: number, setNo: number, deleted: boolean): AdminSetSummary {
  return {
    setId,
    setNo,
    team1Side: 'BLUE',
    winnerTeam: 'TEAM1',
    durationSec: 1800,
    shotDate: '2026-10-15',
    team1Kills: 20,
    team2Kills: 10,
    source: 'IMAGE',
    createdAt: '2026-10-15T10:00:00Z',
    deleted,
  }
}

const session = (sets: AdminSetSummary[]) => ({
  id: 1,
  startDate: '2026-10-15',
  duels: [{ id: 4, seq: 2, fearlessMode: 'RESET', sets }],
})

afterEach(() => {
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})

describe('SessionDetailPage 세트 삭제·복구', () => {
  it('유효 줄에는 수정 링크가 있고 삭제된 줄에는 없다', async () => {
    mockFetch({
      ...me,
      'GET /api/admin/inhouse-sessions/1': () => jsonResponse(200, session([set(7, 1, false), set(8, 2, true)])),
    })
    renderApp('/admin/sessions/1')

    expect(await screen.findByRole('link', { name: '세트 1 수정' })).toHaveAttribute('href', '/admin/sets/7/edit')
    expect(screen.queryByRole('link', { name: '세트 2 수정' })).toBeNull()
    expect(screen.getByRole('button', { name: '세트 2 복구' })).toBeInTheDocument()
  })

  it('확인을 취소하면 삭제 요청을 보내지 않는다', async () => {
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(false)
    const { calls } = mockFetch({
      ...me,
      'GET /api/admin/inhouse-sessions/1': () => jsonResponse(200, session([set(7, 1, false)])),
    })
    renderApp('/admin/sessions/1')

    await userEvent.click(await screen.findByRole('button', { name: '세트 1 삭제' }))

    expect(confirm).toHaveBeenCalledWith('대결 2 · 1세트를 삭제할까요? 기록 관리에서 복구할 수 있어요.')
    expect(calls.some((c) => c.method === 'DELETE')).toBe(false)
  })

  it('삭제하면 DELETE 후 묶음 상세를 다시 불러온다', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    let deleted = false
    const { calls } = mockFetch({
      ...me,
      'GET /api/admin/inhouse-sessions/1': () => jsonResponse(200, session([set(7, 1, deleted)])),
      'DELETE /api/admin/sets/7': () => {
        deleted = true
        return noContent()
      },
    })
    renderApp('/admin/sessions/1')

    await userEvent.click(await screen.findByRole('button', { name: '세트 1 삭제' }))

    expect(await screen.findByRole('button', { name: '세트 1 복구' })).toBeInTheDocument()
    expect(calls.filter((c) => c.path === '/api/admin/inhouse-sessions/1').length).toBe(2)
  })

  it('삭제된 줄의 복구를 누르면 POST 후 다시 불러오고 경고를 보여 준다', async () => {
    let deleted = true
    const { calls } = mockFetch({
      ...me,
      'GET /api/admin/inhouse-sessions/1': () => jsonResponse(200, session([set(7, 1, deleted)])),
      'POST /api/admin/sets/7/restore': () => {
        deleted = false
        return jsonResponse(200, { setId: 7, warnings: ['진영 확인'], fearlessConflicts: [] })
      },
    })
    renderApp('/admin/sessions/1')

    await userEvent.click(await screen.findByRole('button', { name: '세트 1 복구' }))

    expect(await screen.findByRole('button', { name: '세트 1 삭제' })).toBeInTheDocument()
    expect(screen.getByText('진영 확인')).toBeInTheDocument()
    await waitFor(() => expect(calls.some((c) => c.method === 'POST')).toBe(true))
  })
})
