// 최근 세트 목록(홈·프로필 공용). 기록 화면과 같은 SetRow를 대결 링크와 함께 쓴다.
import type { SetView } from '../../api/publicTypes'
import { SetRow } from '../../components/record/SetRow'
import '../../styles/stats.css'

export function RecentSetList({ sets }: { sets: SetView[] }) {
  return (
    <div className="stat-recent">
      {sets.map((set) => (
        <div key={set.setId} className="stat-recent-item">
          <span className="stat-recent-date">{set.sessionDate}</span>
          <SetRow set={set} showDuelLink showNames />
        </div>
      ))}
    </div>
  )
}
