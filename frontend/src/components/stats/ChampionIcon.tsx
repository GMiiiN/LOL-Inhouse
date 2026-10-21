// 챔피언 아이콘 한 칸. 공개 카탈로그(이름·아이콘)를 쓰고, 카탈로그에 없으면 키 앞 글자로 대신한다.
import '../../styles/stats-common.css'
import { useChampionLookup } from './useChampionCatalog'

export type ChampionIconSize = 'sm' | 'md' | 'lg'

type ChampionIconProps = {
  championKey: string
  size?: ChampionIconSize // 기본 md(32px), sm 24px, lg 44px
  showName?: boolean // true면 아이콘 옆에 한글 이름(없으면 키)
}

export function ChampionIcon({ championKey, size = 'md', showName = false }: ChampionIconProps) {
  const champion = useChampionLookup()(championKey)
  const name = champion?.name ?? championKey
  const sizeClass = size === 'md' ? '' : ` ${size}`
  const icon = (
    <span className={`ci${sizeClass}`} title={name} role="img" aria-label={name}>
      {champion ? (
        <img src={champion.iconUrl} alt="" loading="lazy" />
      ) : (
        <span aria-hidden="true">{championKey.slice(0, 2)}</span>
      )}
    </span>
  )
  if (!showName) return icon
  return (
    <span className="ci-with-name">
      {icon}
      <span>{name}</span>
    </span>
  )
}
