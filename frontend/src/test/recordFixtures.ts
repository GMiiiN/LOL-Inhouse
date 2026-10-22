// 기록 화면 테스트용 SetView·RecordPage 조립 도우미
import type { BanView, DuelBlock, PlayerLine, RecordPage, SetView, TeamView } from '../api/publicTypes'
import { POSITIONS } from '../api/types'
import type { DuelTeam, Side } from '../api/registrationTypes'

function team(teamId: DuelTeam, side: Side, prefix: string, kills: number): TeamView {
  const players: PlayerLine[] = POSITIONS.map((position, index) => ({
    playerId: (teamId === 'TEAM1' ? 10 : 20) + index,
    displayName: `${prefix}${position}`,
    registered: true,
    position,
    championKey: `${prefix}Champ${index}`,
    kills: 1,
    deaths: 2,
    assists: 3,
    cs: 100,
    gold: 10000,
  }))
  return { team: teamId, side, won: false, displayed: { kills, deaths: 10, assists: 20, gold: 60000 }, players }
}

// A팀(TEAM1)의 진영을 team1Side로 고른다. A팀 킬 23, B팀 킬 17.
export function makeSet(overrides: Partial<SetView> & { team1Side?: Side } = {}): SetView {
  const team1Side = overrides.team1Side ?? 'BLUE'
  const bans: BanView[] = Array.from({ length: 10 }, (_, i) => ({
    turn: i + 1,
    side: i % 2 === 0 ? 'BLUE' : 'RED',
    championKey: `Ban${i + 1}`,
  }))
  return {
    setId: 1,
    setNo: 1,
    duelId: 7,
    duelSeq: 1,
    sessionId: 3,
    sessionDate: '2026-10-18',
    shotDate: '2026-10-18',
    durationSec: 1865,
    team1Side,
    winnerTeam: 'TEAM1',
    teams: [team('TEAM1', team1Side, 'A', 23), team('TEAM2', team1Side === 'BLUE' ? 'RED' : 'BLUE', 'B', 17)],
    bans,
    fearlessRestricted: ['Zed', 'Ahri'],
    ...overrides,
  }
}

export function makeDuel(overrides: Partial<DuelBlock> = {}): DuelBlock {
  return {
    duelId: 7,
    seq: 1,
    fearlessMode: 'RESET',
    score: { TEAM1: 2, TEAM2: 1 },
    totalSets: 3,
    sets: [makeSet()],
    ...overrides,
  }
}

export function makePage(duels: DuelBlock[], nextBefore: string | null = null, startDate = '2026-10-18'): RecordPage {
  const sets = duels.reduce((sum, duel) => sum + duel.sets.length, 0)
  return { sessions: duels.length ? [{ sessionId: 3, startDate, duels }] : [], nextBefore, totals: { duels: duels.length, sets } }
}
