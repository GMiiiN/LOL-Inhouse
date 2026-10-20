// 한 행의 선수를 확정하는 위젯. 읽은 닉네임, 상태 배지, 후보 라디오, 전체 선수 검색, "새 미등록 선수로", 별칭 추가 체크박스.
// 행의 resolution(UNRESOLVED/EXISTING/NEW)을 항상 명시해서 "아직 안 골랐음"과 "새로 만들기"를 구분한다(설계 §11.2-1).
// 설계: docs/design/m4-design.md §6 PlayerPicker
import { useState } from 'react'
import type { DraftRow, RowInput } from '../../../api/registrationTypes'
import type { Player } from '../../../api/types'
import {
  aliasControl,
  playerStatus,
  selectExistingPlayer,
  selectNewPlayer,
  setRowNickname,
} from './reviewModel'

const MAX_SEARCH_RESULTS = 6

type PlayerPickerProps = {
  rowLabel: string // 예: "위쪽 팀 결과창 1행"
  row: RowInput
  draftRow: DraftRow
  players: Player[] | undefined
  conflict: string | null // 저장 때 409 ALIAS_CONFLICT가 이 행의 닉네임을 지목하면 설명
  onChange: (next: RowInput) => void
}

function playerNicknames(players: Player[] | undefined, playerId: number): string[] {
  return players?.find((p) => p.id === playerId)?.aliases.map((a) => a.nickname) ?? []
}

export function PlayerPicker({ rowLabel, row, draftRow, players, conflict, onChange }: PlayerPickerProps) {
  const [searching, setSearching] = useState(false)
  const [query, setQuery] = useState('')
  const { match } = draftRow
  const status = playerStatus(row, draftRow)
  const knownNicknames = row.playerId === null ? [] : playerNicknames(players, row.playerId)
  const alias = aliasControl(row, draftRow, knownNicknames)

  const normalizedQuery = query.trim().toLowerCase()
  const found =
    players && normalizedQuery
      ? players
          .filter(
            (p) =>
              p.displayName.toLowerCase().includes(normalizedQuery) ||
              p.aliases.some((a) => a.nickname.toLowerCase().includes(normalizedQuery)),
          )
          .slice(0, MAX_SEARCH_RESULTS)
      : []

  const selectedName = (() => {
    if (row.resolution === 'NEW') return `${row.nickname} (새 미등록 선수)`
    if (row.resolution !== 'EXISTING' || row.playerId === null) return null
    const fromList = players?.find((p) => p.id === row.playerId)?.displayName
    const fromCandidate = match.candidates.find((c) => c.playerId === row.playerId)?.displayName
    const fromMatch = match.playerId === row.playerId ? match.playerDisplayName : null
    return fromList ?? fromCandidate ?? fromMatch ?? `선수 #${row.playerId}`
  })()

  function chooseExisting(playerId: number, extraNicknames: string[] = []) {
    onChange(selectExistingPlayer(row, draftRow, { playerId, nicknames: [...extraNicknames, ...playerNicknames(players, playerId)] }))
  }

  const showNicknameInput = draftRow.truncated || row.resolution === 'NEW'

  return (
    <div className="player-picker">
      <div className="player-read">
        <span className="muted-text">읽은 값</span> <code>{draftRow.readNickname}</code>
        <span className={`badge badge-${status.tone}`}>{status.label}</span>
      </div>
      {selectedName && (
        <div className="player-selected">
          <strong>{selectedName}</strong>
        </div>
      )}

      {match.candidates.length > 0 && (
        <div role="radiogroup" aria-label={`${rowLabel} 후보 선수`} className="candidate-list">
          {match.candidates.map((candidate) => (
            <label key={candidate.playerId}>
              <input
                type="radio"
                name={`candidate-${rowLabel}`}
                value={candidate.playerId}
                checked={row.resolution === 'EXISTING' && row.playerId === candidate.playerId}
                onChange={() => chooseExisting(candidate.playerId, [candidate.nickname])}
              />
              {candidate.displayName} ({candidate.nickname} · {Math.round(candidate.similarity * 100)}%)
            </label>
          ))}
        </div>
      )}

      {showNicknameInput && (
        <label className="nickname-input">
          <span>{draftRow.truncated ? '전체 닉네임(잘렸음)' : '닉네임'}</span>
          <input
            type="text"
            aria-label={`${rowLabel} 전체 닉네임`}
            value={row.nickname}
            onChange={(event) => onChange(setRowNickname(row, draftRow, event.target.value))}
          />
        </label>
      )}

      <div className="button-row">
        <button type="button" className="header-button" onClick={() => setSearching((open) => !open)}>
          전체 선수에서 찾기
        </button>
        <button
          type="button"
          className="header-button"
          aria-pressed={row.resolution === 'NEW'}
          onClick={() => onChange(selectNewPlayer(row))}
        >
          새 미등록 선수로
        </button>
      </div>

      {searching && (
        <div className="player-search">
          <input
            type="search"
            aria-label={`${rowLabel} 선수 검색`}
            placeholder="이름 또는 닉네임"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
          />
          {players === undefined && <p className="muted-text">선수 목록을 불러오는 중입니다...</p>}
          <ul className="search-results">
            {found.map((player) => (
              <li key={player.id}>
                <button
                  type="button"
                  className="chip"
                  onClick={() => {
                    chooseExisting(player.id)
                    setSearching(false)
                    setQuery('')
                  }}
                >
                  {player.displayName}
                  {player.aliases.length > 0 && <small> ({player.aliases.map((a) => a.nickname).join(', ')})</small>}
                </button>
              </li>
            ))}
          </ul>
        </div>
      )}

      {alias.visible && (
        <label className="alias-check">
          <input
            type="checkbox"
            checked={alias.checked}
            disabled={alias.disabled}
            onChange={(event) => onChange({ ...row, addAlias: event.target.checked })}
          />
          이 닉네임을 별칭으로 추가
        </label>
      )}
      {alias.hint && <small className="muted-text">{alias.hint}</small>}
      {conflict && <small className="slot-issue">{conflict}</small>}
    </div>
  )
}
