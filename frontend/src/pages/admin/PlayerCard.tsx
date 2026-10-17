// 선수 한 명의 카드: 표시 이름·상태, 닉네임(별칭) 추가·삭제, 수정(미등록이면 실명 입력으로 등록 전환).
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState, type SyntheticEvent } from 'react'
import { addAlias, deleteAlias, updatePlayer } from '../../api/admin'
import { PLAYERS_KEY } from '../../api/queryKeys'
import { POSITION_LABEL, type Player, type PlayerUpdateRequest } from '../../api/types'
import { ErrorNotice } from '../../components/ErrorNotice'
import { PlayerForm } from './PlayerForm'
import { validateNickname } from './playerFormValidation'

export function PlayerCard({ player }: { player: Player }) {
  const queryClient = useQueryClient()
  const [editing, setEditing] = useState(false)
  const [newNickname, setNewNickname] = useState('')
  const [nicknameError, setNicknameError] = useState<string>()

  const refresh = () => queryClient.invalidateQueries({ queryKey: PLAYERS_KEY })

  const update = useMutation({
    mutationFn: (values: PlayerUpdateRequest) => updatePlayer(player.id, values),
    onSuccess: async () => {
      await refresh()
      setEditing(false)
    },
  })

  const add = useMutation({
    mutationFn: (nickname: string) => addAlias(player.id, nickname),
    onSuccess: async () => {
      setNewNickname('')
      await refresh()
    },
  })

  const remove = useMutation({
    mutationFn: (aliasId: number) => deleteAlias(player.id, aliasId),
    onSuccess: refresh,
  })

  function handleAdd(event: SyntheticEvent<HTMLFormElement>) {
    event.preventDefault()
    const message = validateNickname(newNickname)
    setNicknameError(message)
    if (!message) add.mutate(newNickname.trim())
  }

  return (
    <article className="player-card">
      <header className="player-card-header">
        <h2>{player.displayName}</h2>
        {player.registered ? (
          <span className="chip">등록</span>
        ) : (
          <span className="chip chip-warn">미등록</span>
        )}
        {player.mainPosition && <span className="chip">{POSITION_LABEL[player.mainPosition]}</span>}
        <button type="button" className="header-button push-right" onClick={() => setEditing(!editing)}>
          {player.registered ? '수정' : '실명 입력해 등록'}
        </button>
      </header>

      <ul className="alias-list" aria-label={`${player.displayName} 닉네임`}>
        {player.aliases.map((alias) => (
          <li key={alias.id} className="alias-item">
            <span>{alias.nickname}</span>
            <button
              type="button"
              className="alias-delete"
              aria-label={`${alias.nickname} 삭제`}
              disabled={remove.isPending}
              onClick={() => remove.mutate(alias.id)}
            >
              삭제
            </button>
          </li>
        ))}
      </ul>
      <ErrorNotice error={remove.error} />

      <form className="inline-form" onSubmit={handleAdd} noValidate>
        <input
          type="text"
          aria-label={`${player.displayName} 닉네임 추가`}
          placeholder="닉네임 추가"
          value={newNickname}
          onChange={(e) => setNewNickname(e.target.value)}
        />
        <button type="submit" className="header-button" disabled={add.isPending}>
          추가
        </button>
      </form>
      {nicknameError && <p className="field-error">{nicknameError}</p>}
      <ErrorNotice error={add.error} />

      {editing && (
        <div className="player-edit">
          <PlayerForm
            mode={player.registered ? 'edit-registered' : 'edit-unregistered'}
            initial={{ realName: player.realName ?? '', mainPosition: player.mainPosition ?? '' }}
            submitLabel={player.registered ? '저장' : '등록'}
            pending={update.isPending}
            serverError={update.error}
            onSubmit={(values) => update.mutate({ realName: values.realName, mainPosition: values.mainPosition })}
            onCancel={() => setEditing(false)}
          />
        </div>
      )}
    </article>
  )
}
