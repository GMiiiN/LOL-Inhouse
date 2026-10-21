// 홈: 전체 빈 상태, 라인별 최다 픽(null이면 "—")과 최근 경기
import { screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { Home, SetView } from '../../api/publicTypes'
import { jsonResponse, mockFetch } from '../../test/mockFetch'
import { championsHandler, renderPage } from '../../test/statsFixtures'
import { HomePage } from './HomePage'

const none = { TOP: null, JUNGLE: null, MID: null, ADC: null, SUPPORT: null }

const line = (position: SetView['teams'][0]['players'][0]['position'], playerId: number) => ({
  playerId,
  displayName: `선수${playerId}`,
  registered: true,
  position,
  championKey: 'Ahri',
  kills: 1,
  deaths: 1,
  assists: 1,
  cs: 100,
  gold: 9000,
})

const set: SetView = {
  setId: 11,
  setNo: 2,
  duelId: 5,
  duelSeq: 1,
  sessionId: 3,
  sessionDate: '2026-10-12',
  shotDate: '2026-10-12',
  durationSec: 1854,
  team1Side: 'BLUE',
  winnerTeam: 'TEAM1',
  teams: [
    { team: 'TEAM1', side: 'BLUE', won: true, displayed: { kills: 1, deaths: 1, assists: 1, gold: 1 }, players: [line('TOP', 1)] },
    { team: 'TEAM2', side: 'RED', won: false, displayed: { kills: 1, deaths: 1, assists: 1, gold: 1 }, players: [line('TOP', 2)] },
  ],
  bans: [],
  fearlessRestricted: [],
}

function setup(home: Home) {
  mockFetch({ 'GET /api/champions': championsHandler, 'GET /api/home': () => jsonResponse(200, home) })
  renderPage(<HomePage />, '/')
}

describe('HomePage', () => {
  it('기록이 하나도 없으면 빈 상태 한 줄만 보여 준다', async () => {
    setup({ recentSets: [], topPicksByPosition: none })
    expect(await screen.findByText('아직 기록된 경기가 없어요.')).toBeInTheDocument()
    expect(screen.queryByText('최근 경기')).not.toBeInTheDocument()
    expect(screen.queryByText(/가장 많이 나온 픽/)).not.toBeInTheDocument()
  })

  it('라인별 최다 픽은 값이 null이면 "—", 있으면 챔피언과 횟수를 보여 준다', async () => {
    setup({ recentSets: [set], topPicksByPosition: { ...none, MID: { championKey: 'Ahri', picks: 4, wins: 3 } } })
    const mid = await screen.findByLabelText('미드 최다 픽')
    expect(await within(mid).findByText('아리')).toBeInTheDocument()
    expect(within(mid).getByText('4회 · 3승')).toBeInTheDocument()
    expect(within(screen.getByLabelText('탑 최다 픽')).getByText('—')).toBeInTheDocument()
    expect(screen.getByText('2026-10-12')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '대결 1' })).toHaveAttribute('href', `/duels/${set.duelId}`)
    expect(screen.getByRole('link', { name: '세트 상세' })).toHaveAttribute('href', '/sets/11')
  })
})
