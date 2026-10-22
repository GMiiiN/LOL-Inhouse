// 초안(AI 읽기 결과)이 없는 행의 선수 칸: 등록·미등록 선수 검색 선택 + 새 미등록 선수(닉네임 입력) + 별칭 추가.
// 행의 resolution(UNRESOLVED/EXISTING/NEW) 규칙은 등록 PlayerPicker와 같다.
import { useState } from 'react'
import type { RowInput } from '../../../api/registrationTypes'
import type { Player } from '../../../api/types'
import { matchesHangul } from '../../../lib/hangulSearch'
import { knownNicknamesOf, manualAliasControl, pickExistingPlayer, pickNewPlayer, setManualNickname } from './setFormModel'

const MAX_SEARCH_RESULTS = 8

type ManualPlayerPickerProps = {
  rowLabel: string // 예: "위쪽 팀 결과창 1행"
  row: RowInput
  players: Player[] | undefined
  conflict: string | null // 저장 때 409 ALIAS_CONFLICT가 이 행의 닉네임을 지목하면 설명
  onChange: (next: RowInput) => void
}

export function ManualPlayerPicker({ rowLabel, row, players, conflict, onChange }: ManualPlayerPickerProps) {
  const [query, setQuery] = useState('')
  const selected = row.resolution === 'EXISTING' ? players?.find((p) => p.id === row.playerId) : undefined
  const known = selected ? knownNicknamesOf(selected) : []
  const alias = manualAliasControl(row, known)

  const found =
    players && query.trim()
      ? players
          .filter((p) => matchesHangul(query, p.displayName) || p.aliases.some((a) => matchesHangul(query, a.nickname)))
          .slice(0, MAX_SEARCH_RESULTS)
      : []

  const selectedName =
    row.resolution === 'NEW'
      ? '새 미등록 선수'
      : row.resolution === 'EXISTING' && row.playerId !== null
        ? `${selected?.displayName ?? `선수 #${row.playerId}`}${selected && !selected.registered ? ' (미등록)' : ''}`
        : null

  return (
    <div className="player-picker">
      <div className="player-selected" aria-label={`${rowLabel} 선택된 선수`}>
        {selectedName ? <strong>{selectedName}</strong> : <span className="muted-text">선수를 골라 주세요</span>}
      </div>

      <div className="player-search">
        <input
          type="search"
          aria-label={`${rowLabel} 선수 검색`}
          placeholder="이름 또는 닉네임 (초성 가능)"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
        />
        {players === undefined && <p className="muted-text">선수 목록을 불러오는 중입니다...</p>}
        {found.length > 0 && (
          <ul className="search-results" aria-label={`${rowLabel} 검색 결과`}>
            {found.map((player) => (
              <li key={player.id}>
                <button
                  type="button"
                  className="chip"
                  onClick={() => {
                    onChange(pickExistingPlayer(row, player))
                    setQuery('')
                  }}
                >
                  {player.displayName}
                  {!player.registered && <small> 미등록</small>}
                  {player.aliases.length > 0 && <small> ({player.aliases.map((a) => a.nickname).join(', ')})</small>}
                </button>
              </li>
            ))}
          </ul>
        )}
      </div>

      <div className="button-row">
        <button
          type="button"
          className="header-button"
          aria-pressed={row.resolution === 'NEW'}
          onClick={() => onChange(pickNewPlayer(row))}
        >
          새 미등록 선수(닉네임 입력)
        </button>
      </div>

      {(row.resolution === 'NEW' || row.resolution === 'EXISTING') && (
        <label className="nickname-input">
          <span>닉네임</span>
          <input
            type="text"
            aria-label={`${rowLabel} 닉네임`}
            value={row.nickname}
            onChange={(event) => onChange(setManualNickname(row, event.target.value, known))}
          />
        </label>
      )}

      {alias.visible && (
        <label className="alias-check">
          <input
            type="checkbox"
            checked={alias.checked}
            onChange={(event) => onChange({ ...row, addAlias: event.target.checked })}
          />
          이 닉네임을 별칭으로 추가
        </label>
      )}
      {conflict && <small className="slot-issue">{conflict}</small>}
    </div>
  )
}
