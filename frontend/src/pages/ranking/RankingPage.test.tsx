// 랭킹 화면: 탭·정렬이 쿼리로 가는지, 순위 없는 행은 "—", 서버 순서 그대로
import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import type { Ranking } from '../../api/publicTypes'
import { championsHandler, renderPage, summary } from '../../test/statsFixtures'
import { jsonResponse, mockFetch } from '../../test/mockFetch'
import { RankingPage } from './RankingPage'

const ranking = (over: Partial<Ranking> = {}): Ranking => ({
  position: 'ALL',
  sort: 'WIN_RATE',
  minSets: 5,
  totalSets: 8,
  entries: [
    { playerId: 1, displayName: '가나', registered: true, rank: 1, summary: summary(6, 5, 30, 10, 20) },
    { playerId: 2, displayName: '다라', registered: false, rank: null, summary: summary(2, 2, 10, 0, 5) },
  ],
  ...over,
})

function setup(url = '/ranking') {
  const mocked = mockFetch({
    'GET /api/champions': championsHandler,
    'GET /api/rankings?position=ALL&sort=WIN_RATE': () => jsonResponse(200, ranking()),
    'GET /api/rankings?position=MID&sort=WIN_RATE': () => jsonResponse(200, ranking({ position: 'MID' })),
    'GET /api/rankings?position=ALL&sort=KDA': () => jsonResponse(200, ranking({ sort: 'KDA' })),
    'GET /api/rankings?position=ALL&sort=WIN_RATE&from=2026-10-01': () => jsonResponse(200, ranking()),
  })
  renderPage(<RankingPage />, url)
  return mocked
}

describe('RankingPage', () => {
  it('순위 없는 행은 "—"이고 서버가 준 순서 그대로 그린다', async () => {
    setup()
    const rows = await screen.findAllByRole('row')
    expect(within(rows[1]).getByText('가나')).toBeInTheDocument()
    expect(within(rows[1]).getByText('1')).toBeInTheDocument()
    expect(within(rows[2]).getByText('다라')).toBeInTheDocument()
    expect(within(rows[2]).getByText('—')).toBeInTheDocument()
    expect(within(rows[2]).getByText('PERFECT')).toBeInTheDocument()
    expect(within(rows[2]).getByText('미등록')).toBeInTheDocument()
    expect(screen.getByText(/5세트 미만은 순위 없이 기록만 보여요/)).toBeInTheDocument()
  })

  it('화면 이름이 랭킹이 아니라 승률이다', async () => {
    setup()
    expect(await screen.findByRole('heading', { level: 1, name: '승률' })).toBeInTheDocument()
    expect(screen.queryByText('랭킹')).not.toBeInTheDocument()
  })

  it('포지션 탭을 누르면 그 포지션으로 다시 조회한다', async () => {
    const { calls } = setup()
    await screen.findByText('가나')
    await userEvent.click(screen.getByRole('button', { name: '미드' }))
    await screen.findByRole('button', { name: '미드', pressed: true })
    expect(calls.map((c) => c.path)).toContain('/api/rankings?position=MID&sort=WIN_RATE')
  })

  it('정렬을 바꾸면 sort 파라미터로 조회한다', async () => {
    const { calls } = setup()
    await screen.findByText('가나')
    await userEvent.click(screen.getByRole('button', { name: 'KDA' }))
    await screen.findByRole('button', { name: 'KDA', pressed: true })
    expect(calls.map((c) => c.path)).toContain('/api/rankings?position=ALL&sort=KDA')
  })

  it('URL의 기간이 조회에 실린다', async () => {
    const { calls } = setup('/ranking?from=2026-10-01')
    await screen.findByText('가나')
    expect(calls.map((c) => c.path)).toContain('/api/rankings?position=ALL&sort=WIN_RATE&from=2026-10-01')
  })
})
