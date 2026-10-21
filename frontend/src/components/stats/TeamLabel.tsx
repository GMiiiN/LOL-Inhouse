// "A팀 · 블루" 라벨. 대결 팀 이름(TEAM1=A, TEAM2=B)과 그 세트의 진영을 함께 보여 준다.
import type { DuelTeam, Side } from '../../api/registrationTypes'
import '../../styles/stats-common.css'

type TeamLabelProps = {
  team: DuelTeam
  side: Side
}

export function TeamLabel({ team, side }: TeamLabelProps) {
  const teamName = team === 'TEAM1' ? 'A팀' : 'B팀'
  const sideName = side === 'BLUE' ? '블루' : '레드'
  return (
    <span className={`team-label ${side === 'BLUE' ? 'team-blue' : 'team-red'}`}>
      {teamName} · {sideName}
    </span>
  )
}
