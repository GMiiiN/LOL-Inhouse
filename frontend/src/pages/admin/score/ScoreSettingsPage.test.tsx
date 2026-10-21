// 점수 설정 화면 테스트: 디바운스 PUT, 한 번에 하나·최신값 합치기, 초기화도 같은 큐, 저장 상태 표시
import { cleanup, fireEvent, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it } from 'vitest'
import { jsonResponse, mockFetch, problemResponse, type MockRequest } from '../../../test/mockFetch'
import { renderApp } from '../../../test/renderApp'
import { DEFAULT_SCORE_TABLE } from '../../../test/tierListFixtures'
import { resetScoreSaverForTests } from './useScoreSaver'

const custom = { ...DEFAULT_SCORE_TABLE, base: { ...DEFAULT_SCORE_TABLE.base, '1': 70 }, isDefault: false }

// 호출을 직접 풀어 줄 때까지 붙잡아 두는 응답
function deferred() {
  let release!: (response: Response) => void
  const promise = new Promise<Response>((resolve) => (release = resolve))
  return { promise, release }
}

const baseRoutes = {
  'GET /api/admin/me': () => jsonResponse(200, { username: 'admin' }),
  'GET /api/score-table': () => jsonResponse(200, DEFAULT_SCORE_TABLE),
  'GET /api/tier-list': () => jsonResponse(200, { scoreTable: DEFAULT_SCORE_TABLE, players: [] }),
}

const puts = (calls: MockRequest[]) => calls.filter((c) => c.method === 'PUT')
const bodyOf = (call: MockRequest) => JSON.parse(call.body ?? '')

describe('ScoreSettingsPage', () => {
  beforeEach(() => resetScoreSaverForTests()) // 저장 큐는 모듈 하나라 테스트마다 비운다

  it('서버 값을 14칸과 6×3 격자(기본 점수 + 우열 보정)에 보여 준다', async () => {
    mockFetch(baseRoutes)
    renderApp('/admin/score')

    expect(await screen.findByLabelText('1티어')).toHaveValue(60)
    expect(screen.getByLabelText('6티어')).toHaveValue(20)
    expect(screen.getByLabelText('상')).toHaveValue(3)
    expect(screen.getByLabelText('원딜')).toHaveValue(-4)
    expect(screen.getByTestId('grid-1-HIGH')).toHaveTextContent('63')
    expect(screen.getByTestId('grid-6-LOW')).toHaveTextContent('17')
  })

  it('값을 바꾸면 격자가 바로 바뀌고, 400ms 뒤 14칸 전체를 PUT 한 번으로 보낸다', async () => {
    const { calls } = mockFetch({ ...baseRoutes, 'PUT /api/admin/score-table': () => jsonResponse(200, custom) })
    renderApp('/admin/score')

    const input = await screen.findByLabelText('1티어')
    fireEvent.change(input, { target: { value: '70' } })
    fireEvent.change(input, { target: { value: '71' } })

    expect(screen.getByTestId('grid-1-HIGH')).toHaveTextContent('74')
    expect(screen.getByRole('status')).toHaveTextContent('기다리는 중')
    expect(puts(calls)).toHaveLength(0)

    await waitFor(() => expect(puts(calls)).toHaveLength(1))
    expect(bodyOf(puts(calls)[0])).toEqual({
      base: { ...DEFAULT_SCORE_TABLE.base, '1': 71 },
      sub: DEFAULT_SCORE_TABLE.sub,
      lane: DEFAULT_SCORE_TABLE.lane,
    })
    await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('저장됨'))
  })

  it('올바르지 않은 입력(빈칸·소수·범위 밖)은 저장하지 않고 이유를 알린다', async () => {
    const { calls } = mockFetch({ ...baseRoutes, 'PUT /api/admin/score-table': () => jsonResponse(200, custom) })
    renderApp('/admin/score')

    const input = await screen.findByLabelText('2티어')
    fireEvent.change(input, { target: { value: '' } })

    expect(screen.getByRole('status')).toHaveTextContent('저장하지 않았어요')
    expect(input).toHaveAttribute('aria-invalid', 'true')
    expect(screen.getByTestId('grid-2-HIGH')).toHaveTextContent('—')
    await new Promise((resolve) => setTimeout(resolve, 600))
    expect(puts(calls)).toHaveLength(0)
  })

  it('저장 중에 또 바꾸면 PUT을 동시에 보내지 않고, 끝난 뒤 최신 값으로 한 번 더 보낸다', async () => {
    const first = deferred()
    let count = 0
    const { calls } = mockFetch({
      ...baseRoutes,
      'PUT /api/admin/score-table': () => (++count === 1 ? first.promise : Promise.resolve(jsonResponse(200, custom))),
    })
    renderApp('/admin/score')

    const input = await screen.findByLabelText('1티어')
    fireEvent.change(input, { target: { value: '61' } })
    await waitFor(() => expect(puts(calls)).toHaveLength(1))
    expect(screen.getByRole('status')).toHaveTextContent('저장 중')

    // 첫 PUT이 끝나기 전에 두 번 더 바꾼다 -> 둘 다 기다리다가 마지막 값 하나로 합쳐진다
    fireEvent.change(input, { target: { value: '62' } })
    await new Promise((resolve) => setTimeout(resolve, 500))
    fireEvent.change(input, { target: { value: '63' } })
    await new Promise((resolve) => setTimeout(resolve, 500))
    expect(puts(calls)).toHaveLength(1)

    first.release(jsonResponse(200, custom))
    await waitFor(() => expect(puts(calls)).toHaveLength(2))
    expect(bodyOf(puts(calls)[1]).base['1']).toBe(63)
    await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('저장됨'))
  })

  it('기본값으로는 같은 순서 큐에 들어가 진행 중인 PUT이 끝난 뒤에야 보내고, 응답으로 입력칸을 채운다', async () => {
    const firstPut = deferred()
    const order: string[] = []
    const { calls } = mockFetch({
      ...baseRoutes,
      'PUT /api/admin/score-table': () => {
        order.push('put')
        return firstPut.promise
      },
      'POST /api/admin/score-table/reset': () => {
        order.push('reset')
        return jsonResponse(200, DEFAULT_SCORE_TABLE)
      },
    })
    renderApp('/admin/score')

    const input = await screen.findByLabelText('1티어')
    fireEvent.change(input, { target: { value: '90' } })
    await waitFor(() => expect(puts(calls)).toHaveLength(1))

    fireEvent.click(screen.getByRole('button', { name: '기본값으로' }))
    expect(screen.getByRole('status')).toHaveTextContent('기본값으로 되돌리는 중')
    expect(screen.getByLabelText('1티어')).toBeDisabled()
    await new Promise((resolve) => setTimeout(resolve, 100))
    expect(order).toEqual(['put']) // reset은 아직 보내지 않았다

    firstPut.release(jsonResponse(200, custom))
    await waitFor(() => expect(order).toEqual(['put', 'reset']))
    await waitFor(() => expect(screen.getByLabelText('1티어')).toHaveValue(60))
    expect(screen.getByLabelText('1티어')).toBeEnabled()
    await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('저장됨'))
  })

  it('기본값으로를 누르면 아직 보내지 않은 입력(디바운스 대기)은 버린다', async () => {
    const { calls } = mockFetch({
      ...baseRoutes,
      'PUT /api/admin/score-table': () => jsonResponse(200, custom),
      'POST /api/admin/score-table/reset': () => jsonResponse(200, DEFAULT_SCORE_TABLE),
    })
    renderApp('/admin/score')

    fireEvent.change(await screen.findByLabelText('1티어'), { target: { value: '90' } })
    fireEvent.click(screen.getByRole('button', { name: '기본값으로' }))

    await waitFor(() => expect(calls.some((c) => c.path === '/api/admin/score-table/reset')).toBe(true))
    await new Promise((resolve) => setTimeout(resolve, 600))
    expect(puts(calls)).toHaveLength(0)
  })

  it('저장이 실패하면 오류 문구와 다시 저장 버튼을 보여 주고, 누르면 같은 값을 다시 보낸다', async () => {
    let count = 0
    const { calls } = mockFetch({
      ...baseRoutes,
      'PUT /api/admin/score-table': () =>
        ++count === 1 ? problemResponse(400, 'INVALID_SCORE_TABLE') : jsonResponse(200, custom),
    })
    renderApp('/admin/score')

    fireEvent.change(await screen.findByLabelText('1티어'), { target: { value: '70' } })

    expect(await screen.findByText(/저장하지 못했어요/)).toHaveTextContent('-999~999 사이의 정수')
    fireEvent.click(screen.getByRole('button', { name: '다시 저장' }))

    await waitFor(() => expect(puts(calls)).toHaveLength(2))
    expect(bodyOf(puts(calls)[1]).base['1']).toBe(70)
    await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('저장됨'))
  })
  it('응답을 기다리는 중에 화면을 떠났다 돌아와도 큐를 이어받아, 옛 대기값이 최신 값을 덮지 않는다', async () => {
    const first = deferred()
    let count = 0
    const { calls } = mockFetch({
      ...baseRoutes,
      'PUT /api/admin/score-table': () => (++count === 1 ? first.promise : Promise.resolve(jsonResponse(200, custom))),
    })
    renderApp('/admin/score')

    fireEvent.change(await screen.findByLabelText('1티어'), { target: { value: '61' } })
    await waitFor(() => expect(puts(calls)).toHaveLength(1)) // 61이 응답 대기 중
    fireEvent.change(screen.getByLabelText('1티어'), { target: { value: '62' } })
    cleanup() // 화면 이탈: 보내지 않은 62는 큐 대기값으로 넘어간다

    renderApp('/admin/score')
    const input = await screen.findByLabelText('1티어')
    expect(screen.getByRole('status')).toHaveTextContent('저장 중') // 진행 중인 큐를 이어받았다
    fireEvent.change(input, { target: { value: '63' } })
    await new Promise((resolve) => setTimeout(resolve, 600))
    expect(puts(calls)).toHaveLength(1) // 새 인스턴스도 동시에 보내지 않는다

    first.release(jsonResponse(200, custom))
    await waitFor(() => expect(puts(calls)).toHaveLength(2))
    await new Promise((resolve) => setTimeout(resolve, 100))
    expect(puts(calls).map((c) => bodyOf(c).base['1'])).toEqual([61, 63]) // 62가 63 뒤에 나가지 않는다
  })
})
