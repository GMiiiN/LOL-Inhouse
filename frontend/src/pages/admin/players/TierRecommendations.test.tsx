// 티어 조정 추천 카드: 표시, 적용(프로필 PUT), 넘기기(localStorage)
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it } from 'vitest'
import type { TierRecommendation } from '../../../api/tierTypes'
import { jsonResponse, mockFetch } from '../../../test/mockFetch'
import { renderApp } from '../../../test/renderApp'
import { DEFAULT_SCORE_TABLE, lane, profile, tierPlayer } from '../../../test/tierListFixtures'
import { TIER_REC_DISMISSED_KEY } from './TierRecommendations'

const hong = profile({
  id: 1,
  displayName: '홍길동',
  mainPosition: 'MID',
  aliases: [{ id: 10, nickname: '길동이' }],
  lanes: { TOP: { tier: 4, sub: 'LOW' }, JUNGLE: null, MID: { tier: 3, sub: 'MID' }, ADC: null, SUPPORT: null },
})

const rec = (overrides: Partial<TierRecommendation> = {}): TierRecommendation => ({
  playerId: 1,
  displayName: '홍길동',
  position: 'MID',
  games: 5,
  wins: 4,
  kda: 3.456,
  laneAvgKda: 2.5,
  score: 0.6,
  direction: 'UP',
  from: { tier: 3, sub: 'MID' },
  to: { tier: 3, sub: 'HIGH' },
  ...overrides,
})

function routes(recs: TierRecommendation[]) {
  return {
    'GET /api/admin/me': () => jsonResponse(200, { username: 'admin' }),
    'GET /api/tier-list': () =>
      jsonResponse(200, { scoreTable: DEFAULT_SCORE_TABLE, players: [tierPlayer(1, '홍길동', { MID: lane(3, 'MID', 44) })] }),
    'GET /api/admin/player-profiles': () => jsonResponse(200, [hong]),
    'GET /api/admin/tier-recommendations': () => jsonResponse(200, recs),
  }
}

beforeEach(() => localStorage.clear())

describe('TierRecommendations', () => {
  it('이름·라인·방향·티어 변화·근거를 카드에 보여 준다', async () => {
    mockFetch(routes([rec()]))
    renderApp('/admin/players')

    expect(await screen.findByText('▲ 상향')).toBeInTheDocument()
    expect(document.querySelector('.tier-rec-change')).toHaveTextContent('3티어 중 → 3티어 상')
    expect(screen.getByText(/5경기 4승 1패 \(80%\) · KDA 3\.46 \(라인 평균 2\.50\)/)).toBeInTheDocument()
  })

  it('추천이 없으면 안내 문구를 보여 준다', async () => {
    mockFetch(routes([]))
    renderApp('/admin/players')
    expect(await screen.findByText('지금은 추천할 사람이 없어요')).toBeInTheDocument()
  })

  it('기본 6개만 보이고 전체 보기로 모두 본다', async () => {
    const many = Array.from({ length: 8 }, (_, i) => rec({ playerId: 100 + i, displayName: `선수${i}` }))
    mockFetch(routes(many))
    renderApp('/admin/players')

    await screen.findByText('선수0')
    expect(screen.queryByText('선수7')).not.toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: '전체 보기 (8)' }))
    expect(screen.getByText('선수7')).toBeInTheDocument()
  })

  it('적용하면 해당 라인만 추천 티어로 바꿔 recordChange true로 PUT하고 추천을 다시 불러온다', async () => {
    let recs = [rec()]
    const base = routes([])
    const { calls } = mockFetch({
      ...base,
      'GET /api/admin/tier-recommendations': () => jsonResponse(200, recs),
      'PUT /api/admin/player-profiles/1': () => {
        recs = []
        return jsonResponse(200, hong)
      },
    })
    renderApp('/admin/players')

    await userEvent.click(await screen.findByRole('button', { name: '홍길동 미드 적용' }))

    await screen.findByText('지금은 추천할 사람이 없어요')
    const put = calls.find((c) => c.method === 'PUT')
    expect(JSON.parse(put?.body ?? '')).toEqual({
      realName: '홍길동',
      mainPosition: 'MID',
      lanes: { TOP: { tier: 4, sub: 'LOW' }, JUNGLE: null, MID: { tier: 3, sub: 'HIGH' }, ADC: null, SUPPORT: null },
      nicknames: ['길동이'],
      flagged: false,
      recordChange: true,
    })
  })

  it('넘기면 localStorage에 경기 수를 저장해 숨기고, 경기가 더 쌓이면 다시 보인다', async () => {
    const user = userEvent.setup()
    mockFetch(routes([rec({ games: 5 }), rec({ playerId: 2, displayName: '김철수', games: 4 })]))
    localStorage.setItem(TIER_REC_DISMISSED_KEY, JSON.stringify({ '2:MID': 3 }))
    renderApp('/admin/players')

    await user.click(await screen.findByRole('button', { name: '홍길동 미드 넘기기' }))

    await waitFor(() => expect(screen.queryByRole('button', { name: '홍길동 미드 넘기기' })).not.toBeInTheDocument())
    expect(JSON.parse(localStorage.getItem(TIER_REC_DISMISSED_KEY) ?? '{}')).toEqual({ '2:MID': 3, '1:MID': 5 })
    // 저장된 3경기보다 4경기로 늘어난 김철수는 계속 보인다
    expect(screen.getByRole('button', { name: '김철수 미드 넘기기' })).toBeInTheDocument()
  })
})

describe('TierRecommendations 안전장치', () => {
  it('추천을 받은 뒤 티어가 이미 바뀌었으면 PUT하지 않고 안내한다', async () => {
    const user = userEvent.setup()
    const { calls } = mockFetch(routes([rec({ from: { tier: 2, sub: 'LOW' }, to: { tier: 2, sub: 'MID' } })]))
    renderApp('/admin/players')

    await user.click(await screen.findByRole('button', { name: '홍길동 미드 적용' }))

    expect(await screen.findByText('티어가 이미 바뀌었어요. 추천을 새로 불러올게요.')).toBeInTheDocument()
    expect(calls.some((c) => c.method === 'PUT')).toBe(false)
  })

  it('편집기가 열려 있으면 적용 버튼이 꺼진다', async () => {
    const user = userEvent.setup()
    mockFetch(routes([rec()]))
    renderApp('/admin/players')

    await screen.findByText('▲ 상향')
    await user.click(await screen.findByRole('button', { name: /홍길동 수정/ }))

    await waitFor(() => expect(screen.getByRole('button', { name: '홍길동 미드 적용' })).toBeDisabled())
  })
})
