// 팀 짜기 결과의 순수 함수: 수동 교체·되돌리기·진영 바꾸기·판정·복사 글.
// 서버 응답(방안 + scoreMatrix)만으로 교체 뒤 점수를 다시 계산한다. 교체 후 total은 blueSum + redSum(설계 §11 P2-6).
import { POSITION_LABEL, POSITIONS } from '../../api/types'
import type { MatrixCell, ScoreMatrix, SlotView, TeamOption } from '../../api/tierTypes'

export type Side = 'blue' | 'red'
export type SlotRef = { side: Side; i: number }
// original이 null이면 수정하지 않은 방안
export type Candidate = { current: TeamOption; original: TeamOption | null }

export const toCandidates = (options: TeamOption[]): Candidate[] =>
  options.map((current) => ({ current, original: null }))

const sum = (slots: SlotView[]) => slots.reduce((total, slot) => total + slot.score, 0)

// 합계·점수 차·최대 라인 격차·cost를 슬롯에서 다시 계산한다.
export function recalc(blue: SlotView[], red: SlotView[]): TeamOption {
  const blueSum = sum(blue)
  const redSum = sum(red)
  const diff = Math.abs(blueSum - redSum)
  const gap = Math.max(...blue.map((slot, i) => Math.abs(slot.score - red[i].score)))
  return { blue, red, blueSum, redSum, diff, gap, cost: diff * 3 + gap, total: blueSum + redSum }
}

// 그 사람이 그 라인을 맡을 때의 칸. 티어 없는 라인은 서버가 준 대체 점수를 쓴다.
function slotFor(matrix: ScoreMatrix, playerId: number, lane: number): SlotView {
  const position = POSITIONS[lane]
  const cell: MatrixCell | undefined = matrix[String(playerId)]?.[position]
  return {
    playerId,
    position,
    tier: cell?.tier ?? null,
    sub: cell?.sub ?? null,
    score: cell?.score ?? 0,
    ...(cell?.estimated && { estimated: true }),
  }
}

// 두 칸의 사람을 서로 자리 바꿈(같은 칸이면 그대로). 각자 새 자리 라인으로 점수를 다시 구한다.
export function swapPlayers(candidate: Candidate, a: SlotRef, b: SlotRef, matrix: ScoreMatrix): Candidate {
  if (a.side === b.side && a.i === b.i) return candidate
  const { blue, red } = candidate.current
  const teams: Record<Side, SlotView[]> = { blue: [...blue], red: [...red] }
  const personA = teams[a.side][a.i].playerId
  const personB = teams[b.side][b.i].playerId
  teams[a.side][a.i] = slotFor(matrix, personB, a.i)
  teams[b.side][b.i] = slotFor(matrix, personA, b.i)
  return { current: recalc(teams.blue, teams.red), original: candidate.original ?? candidate.current }
}

export const revert = (candidate: Candidate): Candidate =>
  candidate.original ? { current: candidate.original, original: null } : candidate

function flip(option: TeamOption): TeamOption {
  return {
    ...option,
    blue: option.red,
    red: option.blue,
    blueSum: option.redSum,
    redSum: option.blueSum,
  }
}

// 블루 칸과 레드 칸을 통째로 맞바꾼다. 되돌리기 기준도 같이 맞바꿔 진영이 유지된다.
export const swapSides = (candidate: Candidate): Candidate => ({
  current: flip(candidate.current),
  original: candidate.original ? flip(candidate.original) : null,
})

export type Verdict = { tone: 'good' | 'warn'; text: string }
export function verdictOf(option: TeamOption): Verdict {
  if (option.diff <= 3 && option.gap <= 12) return { tone: 'good', text: '점수도 라인 맞대결도 고르게 맞춰졌어요.' }
  if (option.diff <= 8) return { tone: 'good', text: '괜찮은 밸런스예요.' }
  return { tone: 'warn', text: '차이가 좀 있어요. 다른 방안이나 참가자 구성을 바꿔 보세요.' }
}

// 라인 가운데 칸 글: 동점 / "◀ g"(블루가 높음) / "g ▶"(레드가 높음)
export function laneGapText(blueScore: number, redScore: number): string {
  const gap = blueScore - redScore
  if (gap === 0) return '동점'
  return gap > 0 ? `◀ ${gap}` : `${-gap} ▶`
}

export function resultText(option: TeamOption, nameOf: (playerId: number) => string): string {
  const line = (slots: SlotView[]) => slots.map((slot, i) => `${POSITION_LABEL[POSITIONS[i]]} ${nameOf(slot.playerId)}`).join(' / ')
  return [
    '[롤 내전 팀]',
    `A팀 · 블루 (${option.blueSum}점)`,
    line(option.blue),
    '',
    `B팀 · 레드 (${option.redSum}점)`,
    line(option.red),
    '',
    `점수 차: ${option.diff}`,
  ].join('\n')
}
