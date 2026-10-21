// 공개 티어리스트(/tiers): 로그인 없이 보기 전용
import { TierListPanel } from './TierListPanel'

export function TierListPage() {
  return (
    <section>
      <div className="page-title-row">
        <h1>티어리스트</h1>
      </div>
      <TierListPanel />
    </section>
  )
}
