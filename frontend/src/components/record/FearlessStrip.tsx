// 피어리스 제한 챔피언 줄. "이 세트 시점 제한 N개"로 접어 두고 펼치면 아이콘을 보여 준다.
import { ChampionIcon } from '../stats/ChampionIcon'
import '../../styles/records.css'

type FearlessStripProps = {
  championKeys: string[]
  title?: string // 기본 "이 세트 시점 제한"
  defaultOpen?: boolean
}

export function FearlessStrip({ championKeys, title = '이 세트 시점 제한', defaultOpen = false }: FearlessStripProps) {
  return (
    <details className="fearless-strip" open={defaultOpen}>
      <summary>
        {title} {championKeys.length}개
      </summary>
      {championKeys.length > 0 && (
        <div className="fearless-icons">
          {championKeys.map((key) => (
            <ChampionIcon key={key} championKey={key} size="sm" />
          ))}
        </div>
      )}
    </details>
  )
}
