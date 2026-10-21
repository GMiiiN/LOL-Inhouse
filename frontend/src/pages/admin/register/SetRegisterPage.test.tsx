// 세트 등록 흐름 테스트(가짜 fetch): 시작 → 폴링 → 초안 → 후보 선택 → 검사 → 저장(201/422/409), 팀 변경 → 새 대결로 등록, 실패·재추출
import { QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import type { Evaluation, SetRegistrationRequest } from '../../../api/registrationTypes'
import { createQueryClient } from '../../../queryClient'
import { jsonResponse, mockFetch, problemResponse, type MockHandler } from '../../../test/mockFetch'
import {
  baseEvaluation,
  buildDraft,
  champions,
  registrationContext,
} from '../../../test/registrationFixtures'
import { SetRegisterPage } from './SetRegisterPage'

const WAIT = { timeout: 4000 }

function renderPage(giveUpMs?: number) {
  const client = createQueryClient()
  client.setDefaultOptions({ queries: { ...client.getDefaultOptions().queries, retry: false } })
  const router = createMemoryRouter(
    [{ path: '/admin/duels/:duelId/sets/new', element: <SetRegisterPage pollMs={20} giveUpMs={giveUpMs} /> }],
    { initialEntries: ['/admin/duels/11/sets/new'] },
  )
  render(
    <QueryClientProvider client={client}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  )
  return router
}

const succeeded = { jobId: 7, duelId: 11, status: 'SUCCEEDED', errorCode: null, attemptCount: 1, topSide: 'BLUE', capturerPosition: 'ADC', savedSetId: null }

function baseRoutes(overrides: Record<string, MockHandler> = {}) {
  let polls = 0
  return {
    'GET /api/admin/duels/11/registration-context': () => jsonResponse(200, registrationContext),
    'GET /api/admin/champions': () => jsonResponse(200, champions),
    'GET /api/admin/players': () =>
      jsonResponse(200, [
        { id: 101, displayName: '홍길동', realName: '홍길동', registered: true, mainPosition: null, aliases: [{ id: 1, nickname: '홍길동1' }] },
      ]),
    'POST /api/admin/duels/11/extractions': () => jsonResponse(202, { jobId: 7, status: 'QUEUED', reused: false }),
    'GET /api/admin/extractions/7': () => {
      polls += 1
      return jsonResponse(200, polls < 3 ? { ...succeeded, status: 'RUNNING' } : succeeded)
    },
    'GET /api/admin/extractions/7/draft': () => jsonResponse(200, buildDraft()),
    'POST /api/admin/duels/11/sets/check': () => jsonResponse(200, baseEvaluation),
    ...overrides,
  }
}

async function uploadAndReach(user: ReturnType<typeof userEvent.setup>) {
  await screen.findByText('대결 1 · 1세트로 등록')
  await user.click(screen.getByRole('radio', { name: '원딜' }))
  const file = new File([new Uint8Array(10)], 'image.png', { type: 'image/png' })
  const paste = new Event('paste', { bubbles: true, cancelable: true })
  Object.defineProperty(paste, 'clipboardData', {
    value: { items: [{ kind: 'file', type: 'image/png', getAsFile: () => file }] },
  })
  document.dispatchEvent(paste)
  await screen.findByRole('heading', { name: '경기 정보' }, WAIT)
}

const rowGroup = (label: string) => screen.getByRole('group', { name: label })
const lastBody = (calls: { method: string; path: string; body: string | null }[], path: string) =>
  JSON.parse([...calls].reverse().find((c) => c.method === 'POST' && c.path === path)?.body ?? 'null')

async function pickCandidate(user: ReturnType<typeof userEvent.setup>) {
  await user.click(within(rowGroup('위쪽 팀 결과창 2행')).getByRole('radio', { name: /홍길동/ }))
}

vi.setConfig({ testTimeout: 20000 }) // 단계가 많아 전체 병렬 실행 부하에서 5초를 넘길 때가 있다

describe('SetRegisterPage', () => {
  it('붙여넣기 → 업로드(FormData) → 폴링 → 초안 → 후보 선택 → 검사 → 저장 201 → 저장됨 화면', async () => {
    const user = userEvent.setup()
    const { calls } = mockFetch(
      baseRoutes({
        'POST /api/admin/duels/11/sets': () =>
          jsonResponse(201, { setId: 3, duelId: 11, setNo: 1, topTeam: 'TEAM1', warnings: [], createdPlayers: [], addedAliases: [] }),
      }),
    )
    renderPage()

    await uploadAndReach(user)

    const upload = calls.find((c) => c.method === 'POST' && c.path === '/api/admin/duels/11/extractions')
    expect(upload?.formData?.get('topSide')).toBe('BLUE')
    expect(upload?.formData?.get('capturerPosition')).toBe('ADC')
    expect(upload?.formData?.get('image')).toBeInstanceOf(File)
    expect(upload?.headers.has('Content-Type')).toBe(false)
    expect(calls.filter((c) => c.path === '/api/admin/extractions/7').length).toBeGreaterThanOrEqual(3)

    // 선수 미확정이라 저장 불가 → 후보를 고르면 600ms 뒤 검사하고 저장이 켜진다
    expect(screen.getByRole('button', { name: '저장' })).toBeDisabled()
    expect(screen.getByRole('alert', { name: '저장을 막는 오류' })).toHaveTextContent('선수를 골라야 합니다')
    await pickCandidate(user)
    await waitFor(() => expect(screen.getByRole('button', { name: '저장' })).toBeEnabled(), WAIT)
    await user.click(screen.getByRole('button', { name: '저장' }))

    expect(await screen.findByText('1세트 저장됨')).toBeInTheDocument()
    const body = lastBody(calls, '/api/admin/duels/11/sets') as SetRegistrationRequest
    expect(body.top.rows[1]).toMatchObject({ screenRow: 2, resolution: 'EXISTING', playerId: 101, addAlias: true })
    expect(body.top.bans).toHaveLength(5)
    expect(body.newDuel).toBeUndefined()
    expect(screen.getByRole('link', { name: '내전 묶음으로' })).toHaveAttribute('href', '/admin/sessions/5')
  })

  it('저장이 422이면 violations를 빨간 목록으로 보여 주고 입력을 유지한다', async () => {
    const user = userEvent.setup()
    mockFetch(
      baseRoutes({
        'POST /api/admin/duels/11/sets': () =>
          problemResponse(422, 'SET_RULES_VIOLATED', { violations: ['피어리스 제한 챔피언을 골랐습니다'], warnings: ['합계 불일치'] }),
      }),
    )
    renderPage()
    await uploadAndReach(user)
    await pickCandidate(user)
    await waitFor(() => expect(screen.getByRole('button', { name: '저장' })).toBeEnabled(), WAIT)

    await user.click(screen.getByRole('button', { name: '저장' }))

    expect(await screen.findByText('피어리스 제한 챔피언을 골랐습니다')).toBeInTheDocument()
    expect(screen.getByText('합계 불일치')).toBeInTheDocument()
    expect(within(rowGroup('위쪽 팀 결과창 2행')).getByRole('radio', { name: /홍길동/ })).toBeChecked()
  })

  it('409 DUPLICATE_SET이면 기존 세트 번호를 안내한다', async () => {
    const user = userEvent.setup()
    mockFetch(
      baseRoutes({
        'POST /api/admin/duels/11/sets': () => problemResponse(409, 'DUPLICATE_SET', { existingSetId: 4, existingSetNo: 2 }),
      }),
    )
    renderPage()
    await uploadAndReach(user)
    await pickCandidate(user)
    await waitFor(() => expect(screen.getByRole('button', { name: '저장' })).toBeEnabled(), WAIT)

    await user.click(screen.getByRole('button', { name: '저장' }))

    expect(await screen.findByText('이미 2세트로 등록된 같은 경기입니다.')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '기존 세트 보기' })).toHaveAttribute('href', '/sets/4')
  })

  it('409 ALIAS_CONFLICT이면 해당 닉네임 행에 충돌 설명을 표시한다', async () => {
    const user = userEvent.setup()
    mockFetch(
      baseRoutes({
        'POST /api/admin/duels/11/sets': () =>
          problemResponse(409, 'ALIAS_CONFLICT', { conflicts: [{ nickname: '홍길', playerId: 55, playerDisplayName: '다른사람' }] }),
      }),
    )
    renderPage()
    await uploadAndReach(user)
    await pickCandidate(user)
    await waitFor(() => expect(screen.getByRole('button', { name: '저장' })).toBeEnabled(), WAIT)

    await user.click(screen.getByRole('button', { name: '저장' }))

    const row = rowGroup('위쪽 팀 결과창 2행')
    expect(await within(row).findByText('이미 다른사람 선수에게 연결된 닉네임입니다.')).toBeInTheDocument()
  })

  it('409 SET_NO_CONFLICT이면 등록 현황을 다시 불러온다', async () => {
    const user = userEvent.setup()
    let contextCalls = 0
    mockFetch(
      baseRoutes({
        'GET /api/admin/duels/11/registration-context': () => {
          contextCalls += 1
          return jsonResponse(200, registrationContext)
        },
        'POST /api/admin/duels/11/sets': () => problemResponse(409, 'SET_NO_CONFLICT'),
      }),
    )
    renderPage()
    await uploadAndReach(user)
    await pickCandidate(user)
    await waitFor(() => expect(screen.getByRole('button', { name: '저장' })).toBeEnabled(), WAIT)
    const before = contextCalls

    await user.click(screen.getByRole('button', { name: '저장' }))

    expect(await screen.findByText(/세트 번호가 바뀌었습니다/)).toBeInTheDocument()
    await waitFor(() => expect(contextCalls).toBeGreaterThan(before))
  })

  it('팀 변경 경고에서 "새 대결로 등록"을 고르면 check·저장 요청에 newDuel을 담고, 새 대결에서 이어서 등록한다', async () => {
    const user = userEvent.setup()
    const reshuffle: Evaluation = {
      ...baseEvaluation,
      setNo: 2,
      detection: { method: 'OVERLAP', overlap: 2, uncertain: true },
      teamChange: { kind: 'RESHUFFLE', swaps: 0, details: ['새 선수 2명이 투입됨'] },
      warnings: ['팀이 크게 바뀌었습니다'],
    }
    const { calls } = mockFetch(
      baseRoutes({
        'POST /api/admin/duels/11/sets/check': (request) =>
          jsonResponse(200, JSON.parse(request.body ?? '{}').newDuel ? { ...baseEvaluation, setNo: 1 } : reshuffle),
        'POST /api/admin/duels/11/sets': () =>
          jsonResponse(201, { setId: 9, duelId: 12, setNo: 1, topTeam: 'TEAM1', warnings: [], createdPlayers: [], addedAliases: [] }),
      }),
    )
    const router = renderPage()
    await uploadAndReach(user)
    await pickCandidate(user)

    const notice = await screen.findByRole('status', { name: '팀 변경 감지' }, WAIT)
    expect(notice).toHaveTextContent('직전 세트와 팀 구성이 크게 달라졌습니다')
    expect(screen.getByText(/팀이 바뀌어 판별이 불확실/)).toBeInTheDocument()
    await user.click(within(notice).getByRole('button', { name: '새 대결로 등록' }))
    await user.click(screen.getByRole('radio', { name: /이어가기/ }))
    await user.click(screen.getByRole('button', { name: '새 대결로 미리 보기' }))

    await waitFor(() => {
      expect(lastBody(calls, '/api/admin/duels/11/sets/check').newDuel).toEqual({ fearlessMode: 'CONTINUE' })
    }, WAIT)
    expect(await screen.findByText(/새 대결의 1세트/)).toBeInTheDocument()
    await waitFor(() => expect(screen.getByRole('button', { name: '저장' })).toBeEnabled(), WAIT)
    await user.click(screen.getByRole('button', { name: '저장' }))

    await screen.findByText('1세트 저장됨')
    expect(lastBody(calls, '/api/admin/duels/11/sets').newDuel).toEqual({ fearlessMode: 'CONTINUE' })
    await user.click(screen.getByRole('button', { name: '다음 세트 등록' }))
    await waitFor(() => expect(router.state.location.pathname).toBe('/admin/duels/12/sets/new'))
  })

  it('맞라인 교환은 노란 안내만 보여 준다', async () => {
    const user = userEvent.setup()
    mockFetch(
      baseRoutes({
        'POST /api/admin/duels/11/sets/check': () =>
          jsonResponse(200, { ...baseEvaluation, teamChange: { kind: 'SAME_LANE_SWAP', swaps: 2, details: [] } }),
      }),
    )
    renderPage()
    await uploadAndReach(user)
    await pickCandidate(user)

    const notice = await screen.findByRole('status', { name: '팀 변경 감지' }, WAIT)
    expect(notice).toHaveTextContent('맞라인 교환 2건 감지')
  })

  it('추출이 실패하면 코드별 문구를 보이고, 다시 추출은 retry=true로 새 작업을 만든다', async () => {
    const user = userEvent.setup()
    let startCalls = 0
    const { calls } = mockFetch(
      baseRoutes({
        'GET /api/admin/extractions/7': () => jsonResponse(200, { ...succeeded, status: 'FAILED', errorCode: 'EXTRACTION_TIMEOUT' }),
        'POST /api/admin/duels/11/extractions?retry=true': () => {
          startCalls += 1
          return jsonResponse(202, { jobId: 8, status: 'QUEUED', reused: false })
        },
        'GET /api/admin/extractions/8': () => jsonResponse(200, { ...succeeded, jobId: 8, status: 'RUNNING' }),
      }),
    )
    renderPage()
    await screen.findByText('대결 1 · 1세트로 등록')
    await user.click(screen.getByRole('radio', { name: '원딜' }))
    const paste = new Event('paste', { bubbles: true, cancelable: true })
    Object.defineProperty(paste, 'clipboardData', {
      value: { items: [{ kind: 'file', type: 'image/png', getAsFile: () => new File([new Uint8Array(3)], 'a.png', { type: 'image/png' }) }] },
    })
    document.dispatchEvent(paste)

    expect(await screen.findByText(/추출이 제한 시간 안에 끝나지 않았습니다/, undefined, WAIT)).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: '다시 추출' }))

    await waitFor(() => expect(startCalls).toBe(1))
    expect(calls.some((c) => c.path === '/api/admin/extractions/8')).toBe(true)
  })

  it('오래 걸리면 폴링을 멈추고 "상태 다시 확인"과 "다시 추출"을 구분해서 보여 준다', async () => {
    const user = userEvent.setup()
    mockFetch(
      baseRoutes({
        'GET /api/admin/extractions/7': () => jsonResponse(200, { ...succeeded, status: 'RUNNING' }),
      }),
    )
    renderPage(80)
    await screen.findByText('대결 1 · 1세트로 등록')
    await user.click(screen.getByRole('radio', { name: '원딜' }))
    const paste = new Event('paste', { bubbles: true, cancelable: true })
    Object.defineProperty(paste, 'clipboardData', {
      value: { items: [{ kind: 'file', type: 'image/png', getAsFile: () => new File([new Uint8Array(3)], 'a.png', { type: 'image/png' }) }] },
    })
    document.dispatchEvent(paste)

    expect(await screen.findByRole('button', { name: '상태 다시 확인' }, WAIT)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '다시 추출' })).toBeInTheDocument()
  })

  it('재사용된 작업이면 지금 고른 진영·포지션을 초안에 적용하고 안내하며 바로 재검사한다', async () => {
    const user = userEvent.setup()
    const { calls } = mockFetch(
      baseRoutes({
        'POST /api/admin/duels/11/extractions': () => jsonResponse(200, { jobId: 7, status: 'SUCCEEDED', reused: true }),
      }),
    )
    renderPage()
    await screen.findByText('대결 1 · 1세트로 등록')
    await user.click(screen.getByRole('radio', { name: '레드' }))
    await user.click(screen.getByRole('radio', { name: '미드' })) // 초안의 옛 선택은 블루·원딜
    const paste = new Event('paste', { bubbles: true, cancelable: true })
    Object.defineProperty(paste, 'clipboardData', {
      value: { items: [{ kind: 'file', type: 'image/png', getAsFile: () => new File([new Uint8Array(3)], 'a.png', { type: 'image/png' }) }] },
    })
    document.dispatchEvent(paste)

    expect(await screen.findByText(/지금 고른 진영·포지션을 적용했습니다/, undefined, WAIT)).toBeInTheDocument()
    await waitFor(() => {
      const body = lastBody(calls, '/api/admin/duels/11/sets/check') as SetRegistrationRequest | null
      expect(body).toMatchObject({ topSide: 'RED', capturerPosition: 'MID' })
      expect(body?.top.rows[0].position).toBe('MID')
    }, WAIT)
  })

  it('새 대결을 초기화로 미리 볼 때는 피어리스 제한을 비우고, 취소하면 복원한다', async () => {
    const user = userEvent.setup()
    const reshuffle: Evaluation = { ...baseEvaluation, teamChange: { kind: 'RESHUFFLE', swaps: 0, details: [] } }
    mockFetch(
      baseRoutes({
        'GET /api/admin/duels/11/registration-context': () =>
          jsonResponse(200, { ...registrationContext, restrictedChampions: ['Champion00'] }),
        'POST /api/admin/duels/11/sets/check': () => jsonResponse(200, reshuffle),
      }),
    )
    renderPage()
    await uploadAndReach(user)
    await pickCandidate(user)
    const restrictedChip = () =>
      rowGroup('위쪽 팀 결과창 1행').querySelector('.champion-chips .chip.restricted')
    expect(restrictedChip()).toBeInTheDocument()

    const notice = await screen.findByRole('status', { name: '팀 변경 감지' }, WAIT)
    await user.click(within(notice).getByRole('button', { name: '새 대결로 등록' }))
    await user.click(screen.getByRole('button', { name: '새 대결로 미리 보기' })) // 기본값 초기화
    expect(restrictedChip()).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: '새 대결 취소' }))
    expect(restrictedChip()).toBeInTheDocument()
  })

  it('확인 단계에서는 beforeunload를 막는다', async () => {
    const user = userEvent.setup()
    mockFetch(baseRoutes())
    renderPage()
    await uploadAndReach(user)

    const event = new Event('beforeunload', { cancelable: true })
    window.dispatchEvent(event)

    expect(event.defaultPrevented).toBe(true)
  })
})
