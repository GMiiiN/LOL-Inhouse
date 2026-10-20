// 세트 등록 화면 테스트용 가짜 서버 데이터(초안, 챔피언 목록, 등록 맥락). 닉네임은 모두 지어낸 값이다.
import type {
  ChampionGuess,
  ChampionList,
  DraftBan,
  DraftResponse,
  DraftRow,
  DraftTeam,
  Evaluation,
  RegistrationContext,
  RowInput,
  SetRegistrationRequest,
  TeamInput,
} from '../api/registrationTypes'
import type { Position } from '../api/types'

export const CHAMPION_COUNT = 30
export const championId = (index: number) => `Champion${String(index).padStart(2, '0')}`

export const champions: ChampionList = {
  version: '16.20.1',
  champions: Array.from({ length: CHAMPION_COUNT }, (_, index) => ({
    id: championId(index),
    key: String(index + 1),
    name: `챔피언${String(index).padStart(2, '0')}`,
    iconUrl: `https://cdn.test/img/${championId(index)}.png`,
  })),
}

export const registrationContext: RegistrationContext = {
  duelId: 11,
  sessionId: 5,
  sessionStartDate: '2026-10-20',
  fearlessMode: 'RESET',
  duelSeq: 1,
  nextSetNo: 1,
  defaultTopSide: 'BLUE',
  restrictedChampions: [],
  registeredSets: [],
}

function guess(first: number): ChampionGuess {
  return {
    candidates: [first, first + 1, first + 2].map((i, n) => ({ id: championId(i % CHAMPION_COUNT), score: 0.9 - n * 0.1 })),
    margin: 0.4,
    uncertain: false,
    cropFailed: false,
  }
}

const box = (n: number): [number, number, number, number] => [n * 10, n * 10, n * 10 + 40, n * 10 + 40]

function draftRow(prefix: string, screenRow: number, position: Position, pick: number, overrides: Partial<DraftRow> = {}): DraftRow {
  const playerId = (prefix === 'T' ? 0 : 10) + screenRow
  return {
    screenRow,
    position,
    readNickname: `${prefix}${screenRow}`,
    truncated: false,
    match: {
      type: 'EXACT',
      playerId,
      playerDisplayName: `선수${prefix}${screenRow}`,
      similarity: 1,
      candidates: [],
      fullNicknameRequired: false,
      newPlayerName: null,
    },
    kills: 1,
    deaths: 2,
    assists: 3,
    cs: 100,
    gold: 10000,
    portraitBox: box(screenRow),
    champion: guess(pick),
    ...overrides,
  }
}

function inputRow(row: DraftRow): RowInput {
  const resolved = row.match.type === 'EXACT' || row.match.type === 'ONE_CHAR_AUTO' || row.match.type === 'TRUNCATED_AUTO'
  return {
    screenRow: row.screenRow,
    position: row.position,
    nickname: row.readNickname,
    resolution: resolved ? 'EXISTING' : 'UNRESOLVED',
    playerId: resolved ? row.match.playerId : null,
    addAlias: false,
    championKey: row.champion.candidates[0]?.id ?? null,
    kills: row.kills,
    deaths: row.deaths,
    assists: row.assists,
    cs: row.cs,
    gold: row.gold,
  }
}

const TOP_POSITIONS: Position[] = ['ADC', 'TOP', 'JUNGLE', 'MID', 'SUPPORT']
const BOTTOM_POSITIONS: Position[] = ['TOP', 'JUNGLE', 'MID', 'ADC', 'SUPPORT']

function draftBans(start: number): DraftBan[] {
  return [1, 2, 3, 4, 5].map((slot) => ({ slot, box: box(slot + 5), champion: guess(start + slot - 1) }))
}

function team(prefix: string, positions: Position[], pickStart: number, banStart: number, rowOverrides: Record<number, Partial<DraftRow>>) {
  const rows = positions.map((position, i) => draftRow(prefix, i + 1, position, pickStart + i, rowOverrides[i + 1]))
  const draftTeam: DraftTeam = {
    displayedKills: 5,
    displayedDeaths: 10,
    displayedAssists: 15,
    displayedGold: 50000,
    rows,
    bans: draftBans(banStart),
  }
  const input: TeamInput = {
    displayedKills: 5,
    displayedDeaths: 10,
    displayedAssists: 15,
    displayedGold: 50000,
    rows: rows.map(inputRow),
    bans: draftBans(banStart).map((b) => b.champion.candidates[0].id),
  }
  return { draftTeam, input }
}

export const baseEvaluation: Evaluation = {
  setNo: 1,
  topTeam: 'TEAM1',
  detection: { method: 'FIRST_SET', overlap: null, uncertain: false },
  team1Side: 'BLUE',
  fingerprint: 'fp',
  duplicateSet: null,
  errors: [],
  warnings: [],
  teamChange: { kind: 'NONE', swaps: 0, details: [] },
}

// 위쪽 팀 2행은 후보가 둘인 닉네임(선수를 골라야 함), 나머지는 정확 일치
export function buildDraft(): DraftResponse {
  const top = team('T', TOP_POSITIONS, 0, 20, {
    2: {
      readNickname: '홍길',
      match: {
        type: 'CANDIDATES',
        playerId: null,
        playerDisplayName: null,
        similarity: 0.8,
        candidates: [
          { playerId: 101, displayName: '홍길동', nickname: '홍길동1', similarity: 0.82 },
          { playerId: 102, displayName: '고길동', nickname: '고길동1', similarity: 0.55 },
        ],
        fullNicknameRequired: false,
        newPlayerName: null,
      },
    },
  })
  const bottom = team('B', BOTTOM_POSITIONS, 10, 25, {})
  const defaults: SetRegistrationRequest = {
    extractionJobId: 7,
    topSide: 'BLUE',
    capturerPosition: 'ADC',
    topResult: 'WIN',
    shotDate: '2026-10-12',
    duration: '25:42',
    top: top.input,
    bottom: bottom.input,
  }
  return {
    jobId: 7,
    duelId: 11,
    topSide: 'BLUE',
    capturerPosition: 'ADC',
    topResult: 'WIN',
    shotDate: '2026-10-12',
    duration: '25:42',
    top: top.draftTeam,
    bottom: bottom.draftTeam,
    defaults,
    evaluation: { ...baseEvaluation, errors: ['선수를 골라야 합니다: 위쪽 팀 결과창 2행'] },
  }
}
