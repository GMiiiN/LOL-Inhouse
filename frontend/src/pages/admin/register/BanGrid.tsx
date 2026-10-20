// 한 팀의 밴 5칸. 결과창 배치(윗줄 3칸, 아랫줄 2칸)를 따르고 칸마다 턴 라벨(B1~R5)을 붙인다.
// 라벨은 위쪽 팀 진영으로 계산한 표시용이고, 턴 확정은 서버가 한다. 10칸 모두 필수이며 1순위로 미리 채워진다.
import type { ChampionInfo, DraftBan } from '../../../api/registrationTypes'
import { banKey, setBan, type ReviewState, type TeamKey } from './reviewModel'
import { ChampionPicker } from './ChampionPicker'

type BanGridProps = {
  teamKey: TeamKey
  teamLabel: string
  bans: (string | null)[]
  draftBans: DraftBan[]
  labels: string[] // 길이 5, 화면 순서
  imageUrl: string | null
  champions: ChampionInfo[]
  championsById: ReadonlyMap<string, ChampionInfo>
  issues: Record<string, string[]>
  onUpdate: (change: (state: ReviewState) => ReviewState) => void
}

const EMPTY_SET: ReadonlySet<string> = new Set()

export function BanGrid({
  teamKey,
  teamLabel,
  bans,
  draftBans,
  labels,
  imageUrl,
  champions,
  championsById,
  issues,
  onUpdate,
}: BanGridProps) {
  return (
    <div className="ban-grid" role="group" aria-label={`${teamLabel} 밴`}>
      {bans.map((ban, index) => {
        const slot = index + 1
        const draft = draftBans.find((d) => d.slot === slot)
        return (
          <div key={slot} className={`ban-slot ban-slot-${slot <= 3 ? 'upper' : 'lower'}`}>
            <strong className="ban-label">{labels[index]}</strong>
            <ChampionPicker
              label={`${teamLabel} 밴 ${labels[index]}`}
              value={ban}
              guess={draft?.champion ?? null}
              box={draft?.box ?? null}
              imageUrl={imageUrl}
              champions={champions}
              championsById={championsById}
              restricted={EMPTY_SET}
              issues={issues[banKey(teamKey, slot)] ?? []}
              onChange={(id) => onUpdate((state) => setBan(state, teamKey, slot, id))}
            />
          </div>
        )
      })}
    </div>
  )
}
