// 선수 관리 화면 테스트: 칩 바로 수정, 편집기 검증·저장·409 문구, 삭제 두 번 확인, 미등록 목록, 캐시 무효화
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { PLAYERS_KEY, PLAYER_PROFILES_KEY, PUBLIC_KEY, TIER_LIST_KEY } from '../../../api/queryKeys'
import { jsonResponse, mockFetch, noContent, problemResponse, type MockRequest } from '../../../test/mockFetch'
import { renderApp } from '../../../test/renderApp'
import { DEFAULT_SCORE_TABLE, lane, profile, tierPlayer } from '../../../test/tierListFixtures'

const hong = profile({
  id: 1,
  displayName: '홍길동',
  mainPosition: 'MID',
  aliases: [{ id: 10, nickname: '길동이' }],
  lanes: { TOP: null, JUNGLE: null, MID: { tier: 2, sub: 'HIGH' }, ADC: null, SUPPORT: null },
})
const kim = profile({ id: 2, displayName: '김철수' })
const stranger = profile({
  id: 3,
  displayName: '낯선닉',
  realName: null,
  registered: false,
  aliases: [{ id: 30, nickname: '낯선닉' }],
})

const tierList = {
  scoreTable: DEFAULT_SCORE_TABLE,
  players: [tierPlayer(1, '홍길동', { MID: lane(2, 'HIGH', 55) }), tierPlayer(2, '김철수', {})],
}

const baseRoutes = {
  'GET /api/admin/me': () => jsonResponse(200, { username: 'admin' }),
  'GET /api/tier-list': () => jsonResponse(200, tierList),
  'GET /api/admin/player-profiles': () => jsonResponse(200, [hong, kim, stranger]),
  'GET /api/admin/tier-recommendations': () => jsonResponse(200, []),
}

async function waitForCall(find: () => MockRequest | undefined): Promise<MockRequest> {
  return waitFor(() => {
    const call = find()
    if (!call) throw new Error('아직 호출되지 않음')
    return call
  })
}

describe('PlayerTierAdminPage', () => {
  it('편집 모드 없이 처음부터 칩이 수정 버튼이다', async () => {
    mockFetch(baseRoutes)
    renderApp('/admin/players')

    expect(await screen.findByRole('button', { name: /홍길동 수정/ })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '편집 모드' })).not.toBeInTheDocument()
  })

  it('사람 추가: 이름·닉네임·라인을 검증하고 올바르면 POST로 보낸 뒤 목록을 새로 불러온다', async () => {
    const user = userEvent.setup()
    const { calls } = mockFetch({
      ...baseRoutes,
      'POST /api/admin/player-profiles': () => jsonResponse(201, kim),
    })
    const { client } = renderApp('/admin/players')
    const invalidated: unknown[] = []
    const original = client.invalidateQueries.bind(client)
    client.invalidateQueries = ((filters) => {
      invalidated.push((filters as { queryKey: unknown }).queryKey)
      return original(filters)
    }) as typeof client.invalidateQueries

    await user.click(await screen.findByRole('button', { name: '사람 추가' }))
    await user.click(screen.getByRole('button', { name: '저장' }))

    expect(screen.getByText('이름을 입력해 주세요.')).toBeInTheDocument()
    expect(screen.getByText('닉네임을 하나 이상 입력해 주세요.')).toBeInTheDocument()
    expect(screen.getByText('최소 한 라인은 티어를 골라 주세요.')).toBeInTheDocument()
    expect(calls.some((c) => c.method === 'POST')).toBe(false)

    await user.type(screen.getByLabelText('이름 (실명)'), '박민수')
    await user.type(screen.getByLabelText('게임 닉네임 (쉼표로 구분)'), '민수, 민수부캐')
    await user.selectOptions(screen.getByLabelText('탑 티어'), '3')
    await user.selectOptions(screen.getByLabelText('탑 티어 안 우열'), 'LOW')
    await user.selectOptions(screen.getByLabelText('주력 포지션 (선택)'), 'TOP')
    await user.click(screen.getByLabelText('요주 인물로 표시'))
    await user.click(screen.getByRole('button', { name: '저장' }))

    const post = await waitForCall(() => calls.find((c) => c.method === 'POST'))
    expect(JSON.parse(post.body ?? '')).toEqual({
      realName: '박민수',
      mainPosition: 'TOP',
      lanes: { TOP: { tier: 3, sub: 'LOW' }, JUNGLE: null, MID: null, ADC: null, SUPPORT: null },
      nicknames: ['민수', '민수부캐'],
      flagged: true,
    })
    // 프로필·티어리스트뿐 아니라 M4 등록 화면의 선수 목록(PLAYERS_KEY)도 새로 받는다
    await waitFor(() => {
      expect(invalidated).toContainEqual(PLAYER_PROFILES_KEY)
      expect(invalidated).toContainEqual(TIER_LIST_KEY)
      expect(invalidated).toContainEqual(PLAYERS_KEY)
      expect(invalidated).toContainEqual(PUBLIC_KEY)
    })
    await waitFor(() => expect(screen.queryByRole('region', { name: '선수 편집기' })).not.toBeInTheDocument())
  })

  it('같은 이름이 이미 있으면 서버를 부르지 않고 막는다', async () => {
    const user = userEvent.setup()
    const { calls } = mockFetch(baseRoutes)
    renderApp('/admin/players')

    await user.click(await screen.findByRole('button', { name: '사람 추가' }))
    await user.type(screen.getByLabelText('이름 (실명)'), '김철수')
    await user.type(screen.getByLabelText('게임 닉네임 (쉼표로 구분)'), 'x')
    await user.selectOptions(screen.getByLabelText('탑 티어'), '1')
    await user.click(screen.getByRole('button', { name: '저장' }))

    expect(screen.getByText('같은 이름이 이미 있어요.')).toBeInTheDocument()
    expect(calls.some((c) => c.method === 'POST')).toBe(false)
  })

  it('수정: 칩을 눌러 편집기를 열고, 라인을 안 함으로 바꾸면 null로 PUT한다', async () => {
    const user = userEvent.setup()
    const { calls } = mockFetch({
      ...baseRoutes,
      'PUT /api/admin/player-profiles/1': () => jsonResponse(200, hong),
    })
    renderApp('/admin/players')

    await user.click(await screen.findByRole('button', { name: /홍길동 수정/ }))

    expect(await screen.findByRole('heading', { name: '홍길동 수정' })).toBeInTheDocument()
    expect(screen.getByLabelText('이름 (실명)')).toHaveValue('홍길동')
    expect(screen.getByLabelText('게임 닉네임 (쉼표로 구분)')).toHaveValue('길동이')
    expect(screen.getByLabelText('미드 티어')).toHaveValue('2')
    expect(screen.getByLabelText('미드 티어 안 우열')).toHaveValue('HIGH')
    expect(screen.getByLabelText('주력 포지션 (선택)')).toHaveValue('MID')

    await user.selectOptions(screen.getByLabelText('미드 티어'), '')
    await user.click(screen.getByRole('button', { name: '저장' }))

    const put = await waitForCall(() => calls.find((c) => c.method === 'PUT'))
    expect(JSON.parse(put.body ?? '')).toEqual({
      realName: '홍길동',
      mainPosition: 'MID',
      lanes: { TOP: null, JUNGLE: null, MID: null, ADC: null, SUPPORT: null },
      nicknames: ['길동이'],
      flagged: false,
    })
  })

  it('409 ALIAS_CONFLICT이면 충돌한 닉네임과 연결된 선수를 보여 주고 편집기를 닫지 않는다', async () => {
    const user = userEvent.setup()
    mockFetch({
      ...baseRoutes,
      'PUT /api/admin/player-profiles/1': () =>
        problemResponse(409, 'ALIAS_CONFLICT', {
          conflicts: [{ nickname: '낯선닉', playerId: 3, playerDisplayName: '낯선닉' }],
        }),
    })
    renderApp('/admin/players')

    await user.click(await screen.findByRole('button', { name: /홍길동 수정/ }))
    await user.type(await screen.findByLabelText('게임 닉네임 (쉼표로 구분)'), ', 낯선닉')
    await user.click(screen.getByRole('button', { name: '저장' }))

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('이미 다른 선수에게 연결된 닉네임입니다')
    expect(alert).toHaveTextContent("'낯선닉' - 이미 낯선닉 선수에게 연결되어 있습니다.")
    expect(screen.getByRole('region', { name: '선수 편집기' })).toBeInTheDocument()
  })

  it('409 DUPLICATE_REAL_NAME(서버 검사)도 문구로 보여 준다', async () => {
    const user = userEvent.setup()
    mockFetch({
      ...baseRoutes,
      'PUT /api/admin/player-profiles/1': () => problemResponse(409, 'DUPLICATE_REAL_NAME'),
    })
    renderApp('/admin/players')

    await user.click(await screen.findByRole('button', { name: /홍길동 수정/ }))
    await screen.findByRole('heading', { name: '홍길동 수정' })
    await user.click(screen.getByRole('button', { name: '저장' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('같은 이름이 이미 있어요.')
  })

  it('편집기의 삭제는 두 번 눌러야 DELETE하고, 기록이 있으면 409 문구를 보여 준다', async () => {
    const user = userEvent.setup()
    const { calls } = mockFetch({
      ...baseRoutes,
      'DELETE /api/admin/player-profiles/1': () => problemResponse(409, 'PLAYER_HAS_RECORDS'),
    })
    renderApp('/admin/players')

    await user.click(await screen.findByRole('button', { name: /홍길동 수정/ }))
    await screen.findByRole('heading', { name: '홍길동 수정' })

    await user.click(screen.getByRole('button', { name: '삭제' }))
    expect(calls.some((c) => c.method === 'DELETE')).toBe(false)
    await user.click(screen.getByRole('button', { name: '정말 삭제' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('세트 기록이 있는 선수는 삭제할 수 없습니다.')
    expect(calls.filter((c) => c.method === 'DELETE')).toHaveLength(1)
  })

  it('사람별 표에서 삭제하면 204 뒤 목록을 새로 불러온다', async () => {
    const user = userEvent.setup()
    const { calls } = mockFetch({
      ...baseRoutes,
      'DELETE /api/admin/player-profiles/2': () => noContent(),
    })
    renderApp('/admin/players')

    await user.click(await screen.findByRole('button', { name: '사람별' }))
    await user.click(await screen.findByRole('button', { name: '김철수 삭제' }))
    await user.click(screen.getByRole('button', { name: '김철수 정말 삭제' }))

    await waitForCall(() => calls.find((c) => c.method === 'DELETE'))
    await waitFor(() => expect(calls.filter((c) => c.path === '/api/tier-list').length).toBeGreaterThan(1))
  })

  it('미등록 선수 목록에서 실명·라인을 넣으면 PUT으로 등록한다', async () => {
    const user = userEvent.setup()
    const { calls } = mockFetch({
      ...baseRoutes,
      'PUT /api/admin/player-profiles/3': () => jsonResponse(200, { ...stranger, realName: '이영희', registered: true }),
    })
    renderApp('/admin/players')

    const summary = await screen.findByText('미등록 선수 1명')
    await user.click(summary)
    await user.click(within(summary.closest('details') as HTMLElement).getByRole('button', { name: '낯선닉' }))

    expect(await screen.findByRole('heading', { name: '미등록 선수 낯선닉 등록' })).toBeInTheDocument()
    expect(screen.getByLabelText('게임 닉네임 (쉼표로 구분)')).toHaveValue('낯선닉')
    await user.type(screen.getByLabelText('이름 (실명)'), '이영희')
    await user.selectOptions(screen.getByLabelText('정글 티어'), '4')
    await user.click(screen.getByRole('button', { name: '저장' }))

    const put = await waitForCall(() => calls.find((c) => c.method === 'PUT'))
    expect(JSON.parse(put.body ?? '')).toMatchObject({
      realName: '이영희',
      lanes: { JUNGLE: { tier: 4, sub: 'MID' } },
      nicknames: ['낯선닉'],
    })
  })

  it('미등록 선수가 없으면 미등록 목록을 그리지 않는다', async () => {
    mockFetch({ ...baseRoutes, 'GET /api/admin/player-profiles': () => jsonResponse(200, [hong]) })
    renderApp('/admin/players')

    await screen.findByText('홍길동')
    expect(screen.queryByText(/미등록 선수 \d+명/)).not.toBeInTheDocument()
  })

  describe('미등록 선수 합치기', () => {
    afterEach(() => vi.restoreAllMocks())

    async function openMerge(user: ReturnType<typeof userEvent.setup>) {
      const summary = await screen.findByText('미등록 선수 1명')
      await user.click(summary)
      await user.click(screen.getByRole('button', { name: '기존 선수와 합치기' }))
      await user.selectOptions(screen.getByLabelText('합칠 선수'), '1')
    }

    it('확인을 취소하면 호출하지 않는다', async () => {
      const user = userEvent.setup()
      const confirm = vi.spyOn(window, 'confirm').mockReturnValue(false)
      const { calls } = mockFetch(baseRoutes)
      renderApp('/admin/players')

      await openMerge(user)
      await user.click(screen.getByRole('button', { name: '합치기' }))

      expect(confirm).toHaveBeenCalledWith('‘낯선닉’의 기록을 ‘홍길동’에게 합칠까요? 되돌릴 수 없어요.')
      expect(calls.some((c) => c.method === 'POST')).toBe(false)
    })

    it('확인하면 POST하고 목록·티어리스트·등록 화면·공개 조회를 새로 불러온다', async () => {
      const user = userEvent.setup()
      vi.spyOn(window, 'confirm').mockReturnValue(true)
      const { calls } = mockFetch({
        ...baseRoutes,
        'POST /api/admin/players/3/merge': () => jsonResponse(200, hong),
      })
      const { client } = renderApp('/admin/players')
      const invalidated: unknown[] = []
      const original = client.invalidateQueries.bind(client)
      client.invalidateQueries = ((filters) => {
        invalidated.push((filters as { queryKey: unknown }).queryKey)
        return original(filters)
      }) as typeof client.invalidateQueries

      await openMerge(user)
      await user.click(screen.getByRole('button', { name: '합치기' }))

      const post = await waitForCall(() => calls.find((c) => c.method === 'POST'))
      expect(JSON.parse(post.body ?? '')).toEqual({ targetId: 1 })
      await waitFor(() => {
        expect(invalidated).toContainEqual(PLAYER_PROFILES_KEY)
        expect(invalidated).toContainEqual(TIER_LIST_KEY)
        expect(invalidated).toContainEqual(PLAYERS_KEY)
        expect(invalidated).toContainEqual(PUBLIC_KEY)
      })
    })

    it('409 PLAYER_MERGE_CONFLICT 문구를 보여 준다', async () => {
      const user = userEvent.setup()
      vi.spyOn(window, 'confirm').mockReturnValue(true)
      mockFetch({
        ...baseRoutes,
        'POST /api/admin/players/3/merge': () => problemResponse(409, 'PLAYER_MERGE_CONFLICT'),
      })
      renderApp('/admin/players')

      await openMerge(user)
      await user.click(screen.getByRole('button', { name: '합치기' }))

      expect(await screen.findByRole('alert')).toHaveTextContent('같은 세트에 함께 나와서 합칠 수 없어요')
    })
  })
})
