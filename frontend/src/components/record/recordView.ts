// 기록 부품들이 함께 쓰는 순수 계산과 라벨.
import type { BanView, DuelHead, SetView, TeamView } from '../../api/publicTypes'

export function sidesOf(set: SetView): { blue: TeamView; red: TeamView } {
  const [team1, team2] = set.teams
  return team1.side === 'BLUE' ? { blue: team1, red: team2 } : { blue: team2, red: team1 }
}

// 턴 순서대로 진영별 순번을 붙인다: 첫 블루 밴 B1, 첫 레드 밴 R1 ...
export function banLabels(bans: BanView[]): { ban: BanView; label: string }[] {
  const count = { BLUE: 0, RED: 0 }
  return [...bans]
    .sort((a, b) => a.turn - b.turn)
    .map((ban) => {
      count[ban.side] += 1
      return { ban, label: `${ban.side === 'BLUE' ? 'B' : 'R'}${count[ban.side]}` }
    })
}

export const FEARLESS_LABEL: Record<DuelHead['fearlessMode'], string> = {
  RESET: '피어리스 초기화',
  CONTINUE: '피어리스 이어가기',
}
