// 챔피언 한 칸(픽 또는 밴)을 고르는 위젯: 잘라낸 원본 아이콘 + 상위 3개 후보 칩 + 전체 검색.
// 피어리스 제한 챔피언은 흐리게 표시하고, 서버가 "불확실"로 표시한 칸(uncertain)은 강조한다.
// 설계: docs/design/m4-design.md §6 ChampionPicker
import { useState } from 'react'
import type { Box4, ChampionGuess, ChampionInfo } from '../../../api/registrationTypes'
import { IconCrop } from './IconCrop'

export const MAX_SEARCH_RESULTS = 8

type ChampionPickerProps = {
  label: string // 접근성 이름에 쓰는 칸 이름(예: "위쪽 팀 탑 챔피언")
  value: string | null
  guess: ChampionGuess | null
  box: Box4 | null
  imageUrl: string | null
  champions: ChampionInfo[]
  championsById: ReadonlyMap<string, ChampionInfo>
  restricted: ReadonlySet<string>
  issues: string[]
  onChange: (championKey: string | null) => void
}

function ChampionIcon({ champion }: { champion: ChampionInfo | undefined }) {
  if (!champion) return <span className="champion-icon champion-icon-empty" aria-hidden="true" />
  return <img className="champion-icon" src={champion.iconUrl} alt="" loading="lazy" />
}

export function ChampionPicker({
  label,
  value,
  guess,
  box,
  imageUrl,
  champions,
  championsById,
  restricted,
  issues,
  onChange,
}: ChampionPickerProps) {
  const [query, setQuery] = useState('')
  const selected = value ? championsById.get(value) : undefined
  const uncertain = guess?.uncertain ?? false
  const normalizedQuery = query.trim().toLowerCase()
  const results = normalizedQuery
    ? champions
        .filter((c) => c.name.toLowerCase().includes(normalizedQuery) || c.id.toLowerCase().includes(normalizedQuery))
        .slice(0, MAX_SEARCH_RESULTS)
    : []

  function pick(id: string) {
    onChange(id)
    setQuery('')
  }

  return (
    <div className={`champion-picker${uncertain ? ' uncertain' : ''}${issues.length > 0 ? ' has-issue' : ''}`}>
      <div className="champion-main">
        {box && guess && !guess.cropFailed ? (
          <IconCrop imageUrl={imageUrl} box={box} label={`${label} 원본`} />
        ) : (
          guess?.cropFailed && <span className="crop-failed">잘라내기 실패</span>
        )}
        <span className="champion-arrow" aria-hidden="true">
          →
        </span>
        <span className="champion-current" aria-label={`${label} 선택값`}>
          <ChampionIcon champion={selected} />
          <span className="champion-name">{selected ? selected.name : value ?? '미선택'}</span>
        </span>
      </div>
      {uncertain && <span className="badge badge-warn badge-block">확인 필요</span>}

      {guess && guess.candidates.length > 0 && (
        <div className="champion-chips" role="group" aria-label={`${label} 후보`}>
          {guess.candidates.slice(0, 3).map((candidate) => {
            const champion = championsById.get(candidate.id)
            const isRestricted = restricted.has(candidate.id)
            return (
              <button
                key={candidate.id}
                type="button"
                className={`chip${isRestricted ? ' restricted' : ''}`}
                aria-pressed={value === candidate.id}
                onClick={() => pick(candidate.id)}
              >
                <ChampionIcon champion={champion} />
                {champion?.name ?? candidate.id}
                {isRestricted && <small> 피어리스</small>}
              </button>
            )
          })}
        </div>
      )}

      <div className="champion-search">
        <input
          type="search"
          aria-label={`${label} 검색`}
          placeholder="챔피언 검색"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
        />
        {results.length > 0 && (
          <ul className="search-results" aria-label={`${label} 검색 결과`}>
            {results.map((champion) => (
              <li key={champion.id}>
                <button
                  type="button"
                  className={`chip${restricted.has(champion.id) ? ' restricted' : ''}`}
                  onClick={() => pick(champion.id)}
                >
                  <ChampionIcon champion={champion} />
                  {champion.name}
                  {restricted.has(champion.id) && <small> 피어리스</small>}
                </button>
              </li>
            ))}
          </ul>
        )}
      </div>

      {issues.map((issue) => (
        <small key={issue} className="slot-issue">
          {issue}
        </small>
      ))}
    </div>
  )
}
