// 확인 화면(ReviewStep)의 상태 계산을 모은 순수 함수들. React·네트워크를 쓰지 않아 단독으로 시험할 수 있다.
// 설계: docs/design/m4-design.md §6·§11
import { ApiError } from '../../../api/client'
import type {
  DraftRow,
  DuelTeam,
  Evaluation,
  NewDuelOption,
  RowInput,
  SetRegistrationRequest,
  Side,
  TeamInput,
} from '../../../api/registrationTypes'
import { POSITIONS, type Position } from '../../../api/types'

// 확인 화면이 들고 있는 편집 상태 = 저장 명령 그대로(newDuel만 따로 둔다). 키는 (팀, 결과창 행 번호)다.
export type ReviewState = Omit<SetRegistrationRequest, 'newDuel'>
// 팀 표·밴 칸이 다루는 최소 상태. 세트 수정 화면(setFormModel)도 이 모양을 그대로 쓴다.
export type TeamsState = Pick<ReviewState, 'topSide' | 'topResult' | 'shotDate' | 'duration' | 'top' | 'bottom'>
export type TeamKey = 'top' | 'bottom'
export const TEAM_KEYS: readonly TeamKey[] = ['top', 'bottom']

// 화면 표기: 내부 값 TEAM1/TEAM2는 "A팀/B팀"으로 보여 준다(2026-10-20 결정, 값은 그대로)
export const TEAM_LABEL: Record<DuelTeam, string> = { TEAM1: 'A팀', TEAM2: 'B팀' }

// 오류(빨강, 저장 막음)와 경고(노랑)를 한 모양으로 다룬다. check 응답과 422 응답이 모두 이 모양으로 바뀐다.
export type ReviewIssues = { errors: string[]; warnings: string[] }

// ---------- 초기 상태·정렬 ----------

export function cloneTeam(team: TeamInput): TeamInput {
  return {
    ...team,
    rows: [...team.rows].sort((a, b) => a.screenRow - b.screenRow).map((row) => ({ ...row })),
    bans: [...team.bans],
  }
}

// 초안의 defaults를 복사해 행을 결과창 순서(screenRow)로 맞춘다. 원본은 건드리지 않는다.
export function initReviewState(defaults: SetRegistrationRequest): ReviewState {
  return {
    extractionJobId: defaults.extractionJobId,
    topSide: defaults.topSide,
    capturerPosition: defaults.capturerPosition,
    topResult: defaults.topResult,
    shotDate: defaults.shotDate,
    duration: defaults.duration,
    top: cloneTeam(defaults.top),
    bottom: cloneTeam(defaults.bottom),
  }
}

// 화면 표시용: 탑→서포터 순. 결과창 행 번호(screenRow)는 그대로 유지해 원본 대조를 돕는다.
export function rowsByLane(team: TeamInput): RowInput[] {
  return [...team.rows].sort((a, b) => POSITIONS.indexOf(a.position) - POSITIONS.indexOf(b.position))
}

export function updateRow<S extends TeamsState>(state: S, team: TeamKey, screenRow: number, patch: Partial<RowInput>): S {
  const target = state[team]
  const rows = target.rows.map((row) => (row.screenRow === screenRow ? { ...row, ...patch } : row))
  return { ...state, [team]: { ...target, rows } }
}

// 행 하나를 함수로 바꾼다(선수 선택 같은 도우미 함수 결과를 그대로 넣을 때)
export function replaceRow<S extends TeamsState>(state: S, team: TeamKey, next: RowInput): S {
  const target = state[team]
  const rows = target.rows.map((row) => (row.screenRow === next.screenRow ? next : row))
  return { ...state, [team]: { ...target, rows } }
}

export function updateTeam<S extends TeamsState>(
  state: S,
  team: TeamKey,
  patch: Partial<Omit<TeamInput, 'rows' | 'bans'>>,
): S {
  return { ...state, [team]: { ...state[team], ...patch } }
}

export function setBan<S extends TeamsState>(state: S, team: TeamKey, slot: number, championKey: string | null): S {
  const bans = state[team].bans.map((ban, index) => (index === slot - 1 ? championKey : ban))
  return { ...state, [team]: { ...state[team], bans } }
}

// ---------- 포지션 ----------

// 같은 팀에서 screenRow 행의 포지션을 바꾸면, 그 포지션을 갖고 있던 행과 서로 맞바꾼다(PositionAssignment.swapPositions와 같은 규칙).
export function swapPosition<S extends TeamsState>(state: S, team: TeamKey, screenRow: number, position: Position): S {
  const rows = state[team].rows
  const from = rows.find((row) => row.screenRow === screenRow)
  const other = rows.find((row) => row.position === position && row.screenRow !== screenRow)
  if (!from || !other) return state
  return {
    ...state,
    [team]: {
      ...state[team],
      rows: rows.map((row) => {
        if (row.screenRow === from.screenRow) return { ...row, position: other.position }
        if (row.screenRow === other.screenRow) return { ...row, position: from.position }
        return row
      }),
    },
  }
}

// 위쪽 팀 행 순서: 첫 행이 캡처한 사람, 나머지는 그 포지션을 뺀 기본 순서(PositionAssignment.topTeamPositions와 같다).
export function topTeamPositions(capturerPosition: Position): Position[] {
  return [capturerPosition, ...POSITIONS.filter((position) => position !== capturerPosition)]
}

// 캡처한 사람 포지션을 바꾸면 위쪽 팀 포지션을 규칙대로 다시 매긴다(직접 맞바꾼 것은 사라진다). 아래쪽 팀은 그대로.
export function changeCapturerPosition(state: ReviewState, position: Position): ReviewState {
  const positions = topTeamPositions(position)
  return {
    ...state,
    capturerPosition: position,
    top: { ...state.top, rows: state.top.rows.map((row) => ({ ...row, position: positions[row.screenRow - 1] })) },
  }
}

// ---------- 밴 라벨 ----------

// 팀별 밴 칸(화면 순서 1~5)의 턴 라벨. 블루는 B1~B5, 레드는 R1~R5. 턴 확정은 서버(BanOrder.build)가 하고 이것은 표시 전용이다.
export function banLabels(topSide: Side): Record<TeamKey, string[]> {
  const letter = (side: Side) => (side === 'BLUE' ? 'B' : 'R')
  const topLetter = letter(topSide)
  const bottomLetter = letter(topSide === 'BLUE' ? 'RED' : 'BLUE')
  const make = (prefix: string) => [1, 2, 3, 4, 5].map((n) => `${prefix}${n}`)
  return { top: make(topLetter), bottom: make(bottomLetter) }
}

// ---------- 선수 연결 ----------

const TRUNCATION_SUFFIX = /(…|\.\.\.)$/

// 닉네임이 아직 잘린 상태인가: "…"로 끝나거나, 잘린 채 읽은 값 그대로인 경우
export function isTruncatedNickname(row: RowInput, draftRow: DraftRow): boolean {
  const nickname = row.nickname.trim()
  if (TRUNCATION_SUFFIX.test(nickname)) return true
  return draftRow.truncated && nickname === draftRow.readNickname.trim()
}

export type PlayerStatus = { label: string; tone: 'ok' | 'auto' | 'warn' | 'new' | 'error' }

// 선수 칸의 상태 배지. 서버 매칭 결과(match)와 담당자의 현재 선택(resolution)을 함께 본다.
export function playerStatus(row: RowInput, draftRow: DraftRow): PlayerStatus {
  const { match } = draftRow
  if (row.resolution === 'NEW') return { label: '새 선수', tone: 'new' }
  if (row.resolution === 'EXISTING') {
    if (row.playerId !== null && row.playerId === match.playerId) {
      if (match.type === 'EXACT') return { label: '정확', tone: 'ok' }
      if (match.type === 'ONE_CHAR_AUTO') return { label: '자동(한 글자 차이)', tone: 'auto' }
      if (match.type === 'TRUNCATED_AUTO') return { label: '자동(잘림)', tone: 'auto' }
    }
    return { label: '직접 선택', tone: 'ok' }
  }
  if (match.fullNicknameRequired) return { label: '전체 닉네임 필요', tone: 'error' }
  if (match.type === 'CANDIDATES') return { label: `후보 ${match.candidates.length}명`, tone: 'warn' }
  return { label: '선수를 골라 주세요', tone: 'error' }
}

export type AliasControl = { visible: boolean; checked: boolean; disabled: boolean; hint: string | null }

const HIDDEN: AliasControl = { visible: false, checked: false, disabled: true, hint: null }

function normalizeForCompare(nickname: string): string {
  return nickname.trim().toLowerCase()
}

// "이 닉네임을 별칭으로 추가" 체크박스 규칙(설계 §6):
//  - 정확 일치로 연결됐거나 그 선수가 이미 이 닉네임을 가졌으면 숨김
//  - 자동 연결(한 글자 차이·잘림)이면 해제·비활성
//  - 닉네임이 아직 잘렸으면 비활성(전체 닉네임을 입력해야 체크 가능)
//  - 그 밖(담당자가 직접 고른 선수)은 활성
export function aliasControl(row: RowInput, draftRow: DraftRow, knownNicknames: readonly string[] = []): AliasControl {
  if (row.resolution !== 'EXISTING' || row.playerId === null) return HIDDEN
  const { match } = draftRow
  if (row.playerId === match.playerId) {
    if (match.type === 'EXACT') return HIDDEN
    if (match.type === 'ONE_CHAR_AUTO' || match.type === 'TRUNCATED_AUTO') {
      return { visible: true, checked: false, disabled: true, hint: '자동 연결이라 별칭을 추가하지 않습니다.' }
    }
  }
  if (isTruncatedNickname(row, draftRow)) {
    return {
      visible: true,
      checked: false,
      disabled: true,
      hint: '닉네임이 잘렸습니다. 전체 닉네임을 입력하면 별칭으로 추가할 수 있습니다.',
    }
  }
  const nickname = normalizeForCompare(row.nickname)
  if (knownNicknames.some((known) => normalizeForCompare(known) === nickname)) return HIDDEN
  return { visible: true, checked: row.addAlias, disabled: false, hint: null }
}

// 기존 선수를 고른다. 직접 고른 선수면 별칭 추가를 기본으로 체크하고, 자동 연결과 같은 선수면 체크하지 않는다.
export function selectExistingPlayer(
  row: RowInput,
  draftRow: DraftRow,
  player: { playerId: number; nicknames: readonly string[] },
): RowInput {
  const next: RowInput = { ...row, resolution: 'EXISTING', playerId: player.playerId, addAlias: false }
  const control = aliasControl(next, draftRow, player.nicknames)
  return { ...next, addAlias: control.visible && !control.disabled }
}

// "새 미등록 선수로": playerId=null, 이 행의 nickname으로 서버가 미등록 선수를 만든다.
export function selectNewPlayer(row: RowInput): RowInput {
  return { ...row, resolution: 'NEW', playerId: null, addAlias: false }
}

// 닉네임을 고친다. 별칭 추가가 켜져 있었는데 닉네임이 다시 잘린 상태가 되면 끈다.
export function setRowNickname(row: RowInput, draftRow: DraftRow, nickname: string): RowInput {
  const next = { ...row, nickname }
  return next.addAlias && isTruncatedNickname(next, draftRow) ? { ...next, addAlias: false } : next
}

// ---------- 저장 명령 ----------

// 화면 상태 → 서버로 보낼 명령. resolution과 playerId·addAlias가 서로 어긋나지 않게 정리한다.
function cleanRow(row: RowInput): RowInput {
  const nickname = row.nickname.trim()
  if (row.resolution === 'EXISTING' && row.playerId !== null) return { ...row, nickname }
  if (row.resolution === 'NEW') return { ...row, nickname, playerId: null, addAlias: false }
  return { ...row, nickname, resolution: 'UNRESOLVED', playerId: null, addAlias: false }
}

export function cleanTeam(team: TeamInput): TeamInput {
  return { ...team, rows: [...team.rows].sort((a, b) => a.screenRow - b.screenRow).map(cleanRow) }
}

export function buildRequest(state: ReviewState, newDuel: NewDuelOption | null): SetRegistrationRequest {
  const request: SetRegistrationRequest = { ...state, top: cleanTeam(state.top), bottom: cleanTeam(state.bottom) }
  return newDuel ? { ...request, newDuel } : request
}

// ---------- 화면 강조용 검사 (최종 판단은 서버) ----------

export const rowKey = (team: TeamKey, screenRow: number) => `row:${team}:${screenRow}`
export const banKey = (team: TeamKey, slot: number) => `ban:${team}:${slot}`

function playerIdentity(row: RowInput): string | null {
  if (row.resolution === 'EXISTING' && row.playerId !== null) return `p:${row.playerId}`
  if (row.resolution === 'NEW') return `n:${normalizeForCompare(row.nickname)}`
  return null
}

function countBy(map: Map<string, number>, key: string | null) {
  if (key) map.set(key, (map.get(key) ?? 0) + 1)
}

// 칸마다 문제 문구를 모은다. 키는 rowKey/banKey. 빈 챔피언·밴, 세트 안 중복, 제한 챔피언, 미확정 선수, 같은 선수 중복.
export function slotIssues(state: TeamsState, restricted: ReadonlySet<string>): Record<string, string[]> {
  const issues: Record<string, string[]> = {}
  const add = (key: string, message: string) => {
    if (!issues[key]) issues[key] = []
    issues[key].push(message)
  }

  const pickCount = new Map<string, number>()
  const banCount = new Map<string, number>()
  const playerCount = new Map<string, number>()
  for (const team of TEAM_KEYS) {
    for (const row of state[team].rows) {
      countBy(pickCount, row.championKey)
      countBy(playerCount, playerIdentity(row))
    }
    for (const ban of state[team].bans) countBy(banCount, ban)
  }

  for (const team of TEAM_KEYS) {
    for (const row of state[team].rows) {
      const key = rowKey(team, row.screenRow)
      if (!row.championKey) {
        add(key, '챔피언을 골라 주세요')
      } else {
        if ((pickCount.get(row.championKey) ?? 0) > 1) add(key, '이 세트에서 같은 챔피언이 두 번 쓰였습니다')
        if (restricted.has(row.championKey)) add(key, '피어리스 제한 챔피언입니다')
      }
      if (row.resolution === 'UNRESOLVED') add(key, '선수를 골라 주세요')
      const identity = playerIdentity(row)
      if (identity && (playerCount.get(identity) ?? 0) > 1) add(key, '같은 선수가 두 번 들어 있습니다')
    }
    state[team].bans.forEach((ban, index) => {
      const key = banKey(team, index + 1)
      if (!ban) add(key, '밴 챔피언을 골라 주세요')
      else if ((banCount.get(ban) ?? 0) > 1) add(key, '같은 챔피언이 두 번 밴되었습니다')
    })
  }
  return issues
}

// ---------- 오류·경고 모델 ----------

export function issuesFromEvaluation(evaluation: Evaluation): ReviewIssues {
  return { errors: [...evaluation.errors], warnings: [...evaluation.warnings] }
}

// 422 SET_RULES_VIOLATED의 violations/warnings를 같은 모양으로. 다른 오류면 null.
export function issuesFromError(error: unknown): ReviewIssues | null {
  if (error instanceof ApiError && error.code === 'SET_RULES_VIOLATED') {
    return { errors: [...error.violations], warnings: [...error.warnings] }
  }
  return null
}

// 저장 가능 여부: 검사가 최신이고(디바운스 대기·요청 중 아님), 검사 오류가 없고, 같은 경기가 이미 없을 때만
export function canSave(evaluation: Evaluation | null, settling: boolean, checkFailed: boolean): boolean {
  if (!evaluation || settling || checkFailed) return false
  return evaluation.errors.length === 0 && evaluation.duplicateSet === null
}
