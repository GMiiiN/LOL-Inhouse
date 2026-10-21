// 홈: 전체 빈 상태, 라인별 최다 픽(null이면 "—")과 최근 경기
import { screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { DuoEntry, Duos, Home, SetView } from '../../api/publicTypes'
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

const duo = (a: number, b: number, wins: number, losses: number): DuoEntry => ({
  players: [
    { playerId: a, displayName: `선수${a}`, registered: true },
    { playerId: b, displayName: `선수${b}`, registered: true },
  ],
  sets: wins + losses,
  wins,
  losses,
  winRate: wins / (wins + losses),
})
const noDuos: Duos = { minSets: 3, best: [], worst: [] }

function setup(home: Home, duos: Duos = noDuos) {
  mockFetch({
    'GET /api/champions': championsHandler,
    'GET /api/home': () => jsonResponse(200, home),
    'GET /api/duos': () => jsonResponse(200, duos),
  })
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

  it('왼쪽 열에 최고·최악의 듀오 3쌍씩과 3세트 안내를 보여 준다', async () => {
    setup(
      { recentSets: [set], topPicksByPosition: none },
      {
        minSets: 3,
        best: [duo(1, 2, 5, 0), duo(3, 4, 4, 1), duo(5, 6, 3, 1)],
        worst: [duo(7, 8, 0, 4), duo(9, 10, 1, 4), duo(11, 12, 1, 3)],
      },
    )
    const best = await screen.findByRole('region', { name: '최고의 듀오' })
    expect(within(best).getAllByRole('listitem')).toHaveLength(3)
    expect(within(best).getByText('선수1 + 선수2')).toBeInTheDocument()
    expect(within(best).getByText(/5세트 · 5승 0패/)).toBeInTheDocument()
    expect(within(screen.getByRole('region', { name: '최악의 듀오' })).getAllByRole('listitem')).toHaveLength(3)
    expect(screen.getByText(/3세트 이상 뛴 두 사람만/)).toBeInTheDocument()
  })

  it('듀오가 없으면 빈 상태를 보여 준다', async () => {
    setup({ recentSets: [set], topPicksByPosition: none })
    expect(await screen.findByText(/함께 3세트 이상 뛴 듀오가 없어요/)).toBeInTheDocument()
  })

  it('최근 경기 3개와 내전 기록 더 보기 링크를 보여 준다', async () => {
    const sets = [11, 12, 13].map((id) => ({ ...set, setId: id }))
    setup({ recentSets: sets, topPicksByPosition: none })
    expect(await screen.findAllByRole('link', { name: '세트 상세' })).toHaveLength(3)
    expect(screen.getByRole('link', { name: '내전 기록 더 보기' })).toHaveAttribute('href', '/records')
  })

  it('최근 경기 접힌 줄에 참여한 선수 이름이 챔피언 아래 보인다', async () => {
    setup({ recentSets: [set], topPicksByPosition: none })
    await screen.findByRole('link', { name: '세트 상세' })
    const names = set.teams.flatMap((t) => t.players.map((p) => p.displayName))
    expect(document.querySelectorAll('.set-row-pick-name')).toHaveLength(names.length)
    for (const name of names) expect(screen.getAllByText(name).length).toBeGreaterThan(0)
  })
})
