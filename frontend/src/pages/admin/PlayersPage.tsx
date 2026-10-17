// 선수 관리(/admin/players): 선수 등록 폼 + 선수 카드 목록
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { createPlayer, listPlayers } from '../../api/admin'
import { PLAYERS_KEY } from '../../api/queryKeys'
import { ErrorNotice } from '../../components/ErrorNotice'
import { PlayerCard } from './PlayerCard'
import { PlayerForm } from './PlayerForm'

export function PlayersPage() {
  const queryClient = useQueryClient()
  const [creating, setCreating] = useState(false)
  const players = useQuery({ queryKey: PLAYERS_KEY, queryFn: listPlayers })

  const create = useMutation({
    mutationFn: createPlayer,
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: PLAYERS_KEY })
      setCreating(false)
    },
  })

  return (
    <section>
      <div className="page-title-row">
        <h1>선수 관리</h1>
        {!creating && (
          <button
            type="button"
            className="primary-button"
            onClick={() => {
              create.reset()
              setCreating(true)
            }}
          >
            새 선수 등록
          </button>
        )}
      </div>

      {creating && (
        <div className="panel">
          <h2>새 선수 등록</h2>
          <PlayerForm
            mode="create"
            submitLabel="등록"
            pending={create.isPending}
            serverError={create.error}
            // create 모드 검증이 실명 비어 있지 않음을 보장한다
            onSubmit={(values) => create.mutate({ ...values, realName: values.realName ?? '' })}
            onCancel={() => setCreating(false)}
          />
        </div>
      )}

      {players.isPending && <p className="muted-text">불러오는 중입니다...</p>}
      <ErrorNotice error={players.error} />
      {players.data?.length === 0 && <p className="muted-text">등록된 선수가 없습니다.</p>}
      <div className="player-list">
        {players.data?.map((player) => <PlayerCard key={player.id} player={player} />)}
      </div>
    </section>
  )
}
