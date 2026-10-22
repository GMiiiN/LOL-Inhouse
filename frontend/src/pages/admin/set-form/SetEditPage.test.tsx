// 세트 수정 화면 테스트(가짜 fetch): 불러오기 → 검사 호출 → 저장 후 이동 / SET_MODIFIED / 삭제된 세트 안내
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { jsonResponse, mockFetch, problemResponse, type MockHandler } from '../../../test/mockFetch'
import { champions } from '../../../test/registrationFixtures'
import { makeSet } from '../../../test/recordFixtures'
import { renderApp } from '../../../test/renderApp'
import { buildSetForm, editEvaluation, FORM_UPDATED_AT, formPlayers } from '../../../test/setFormFixtures'

const WAIT = { timeout: 4000 }

afterEach(() => {
  vi.unstubAllGlobals()
})

function routes(overrides: Record<string, MockHandler> = {}) {
  return {
    'GET /api/admin/me': () => jsonResponse(200, { username: 'admin' }),
    'GET /api/admin/sets/7/form': () => jsonResponse(200, buildSetForm()),
    'GET /api/admin/champions': () => jsonResponse(200, champions),
    'GET /api/admin/players': () => jsonResponse(200, formPlayers),
    'POST /api/admin/sets/7/check': () => jsonResponse(200, editEvaluation),
    'GET /api/sets/7': () => jsonResponse(200, makeSet({ setId: 7, duelId: 11, duelSeq: 2, setNo: 2, sessionId: 5 })),
    ...overrides,
  }
}

const lastBody = (calls: { method: string; path: string; body: string | null }[], method: string, path: string) =>
  JSON.parse([...calls].reverse().find((c) => c.method === method && c.path === path)?.body ?? 'null')

describe('SetEditPage', () => {
  it('폼을 불러와 머리·경기 정보·원본 보기·제한 챔피언 안내를 그리고 첫 검사는 다시 하지 않는다', async () => {
    const { calls } = mockFetch(routes())
    renderApp('/admin/sets/7/edit')

    expect(await screen.findByText('대결 2 · 2세트 수정')).toBeInTheDocument()
    expect(screen.getByText(/내전 묶음 2026-10-20/)).toBeInTheDocument()
    expect(await screen.findByRole('button', { name: '원본 보기' })).toBeInTheDocument()
    expect(screen.getByLabelText('결과창 날짜')).toHaveValue('2026-10-12')
    expect(screen.getByLabelText('경기 시간(mm:ss)')).toHaveValue('25:42')
    expect(screen.getByText('피어리스 제한 챔피언 1명')).toBeInTheDocument()
    expect(screen.getByLabelText('위쪽 팀 결과창 1행 선택된 선수')).toHaveTextContent('선수1')
    expect(screen.getByRole('button', { name: '저장' })).toBeEnabled()
    expect(calls.some((c) => c.path === '/api/admin/sets/7/check')).toBe(false)
  })

  it('이미지가 없는 세트에는 원본 보기가 없다', async () => {
    mockFetch(routes({ 'GET /api/admin/sets/7/form': () => jsonResponse(200, buildSetForm({ hasImage: false })) }))
    renderApp('/admin/sets/7/edit')
    await screen.findByText('대결 2 · 2세트 수정')
    expect(screen.queryByRole('button', { name: '원본 보기' })).toBeNull()
  })

  it('경기 시간을 비우면 모름 안내를 보이고, 검사에 빈 시간과 expectedUpdatedAt을 보낸다. 뒤 세트 충돌은 링크로 보여 준다', async () => {
    const { calls } = mockFetch(
      routes({
        'POST /api/admin/sets/7/check': () =>
          jsonResponse(200, {
            ...editEvaluation,
            fearlessConflicts: [{ setId: 9, duelId: 11, duelSeq: 2, setNo: 3, championKey: 'Champion05' }],
          }),
      }),
    )
    renderApp('/admin/sets/7/edit')
    await userEvent.clear(await screen.findByLabelText('경기 시간(mm:ss)'))

    expect(screen.getByText(/모름.으로 저장됩니다/)).toBeInTheDocument()
    const link = await screen.findByRole('link', { name: '대결 2 · 3세트' }, WAIT)
    expect(link).toHaveAttribute('href', '/sets/9')
    expect(screen.getByLabelText('뒤 세트 피어리스 충돌')).toHaveTextContent('챔피언05')
    const body = lastBody(calls, 'POST', '/api/admin/sets/7/check')
    expect(body).toMatchObject({ duration: '', expectedUpdatedAt: FORM_UPDATED_AT, topSide: 'BLUE' })
    expect(body).not.toHaveProperty('capturerPosition')
  })

  it('잘못된 경기 시간이면 저장할 수 없다', async () => {
    mockFetch(routes())
    renderApp('/admin/sets/7/edit')
    const duration = await screen.findByLabelText('경기 시간(mm:ss)')
    await userEvent.clear(duration)
    await userEvent.type(duration, '2542')
    expect(screen.getByText(/25:42처럼 분:초/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '저장' })).toBeDisabled()
  })

  it('저장하면 PUT 후 세트 상세로 이동하고 경고를 보여 준다', async () => {
    const { calls } = mockFetch(
      routes({
        'PUT /api/admin/sets/7': () =>
          jsonResponse(200, {
            setId: 7,
            duelId: 11,
            setNo: 2,
            topTeam: 'TEAM1',
            warnings: ['진영 경고 하나'],
            fearlessConflicts: [{ setId: 9, duelId: 11, duelSeq: 2, setNo: 3, championKey: 'Champion05' }],
            createdPlayers: [{ playerId: 55, nickname: '뉴페이스' }],
            addedAliases: [],
          }),
      }),
    )
    const { router } = renderApp('/admin/sets/7/edit')

    await userEvent.click(await screen.findByRole('button', { name: '저장' }))

    await waitFor(() => expect(router.state.location.pathname).toBe('/sets/7'))
    const notice = await screen.findByLabelText('수정 결과')
    expect(notice).toHaveTextContent('진영 경고 하나')
    expect(notice).toHaveTextContent('대결 2 · 3세트')
    const body = lastBody(calls, 'PUT', '/api/admin/sets/7')
    expect(body.expectedUpdatedAt).toBe(FORM_UPDATED_AT)
    expect(body.top.rows).toHaveLength(5)
    expect(calls.filter((c) => c.method === 'GET' && c.path === '/api/admin/players').length).toBeGreaterThanOrEqual(1)
  })

  it('SET_MODIFIED면 안내와 새로 불러오기 버튼을 보이고, 누르면 폼을 다시 받는다', async () => {
    let formCalls = 0
    const { calls } = mockFetch(
      routes({
        'GET /api/admin/sets/7/form': () => {
          formCalls += 1
          return jsonResponse(200, buildSetForm({ updatedAt: `2026-10-21T00:00:0${formCalls}Z`, hasImage: false }))
        },
        'PUT /api/admin/sets/7': () => problemResponse(409, 'SET_MODIFIED'),
      }),
    )
    renderApp('/admin/sets/7/edit')

    await userEvent.click(await screen.findByRole('button', { name: '저장' }))
    expect(await screen.findByText('다른 곳에서 먼저 수정·삭제됐어요. 새로 불러와 주세요')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: '새로 불러오기' }))

    await waitFor(() => expect(formCalls).toBe(2))
    await waitFor(() => expect(screen.queryByText(/먼저 수정·삭제됐어요/)).toBeNull())
    await userEvent.click(screen.getByRole('button', { name: '저장' }))
    await waitFor(() => expect(calls.filter((c) => c.method === 'PUT')).toHaveLength(2))
    expect(lastBody(calls, 'PUT', '/api/admin/sets/7').expectedUpdatedAt).toBe('2026-10-21T00:00:02Z')
  })

  it('422 SET_RULES_VIOLATED는 오류 목록으로 보여 준다', async () => {
    mockFetch(
      routes({
        'PUT /api/admin/sets/7': () => problemResponse(422, 'SET_RULES_VIOLATED', { violations: ['앞 세트와 챔피언이 겹칩니다'], warnings: [] }),
      }),
    )
    renderApp('/admin/sets/7/edit')
    await userEvent.click(await screen.findByRole('button', { name: '저장' }))
    expect(await screen.findByLabelText('저장을 막는 오류')).toHaveTextContent('앞 세트와 챔피언이 겹칩니다')
  })

  it('삭제된 세트는 수정 폼 대신 먼저 복구하라고 안내한다', async () => {
    mockFetch(routes({ 'GET /api/admin/sets/7/form': () => jsonResponse(200, buildSetForm({ deleted: true })) }))
    renderApp('/admin/sets/7/edit')

    expect(await screen.findByText(/먼저 복구하세요/)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '기록 관리로' })).toHaveAttribute('href', '/admin/history')
    expect(screen.queryByRole('button', { name: '저장' })).toBeNull()
    expect(screen.queryByLabelText('결과창 날짜')).toBeNull()
  })

  it('폼을 못 불러오면 오류와 다시 불러오기를 보여 준다', async () => {
    mockFetch(routes({ 'GET /api/admin/sets/7/form': () => problemResponse(404, 'SET_NOT_FOUND') }))
    renderApp('/admin/sets/7/edit')
    expect(await screen.findByText('세트를 찾을 수 없어요. 삭제되었거나 없는 세트예요.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '다시 불러오기' })).toBeInTheDocument()
  })
})
