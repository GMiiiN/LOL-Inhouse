// 선수 프로필: 라인 카드 순서, 챔피언 행 링크(PLAYER_PICK + 기간 유지), 기록 없음, 없는 선수 404
import { screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { PlayerRecord } from '../../api/publicTypes'
import { jsonResponse, mockFetch, problemResponse } from '../../test/mockFetch'
import { championsHandler, renderPage, summary } from '../../test/statsFixtures'
import { PlayerProfilePage } from './PlayerProfilePage'

const NO_TIERS = { TOP: null, JUNGLE: null, MID: null, ADC: null, SUPPORT: null }

const base: PlayerRecord = {
  playerId: 3,
  displayName: '가나',
  registered: true,
  mainPosition: 'MID',
  laneTiers: { ...NO_TIERS, MID: { tier: 2, sub: 'HIGH', trend: null } },
  totalSets: 10,
  participationRate: 0.6,
  summary: summary(6, 4, 30, 12, 24),
  byPosition: [
    { position: 'TOP', summary: summary(1, 1, 3, 1, 2), topChampions: [{ championKey: 'Zed', sets: 1 }] },
    { position: 'MID', summary: summary(4, 3, 20, 8, 16), topChampions: [{ championKey: 'Ahri', sets: 3 }] },
    { position: 'ADC', summary: summary(1, 0, 1, 2, 3), topChampions: [{ championKey: 'Jinx', sets: 1 }] },
  ],
  byChampion: [
    { championKey: 'Ahri', summary: summary(3, 2, 10, 4, 8) },
    { championKey: 'Zed', summary: summary(1, 1, 3, 1, 2) },
  ],
  duels: { wins: 1, draws: 1, losses: 0, items: [{ duelId: 9, sessionDate: '2026-10-12', seq: 2, wins: 2, losses: 1, result: 'WIN' }] },
  recentSets: [],
}

function setup(url: string, handler: () => Response) {
  mockFetch({ 'GET /api/champions': championsHandler, [`GET ${url.replace('/players', '/api/players')}`]: handler })
  renderPage(<PlayerProfilePage />, url, '/players/:playerId')
}

describe('PlayerProfilePage', () => {
  it('라인 카드는 경기 수가 많은 라인부터 보여 준다', async () => {
    setup('/players/3', () => jsonResponse(200, base))
    await screen.findByText('요약')
    const cards = screen.getAllByRole('article').map((el) => el.getAttribute('aria-label'))
    expect(cards).toEqual(['미드 기록', '탑 기록', '원딜 기록']) // 4경기, 1경기(탑→원딜 순서 유지)
    expect(screen.getByText('경기 참여율').nextSibling).toHaveTextContent('60%')
    expect(within(screen.getByLabelText('라인 티어')).getByText('2티어 상')).toBeInTheDocument()
  })

  it('챔피언 행은 PLAYER_PICK 범위의 기록 링크이고 현재 기간을 유지한다', async () => {
    setup('/players/3?from=2026-10-01&to=2026-10-31', () => jsonResponse(200, base))
    const table = await screen.findByRole('table', { name: '챔피언별 기록' })
    const links = within(table).getAllByRole('link')
    expect(links[0]).toHaveAttribute(
      'href',
      '/records?player=3&champion=Ahri&championScope=PLAYER_PICK&from=2026-10-01&to=2026-10-31',
    )
  })

  it('기간이 없으면 링크에도 기간이 없다. 대결 결과는 대결 상세로 간다', async () => {
    setup('/players/3', () => jsonResponse(200, base))
    const table = await screen.findByRole('table', { name: '챔피언별 기록' })
    expect(within(table).getAllByRole('link')[1]).toHaveAttribute(
      'href',
      '/records?player=3&champion=Zed&championScope=PLAYER_PICK',
    )
    expect(screen.getByText('1승 1무 0패')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '2026-10-12' })).toHaveAttribute('href', '/duels/9')
  })

  it('기록이 없는 선수는 머리와 라인 티어만 보여 주고 안내 문구를 낸다', async () => {
    const empty: PlayerRecord = {
      ...base,
      summary: summary(0, 0),
      byPosition: [],
      byChampion: [],
      participationRate: 0,
      duels: { wins: 0, draws: 0, losses: 0, items: [] },
    }
    setup('/players/3', () => jsonResponse(200, empty))
    expect(await screen.findByText('아직 기록된 경기가 없어요.')).toBeInTheDocument()
    expect(screen.getByLabelText('라인 티어')).toBeInTheDocument()
    expect(screen.queryByText('라인별 기록')).not.toBeInTheDocument()
  })

  it('없는 선수는 404 안내 화면', async () => {
    setup('/players/99', () => problemResponse(404, 'PLAYER_NOT_FOUND'))
    expect(await screen.findByText('선수를 찾을 수 없어요')).toBeInTheDocument()
  })
})
