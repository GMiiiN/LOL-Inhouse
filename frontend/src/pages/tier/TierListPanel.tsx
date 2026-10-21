// 티어리스트 패널(공개 /tiers와 담당자 /admin/players가 함께 쓴다): 데이터 조회 + 티어표/사람별 전환.
// 편집 기능(onEdit·onDelete)은 담당자 화면이 넘길 때만 켜진다.
import { useQuery } from '@tanstack/react-query'
import { useState, type ReactNode } from 'react'
import { getTierList } from '../../api/tier'
import { TIER_LIST_KEY } from '../../api/queryKeys'
import { ErrorNotice } from '../../components/ErrorNotice'
import '../../styles/tier.css'
import { TierBoard } from './TierBoard'
import { TierPeopleTable } from './TierPeopleTable'

type TierView = 'board' | 'people'

type TierListPanelProps = {
  onEdit?: (playerId: number) => void
  onDelete?: (playerId: number) => void
  toolbar?: ReactNode // 보기 전환 옆에 붙는 버튼들(담당자 화면)
  emptyHint?: string
}

export function TierListPanel({ onEdit, onDelete, toolbar, emptyHint }: TierListPanelProps) {
  const [view, setView] = useState<TierView>('board')
  const tierList = useQuery({ queryKey: TIER_LIST_KEY, queryFn: getTierList })
  const players = tierList.data?.players ?? []

  return (
    <section className="panel" aria-label="티어리스트">
      <div className="tier-bar">
        <h2>
          티어리스트 <span className="tier-count">{tierList.data ? `${players.length}명` : ''}</span>
        </h2>
        <div className="tier-group">
          <div className="tier-seg" role="group" aria-label="보기 방식">
            <button type="button" aria-pressed={view === 'board'} onClick={() => setView('board')}>
              티어표
            </button>
            <button type="button" aria-pressed={view === 'people'} onClick={() => setView('people')}>
              사람별
            </button>
          </div>
          {toolbar}
        </div>
      </div>

      {tierList.isPending && <p className="muted-text">불러오는 중입니다...</p>}
      <ErrorNotice error={tierList.error} />
      {tierList.isError && (
        <button type="button" className="header-button" onClick={() => tierList.refetch()}>
          다시 시도
        </button>
      )}
      {tierList.data && players.length === 0 && (
        <p className="muted-text">아직 등록된 사람이 없어요. {emptyHint}</p>
      )}
      {players.length > 0 &&
        (view === 'board' ? (
          <>
            <p className="tier-help">한 칸 안에서는 상 → 중 → 하 순서로 보여요. ▲▼는 마지막으로 티어를 바꾼 방향이에요.</p>
            <TierBoard players={players} onEdit={onEdit} />
          </>
        ) : (
          <TierPeopleTable players={players} onEdit={onEdit} onDelete={onDelete} />
        ))}
    </section>
  )
}
