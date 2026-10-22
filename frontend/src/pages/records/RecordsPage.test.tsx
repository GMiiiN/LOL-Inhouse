import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { jsonResponse, mockFetch } from '../../test/mockFetch'
import { makeDuel, makePage } from '../../test/recordFixtures'
import { renderApp } from '../../test/renderApp'

const players = {
  totalSets: 1,
  players: [{ playerId: 3, displayName: '철수', registered: true }],
}
const champions = { version: '1', champions: [{ id: 'Ahri', key: '103', name: '아리', iconUrl: 'https://x/Ahri.png' }] }

afterEach(() => vi.unstubAllGlobals())

// 경로에 쿼리가 붙으므로 mockFetch 표 대신 경로 접두사로 라우팅하는 처리기를 쓴다.
function mockRecords(handler: (path: string) => Response) {
  const base = mockFetch({
    'GET /api/players': () => jsonResponse(200, players),
    'GET /api/champions': () => jsonResponse(200, champions),
    'GET /api/admin/me': () => jsonResponse(401, { status: 401, code: 'UNAUTHENTICATED' }),
  })
  const original = globalThis.fetch
  const recordCalls: string[] = []
  vi.stubGlobal('fetch', (input: string, init?: RequestInit) => {
    if (String(input).startsWith('/api/records')) {
      recordCalls.push(String(input))
      return Promise.resolve(handler(String(input)))
    }
    return original(input, init)
  })
  return { recordCalls, base }
}

describe('RecordsPage', () => {
  it('필터가 URL에서 읽혀 API 파라미터(playerId 등)로 가고 CSV 링크에도 실린다', async () => {
    const { recordCalls } = mockRecords(() => jsonResponse(200, makePage([makeDuel()])))
    renderApp('/records?from=2026-10-01&to=2026-10-31&player=3&champion=Ahri&championScope=PLAYER_PICK')
    await screen.findByText('대결 1')
    expect(recordCalls[0]).toBe(
      '/api/records?from=2026-10-01&to=2026-10-31&playerId=3&champion=Ahri&championScope=PLAYER_PICK',
    )
    expect(screen.getByRole('link', { name: 'CSV' })).toHaveAttribute(
      'href',
      '/api/records/export?from=2026-10-01&to=2026-10-31&playerId=3&champion=Ahri&championScope=PLAYER_PICK',
    )
  })

  it('맨 위에 총 경기 수와 세트 수를 보여 준다', async () => {
    mockRecords(() => jsonResponse(200, { ...makePage([makeDuel()]), totals: { duels: 7, sets: 19 } }))
    renderApp('/records')
    expect(await screen.findByText('총 7경기 · 19세트')).toBeInTheDocument()
  })

  it('챔피언을 고르면 URL에 반영되고 다시 조회한다', async () => {
    const { recordCalls } = mockRecords(() => jsonResponse(200, makePage([makeDuel()])))
    const { router } = renderApp('/records')
    await screen.findByText('대결 1')
    await screen.findByRole('option', { name: '아리' })
    await userEvent.selectOptions(screen.getByLabelText('챔피언'), 'Ahri')
    expect(router.state.location.search).toBe('?champion=Ahri')
    await waitFor(() => expect(recordCalls).toContain('/api/records?champion=Ahri'))
  })

  it('선수를 전체로 바꾸면 숨은 PLAYER_PICK 범위도 지워져 API와 CSV가 ANY로 돌아간다', async () => {
    const { recordCalls } = mockRecords(() => jsonResponse(200, makePage([makeDuel()])))
    const { router } = renderApp('/records?player=3&champion=Ahri&championScope=PLAYER_PICK')
    await screen.findByText('대결 1')
    await screen.findByRole('option', { name: '철수' })
    await userEvent.selectOptions(screen.getByLabelText('선수'), '')
    expect(router.state.location.search).toBe('?champion=Ahri')
    await waitFor(() => expect(recordCalls).toContain('/api/records?champion=Ahri'))
    expect(screen.getByRole('link', { name: 'CSV' })).toHaveAttribute('href', '/api/records/export?champion=Ahri')
  })

  it('더 보기는 nextBefore를 before로 보내고 다음 묶음을 이어 붙인다', async () => {
    const { recordCalls } = mockRecords((path) =>
      path.includes('before=')
        ? jsonResponse(200, makePage([makeDuel({ duelId: 8, seq: 2 })], null, '2026-10-10'))
        : jsonResponse(200, makePage([makeDuel()], '2026-10-18')),
    )
    renderApp('/records')
    await screen.findByText('대결 1')
    await userEvent.click(screen.getByRole('button', { name: '더 보기' }))
    await screen.findByText('대결 2')
    expect(recordCalls[1]).toBe('/api/records?before=2026-10-18')
    expect(screen.getByText('대결 1')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '더 보기' })).toBeNull()
  })

  it('필터가 있고 결과가 없으면 안내와 조건 지우기를 보여 주고, 누르면 URL을 비운다', async () => {
    mockRecords((path) =>
      path.includes('champion=')
        ? jsonResponse(200, makePage([]))
        : jsonResponse(200, makePage([makeDuel()])),
    )
    const { router } = renderApp('/records?champion=Ahri')
    await screen.findByText('조건에 맞는 경기가 없어요.')
    const clear = screen.getAllByRole('button', { name: '조건 지우기' })
    await userEvent.click(clear[clear.length - 1])
    expect(router.state.location.search).toBe('')
    await screen.findByText('대결 1')
  })

  it('필터 없이 기록이 없으면 빈 안내만 보여 준다', async () => {
    mockRecords(() => jsonResponse(200, makePage([])))
    renderApp('/records')
    await screen.findByText('아직 기록된 경기가 없어요.')
    expect(screen.getAllByRole('button', { name: '조건 지우기' })).toHaveLength(1) // 필터 줄의 비활성 버튼뿐
  })
})
