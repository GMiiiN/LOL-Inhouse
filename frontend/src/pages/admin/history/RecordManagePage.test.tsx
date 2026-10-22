// 기록 관리 화면 테스트: 삭제 목록, 빈 상태, 복구, 막는 세트, 409 문구
import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { DeletedSetRow } from '../../../api/recordEditTypes'
import { jsonResponse, mockFetch, problemResponse } from '../../../test/mockFetch'
import { renderApp } from '../../../test/renderApp'

const me = { 'GET /api/admin/me': () => jsonResponse(200, { username: 'admin' }) }

function row(overrides: Partial<DeletedSetRow> = {}): DeletedSetRow {
  return {
    setId: 11,
    duelId: 4,
    duelSeq: 2,
    sessionId: 1,
    sessionStartDate: '2026-10-15',
    setNo: 3,
    shotDate: '2026-10-15',
    durationSec: null,
    winnerTeam: 'TEAM1',
    team1Side: 'BLUE',
    team1Kills: 20,
    team2Kills: 15,
    deletedAt: '2026-10-22T03:05:00Z',
    blockers: { fingerprintSetId: null, setNoSetId: null },
    fearlessConflicts: [],
    ...overrides,
  }
}

afterEach(() => vi.unstubAllGlobals())

describe('RecordManagePage', () => {
  it('삭제된 세트를 표로 보여 준다(삭제 시각은 서울 시간)', async () => {
    mockFetch({ ...me, 'GET /api/admin/deleted-sets': () => jsonResponse(200, [row()]) })
    renderApp('/admin/history')

    const cells = await screen.findAllByRole('cell')
    const text = cells.map((c) => c.textContent)
    expect(text).toContain('2026-10-15')
    expect(text).toContain('대결 2')
    expect(text).toContain('3세트')
    expect(text).toContain('—')
    expect(text).toContain('20 : 15')
    expect(text).toContain('10-22 12:05')
    expect(screen.getByRole('button', { name: '복구' })).toBeInTheDocument()
  })

  it('비었으면 안내를 보여 준다', async () => {
    mockFetch({ ...me, 'GET /api/admin/deleted-sets': () => jsonResponse(200, []) })
    renderApp('/admin/history')
    expect(await screen.findByText('삭제된 세트가 없어요')).toBeInTheDocument()
  })

  it('복구하면 POST를 보내고 경고를 보여 주며 목록을 다시 불러온다', async () => {
    let listed = 0
    const { calls } = mockFetch({
      ...me,
      'GET /api/admin/deleted-sets': () => jsonResponse(200, listed++ === 0 ? [row()] : []),
      'POST /api/admin/sets/11/restore': () =>
        jsonResponse(200, {
          setId: 11,
          warnings: ['뒤 세트와 챔피언이 겹쳐요'],
          fearlessConflicts: [{ setId: 12, duelId: 4, duelSeq: 2, setNo: 4, championKey: 'Ahri' }],
        }),
    })
    renderApp('/admin/history')

    await userEvent.click(await screen.findByRole('button', { name: '복구' }))

    expect(await screen.findByText('뒤 세트와 챔피언이 겹쳐요')).toBeInTheDocument()
    expect(screen.getByText(/피어리스 겹침: 대결 2 · 4세트 · Ahri/)).toBeInTheDocument()
    expect(await screen.findByText('삭제된 세트가 없어요')).toBeInTheDocument()
    expect(calls.some((c) => c.method === 'POST' && c.path === '/api/admin/sets/11/restore')).toBe(true)
  })

  it('막는 세트가 있으면 복구 버튼 대신 새 탭 링크를 보여 준다', async () => {
    mockFetch({
      ...me,
      'GET /api/admin/deleted-sets': () =>
        jsonResponse(200, [row({ blockers: { fingerprintSetId: 30, setNoSetId: 31 } })]),
    })
    renderApp('/admin/history')

    const fp = await screen.findByRole('link', { name: '같은 경기 세트 보기' })
    expect(fp).toHaveAttribute('href', '/sets/30')
    expect(fp).toHaveAttribute('target', '_blank')
    expect(screen.getByRole('link', { name: '같은 번호 세트 보기' })).toHaveAttribute('href', '/sets/31')
    expect(screen.queryByRole('button', { name: '복구' })).toBeNull()
  })

  it('피어리스 충돌은 경고로 보이고 복구는 막지 않는다', async () => {
    mockFetch({
      ...me,
      'GET /api/admin/deleted-sets': () =>
        jsonResponse(200, [
          row({ fearlessConflicts: [{ setId: 12, duelId: 4, duelSeq: 2, setNo: 4, championKey: 'Ahri' }] }),
        ]),
    })
    renderApp('/admin/history')
    expect(await screen.findByText(/다른 세트와 피어리스 챔피언이 겹쳐요/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '복구' })).toBeEnabled()
  })

  it('복구가 409로 거절되면 한국어 문구와 막는 세트 링크를 보여 준다', async () => {
    mockFetch({
      ...me,
      'GET /api/admin/deleted-sets': () => jsonResponse(200, [row()]),
      'POST /api/admin/sets/11/restore': () =>
        problemResponse(409, 'RESTORE_FINGERPRINT_CONFLICT', { existingSetId: 40, existingDuelId: 4, existingSetNo: 1 }),
    })
    renderApp('/admin/history')

    await userEvent.click(await screen.findByRole('button', { name: '복구' }))

    const alert = await screen.findByRole('alert')
    expect(within(alert).getByText(/같은 경기가 이미 등록돼 있어 복구할 수 없어요/)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '막는 세트 보기' })).toHaveAttribute('href', '/sets/40')
  })
})
