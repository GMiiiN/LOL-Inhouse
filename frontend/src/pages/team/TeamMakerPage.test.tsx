// 팀 짜기 페이지 테스트: 클릭 순서가 서버로 가는지, 오류 문구, 수동 교체·되돌리기·진영 바꾸기, 코인 결과 지움, 복사
import { QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { createQueryClient } from '../../queryClient'
import { jsonResponse, mockFetch, problemResponse } from '../../test/mockFetch'
import type { MatrixCell, SlotView, TeamMakerResponse, TierListResponse } from '../../api/tierTypes'
import { POSITIONS, type Position } from '../../api/types'
import { TeamMakerPage } from './TeamMakerPage'
import { TEAM_MAKER_STORAGE_KEY } from './useTeamMakerState'

const NAMES = ['가나', '다라', '마바', '사아', '자차', '카타', '파하', '거너', '더러', '모보']
const LABEL: Record<Position, string> = { TOP: '탑', JUNGLE: '정글', MID: '미드', ADC: '원딜', SUPPORT: '서포터' }
const lanes = () =>
  Object.fromEntries(POSITIONS.map((p) => [p, { tier: 3, sub: 'MID', score: 44, trend: null }])) as TierListResponse['players'][number]['lanes']
const tierList: TierListResponse = {
  scoreTable: {
    base: { '1': 60, '2': 52, '3': 44, '4': 36, '5': 28, '6': 20 },
    sub: { HIGH: 3, MID: 0, LOW: -3 },
    lane: { TOP: 0, JUNGLE: 0, MID: 0, ADC: -4, SUPPORT: -3 },
    isDefault: true,
  },
  players: NAMES.map((displayName, i) => ({ id: i + 1, displayName, lanes: lanes(), flagged: i === 0 })),
}
const slot = (playerId: number, position: Position, score: number): SlotView => ({ playerId, position, tier: 3, sub: 'MID', score })
const cell = (score: number): MatrixCell => ({ score, tier: 3, sub: 'MID', hasTier: true })
const response: TeamMakerResponse = {
  options: [
    {
      blue: POSITIONS.map((p, i) => slot(i + 1, p, 40 + i)),
      red: POSITIONS.map((p, i) => slot(i + 6, p, 40)),
      blueSum: 210,
      redSum: 200,
      diff: 10,
      gap: 4,
      cost: 34,
      total: 410,
    },
    {
      blue: POSITIONS.map((p, i) => slot(i + 6, p, 40)),
      red: POSITIONS.map((p, i) => slot(i + 1, p, 40)),
      blueSum: 200,
      redSum: 200,
      diff: 0,
      gap: 0,
      cost: 0,
      total: 400,
    },
  ],
  scoreMatrix: Object.fromEntries(
    NAMES.map((_, i) => [String(i + 1), Object.fromEntries(POSITIONS.map((p) => [p, cell(50 + i)]))]),
  ) as TeamMakerResponse['scoreMatrix'],
}

function setup(routes: Parameters<typeof mockFetch>[0] = {}, random = () => 0.9) {
  const mocked = mockFetch({ 'GET /api/tier-list': () => jsonResponse(200, tierList), ...routes })
  const client = createQueryClient()
  client.setDefaultOptions({ queries: { ...client.getDefaultOptions().queries, retry: false } })
  render(
    <QueryClientProvider client={client}>
      <TeamMakerPage random={random} coinDelayMs={0} />
    </QueryClientProvider>,
  )
  return mocked
}

// 참가자 카드는 화면에서 가장 먼저 나온다(결과의 같은 이름 버튼보다 앞)
async function pickPlayers(order: string[]) {
  for (const name of order) {
    const buttons = await screen.findAllByRole('button', { name: new RegExp(`^${name}`) })
    await userEvent.click(buttons[0])
  }
}

const postBody = (calls: { method: string; body: string | null }[]) => JSON.parse(calls.find((c) => c.method === 'POST')!.body!)

vi.setConfig({ testTimeout: 20000 }) // 클릭이 많아 병렬 실행 부하에서 느리다

describe('TeamMakerPage 입력', () => {
  it('참가자를 클릭한 순서 그대로 서버에 보내고 10명 전에는 버튼이 막힌다', async () => {
    const { calls } = setup({ 'POST /api/team-maker/auto': () => jsonResponse(200, response) })
    const order = [...NAMES].reverse() // 이름순과 다른 클릭 순서

    await pickPlayers(order.slice(0, 9))
    expect(screen.getByText('9/10')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '팀 짜기' })).toBeDisabled()

    await pickPlayers(order.slice(9))
    await userEvent.click(screen.getByRole('button', { name: '팀 짜기' }))

    await screen.findByText('팀 방안 2개')
    expect(postBody(calls)).toEqual({ participantIds: [10, 9, 8, 7, 6, 5, 4, 3, 2, 1], together: [] })
    expect(JSON.parse(localStorage.getItem(TEAM_MAKER_STORAGE_KEY)!).chosen).toEqual([10, 9, 8, 7, 6, 5, 4, 3, 2, 1])
  })

  it('같은 팀 조건: 같은 사람은 문구로 막고, 요청에 실리고, 참가자에서 빠지면 쌍이 사라진다', async () => {
    const { calls } = setup({ 'POST /api/team-maker/auto': () => jsonResponse(200, response) })
    await pickPlayers(NAMES)

    await userEvent.click(screen.getByRole('button', { name: '추가' }))
    expect(screen.getByRole('alert')).toHaveTextContent('서로 다른 두 사람')
    await userEvent.selectOptions(screen.getByLabelText('첫 번째 사람'), '가나')
    await userEvent.selectOptions(screen.getByLabelText('두 번째 사람'), '다라')
    await userEvent.click(screen.getByRole('button', { name: '추가' }))
    expect(screen.getByText('가나 + 다라')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: '팀 짜기' }))
    await screen.findByText('팀 방안 2개')
    expect(postBody(calls).together).toEqual([[1, 2]])

    await pickPlayers(['다라']) // 참가자에서 빼기
    expect(screen.queryByText('가나 + 다라')).not.toBeInTheDocument()
  })

  it('수동 모드: 라인마다 2명을 골라야 하고 옵션 문구는 이름 · N티어 (점수점)', async () => {
    const { calls } = setup({ 'POST /api/team-maker/manual': () => jsonResponse(200, response) })
    await userEvent.click(await screen.findByRole('tab', { name: '라인 직접 지정' }))

    expect(screen.getAllByRole('option', { name: '가나 · 3티어 (44점)' })).toHaveLength(10)
    expect(screen.getByRole('button', { name: '팀 짜기' })).toBeDisabled()
    for (const [i, position] of POSITIONS.entries()) {
      await userEvent.selectOptions(screen.getByLabelText(`${LABEL[position]} 1번째`), String(i * 2 + 1))
      await userEvent.selectOptions(screen.getByLabelText(`${LABEL[position]} 2번째`), String(i * 2 + 2))
    }
    await userEvent.click(screen.getByRole('button', { name: '팀 짜기' }))

    await screen.findByText('팀 방안 2개')
    expect(postBody(calls).picks).toEqual({ TOP: [1, 2], JUNGLE: [3, 4], MID: [5, 6], ADC: [7, 8], SUPPORT: [9, 10] })
  })
})

describe('TeamMakerPage 오류', () => {
  it.each([
    [503, 'TEAM_MAKER_BUSY', '다른 팀 짜기 계산이 진행 중'],
    [429, 'RATE_LIMITED', '요청이 너무 많아요'],
    [422, 'TEAM_NO_VALID_SPLIT', '가능한 조합이 없어요'],
  ])('%i %s 오류를 한국어 문구로 보여 준다', async (status, code, text) => {
    setup({ 'POST /api/team-maker/auto': () => problemResponse(status, code) })
    await pickPlayers(NAMES)

    await userEvent.click(screen.getByRole('button', { name: '팀 짜기' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(text)
  })
})

describe('TeamMakerPage 결과', () => {
  async function showResults(random = () => 0.9) {
    setup({ 'POST /api/team-maker/auto': () => jsonResponse(200, response) }, random)
    await pickPlayers(NAMES)
    await userEvent.click(screen.getByRole('button', { name: '팀 짜기' }))
    await screen.findByText('팀 방안 2개')
    return screen.getByRole('region', { name: '방안 1 자세히' })
  }

  it('첫 방안을 선택해 보여 주고 점수 차가 크면 경고 판정을 낸다', async () => {
    const detail = await showResults()

    expect(screen.getByRole('button', { name: '방안 1' })).toHaveAttribute('aria-pressed', 'true')
    expect(within(detail).getByText('차이가 좀 있어요. 다른 방안이나 참가자 구성을 바꿔 보세요.')).toBeInTheDocument()
    expect(within(detail).getByText('동점')).toBeInTheDocument() // 탑 40:40
    expect(within(detail).getByText('◀ 1')).toBeInTheDocument() // 정글 41:40
  })

  it('두 칸을 차례로 누르면 자리가 바뀌고 수정됨이 뜨며, 처음 방안으로 되돌린다', async () => {
    const detail = await showResults()

    // 블루 탑(가나) -> 블루 정글(다라): 서버 점수표로 새 라인 점수를 다시 구한다
    await userEvent.click(within(detail).getByRole('button', { name: /^가나/ }))
    await userEvent.click(within(detail).getByRole('button', { name: /^다라/ }))
    expect(screen.getByText('수정됨')).toBeInTheDocument()
    expect(within(detail).getByText('230', { selector: 'b' })).toBeInTheDocument() // 51+50+42+43+44

    await userEvent.click(screen.getByRole('button', { name: '처음 방안으로' }))
    expect(screen.queryByText('수정됨')).not.toBeInTheDocument()
    expect(within(detail).getByText('210', { selector: 'b' })).toBeInTheDocument()
  })

  it('같은 칸을 다시 누르면 선택만 풀린다', async () => {
    const detail = await showResults()
    const first = within(detail).getByRole('button', { name: /^가나/ })

    await userEvent.click(first)
    expect(first).toHaveAttribute('aria-pressed', 'true')
    await userEvent.click(first)

    expect(first).toHaveAttribute('aria-pressed', 'false')
    expect(screen.queryByText('수정됨')).not.toBeInTheDocument()
  })

  it('진영 바꾸기는 블루/레드 합계를 맞바꾸고 코인 결과를 지운다', async () => {
    const detail = await showResults()

    await userEvent.click(within(detail).getByRole('button', { name: '코인토스' }))
    expect(within(detail).getByRole('status')).toHaveTextContent('A팀')

    await userEvent.click(within(detail).getByRole('button', { name: '진영 바꾸기' }))
    expect(within(detail).queryByRole('status')).not.toBeInTheDocument()
    expect(within(detail).getByRole('img', { name: '블루 200점, 레드 210점' })).toBeInTheDocument()
  })

  it('수동 교체를 하면 코인 결과를 지운다', async () => {
    const detail = await showResults()
    await userEvent.click(within(detail).getByRole('button', { name: '코인토스' }))

    await userEvent.click(within(detail).getByRole('button', { name: /^가나/ }))
    await userEvent.click(within(detail).getByRole('button', { name: /^다라/ }))

    expect(within(detail).queryByRole('status')).not.toBeInTheDocument()
  })

  it('레드가 나오면 칸을 맞바꿔 첫 묶음이 레드(B팀)에 놓인다', async () => {
    const detail = await showResults(() => 0.1)

    await userEvent.click(within(detail).getByRole('button', { name: '코인토스' }))

    expect(await within(detail).findByRole('status')).toHaveTextContent('레드가 나왔어요')
    expect(within(detail).getByRole('img', { name: '블루 200점, 레드 210점' })).toBeInTheDocument()
  })

  it('결과 복사: 안 되면 읽기 전용 상자를, 되면 A팀 · 블루 글을 클립보드에 쓴다', async () => {
    const detail = await showResults()
    const writeText = vi.fn().mockRejectedValueOnce(new Error('denied')).mockResolvedValueOnce(undefined)
    Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true })

    fireEvent.click(within(detail).getByRole('button', { name: '결과 복사' }))
    const box = await screen.findByLabelText('복사할 결과')
    expect((box as HTMLTextAreaElement).value).toContain('A팀 · 블루 (210점)')
    expect(box).toHaveAttribute('readonly')

    fireEvent.click(within(detail).getByRole('button', { name: '결과 복사' }))
    await waitFor(() => expect(screen.queryByLabelText('복사할 결과')).not.toBeInTheDocument())
    expect(writeText).toHaveBeenLastCalledWith(expect.stringContaining('B팀 · 레드 (200점)'))
  })

  it('칸을 선택한 채 코인 결과로 진영이 바뀌면 선택이 풀려, 다음 클릭이 엉뚱한 사람과 교체되지 않는다', async () => {
    const detail = await showResults(() => 0.1)
    const first = within(detail).getByRole('button', { name: /^가나/ })

    await userEvent.click(first) // 블루 탑 선택
    await userEvent.click(within(detail).getByRole('button', { name: '코인토스' })) // 레드 -> 칸 맞바꿈
    expect(within(detail).getByRole('button', { name: /^카타/ })).toHaveAttribute('aria-pressed', 'false')

    await userEvent.click(within(detail).getByRole('button', { name: /^마바/ }))
    expect(screen.queryByText('수정됨')).not.toBeInTheDocument() // 교체가 아니라 새 선택
  })

  it('복사 상자가 열린 뒤 결과가 바뀌면 옛 글이 남지 않고 상자가 닫힌다', async () => {
    const detail = await showResults()
    Object.defineProperty(navigator, 'clipboard', { value: { writeText: vi.fn().mockRejectedValue(new Error('x')) }, configurable: true })
    fireEvent.click(within(detail).getByRole('button', { name: '결과 복사' }))
    await screen.findByLabelText('복사할 결과')

    await userEvent.click(within(detail).getByRole('button', { name: '진영 바꾸기' }))

    expect(screen.queryByLabelText('복사할 결과')).not.toBeInTheDocument()
  })

  it('다른 방안을 고르면 그 방안 상세로 바뀐다', async () => {
    await showResults()

    await userEvent.click(screen.getByRole('button', { name: '방안 2' }))

    expect(screen.getByRole('region', { name: '방안 2 자세히' })).toBeInTheDocument()
    expect(screen.getByText('점수도 라인 맞대결도 고르게 맞춰졌어요.')).toBeInTheDocument()
  })
})

describe('TeamMakerPage 라인 고정·요주 인물', () => {
  const fixSelect = (id: number) => document.getElementById(`fix-${id}`) as HTMLSelectElement

  it('참가자가 없으면 라인 고정 상자가 없고, 고른 사람만 가나다순으로 나온다', async () => {
    setup()
    await pickPlayers(['다라'])
    expect(screen.getByText('(선택)')).toBeInTheDocument()
    expect(document.querySelectorAll('select[id^="fix-"]')).toHaveLength(1)

    await pickPlayers(['다라'])
    expect(screen.queryByText('(선택)')).not.toBeInTheDocument()
  })

  it('고정한 라인은 문자열 id 키 맵으로 현재 참가자 것만 보내고, 고정 칸에만 고정 표시가 붙는다', async () => {
    const { calls } = setup({ 'POST /api/team-maker/auto': () => jsonResponse(200, response) })
    await pickPlayers(NAMES)
    await userEvent.selectOptions(fixSelect(1), 'TOP')
    await userEvent.selectOptions(fixSelect(2), 'MID')
    expect(fixSelect(1).closest('details')).toHaveAttribute('open')

    await userEvent.click(screen.getByRole('button', { name: '팀 짜기' }))
    const detail = await screen.findByRole('region', { name: '방안 1 자세히' })

    expect(postBody(calls).fixedLanes).toEqual({ '1': 'TOP', '2': 'MID' })
    expect(within(detail).getAllByText('고정')).toHaveLength(1) // 가나(탑)만. 다라는 정글 칸이라 표시 없음

    // 가나가 정글로 옮겨 가면 고정 라인이 아니므로 표시가 사라지고, 다라가 탑으로 가도 표시가 없다
    await userEvent.click(within(detail).getByRole('button', { name: /^가나/ }))
    await userEvent.click(within(detail).getByRole('button', { name: /^다라/ }))
    expect(within(detail).queryByText('고정')).not.toBeInTheDocument()
  })

  it('고정이 없으면 fixedLanes를 보내지 않는다', async () => {
    const { calls } = setup({ 'POST /api/team-maker/auto': () => jsonResponse(200, response) })
    await pickPlayers(NAMES)
    await userEvent.click(screen.getByRole('button', { name: '팀 짜기' }))
    await screen.findByText('팀 방안 2개')

    expect(postBody(calls)).not.toHaveProperty('fixedLanes')
  })

  it('고정을 바꾸면 결과와 오류가 사라진다', async () => {
    setup({ 'POST /api/team-maker/auto': () => jsonResponse(200, response) })
    await pickPlayers(NAMES)
    await userEvent.click(screen.getByRole('button', { name: '팀 짜기' }))
    await screen.findByText('팀 방안 2개')

    await userEvent.selectOptions(fixSelect(1), 'TOP')

    expect(screen.queryByText('팀 방안 2개')).not.toBeInTheDocument()
  })

  it('고정 때문에 막히면 전용 문구를 보여 준다', async () => {
    setup({ 'POST /api/team-maker/auto': () => problemResponse(422, 'TEAM_FIXED_LANE_BLOCKED') })
    await pickPlayers(NAMES)
    await userEvent.selectOptions(fixSelect(1), 'TOP')

    await userEvent.click(screen.getByRole('button', { name: '팀 짜기' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('라인 고정 때문에 가능한 조합이 없어요')
  })

  it('요주 인물에게만 벌 아이콘이 붙는다', async () => {
    setup()
    await pickPlayers(['가나'])

    expect(screen.getAllByRole('img', { name: '요주 인물' })).toHaveLength(1)
  })
})
