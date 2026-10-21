// 선수 관리(/admin/players): 티어리스트 + 편집 모드 + 사람 추가 + 미등록 선수 목록. 편집기는 목록 위에 열린다.
// 프로필을 바꾸면 티어리스트·프로필 목록·M4 등록 화면의 선수 목록(PLAYERS_KEY)을 모두 새로 불러온다.
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { createProfile, deleteProfile, listProfiles, updateProfile } from '../../../api/tier'
import { PLAYERS_KEY, PLAYER_PROFILES_KEY, TIER_LIST_KEY } from '../../../api/queryKeys'
import type { PlayerProfileRequest } from '../../../api/tierTypes'
import { ErrorNotice } from '../../../components/ErrorNotice'
import { TierListPanel } from '../../tier/TierListPanel'
import { compareNames } from '../../tier/tierSort'
import { PlayerEditor } from './PlayerEditor'

type Editing = { kind: 'new' } | { kind: 'profile'; id: number }

export function PlayerTierAdminPage() {
  const queryClient = useQueryClient()
  const [editMode, setEditMode] = useState(false)
  const [editing, setEditing] = useState<Editing | null>(null)
  const profiles = useQuery({ queryKey: PLAYER_PROFILES_KEY, queryFn: listProfiles })

  const refreshAll = () =>
    Promise.all([
      queryClient.invalidateQueries({ queryKey: PLAYER_PROFILES_KEY }),
      queryClient.invalidateQueries({ queryKey: TIER_LIST_KEY }),
      queryClient.invalidateQueries({ queryKey: PLAYERS_KEY }),
    ])

  const save = useMutation({
    mutationFn: (request: PlayerProfileRequest) =>
      editing?.kind === 'profile' ? updateProfile(editing.id, request) : createProfile(request),
    onSuccess: async () => {
      await refreshAll()
      setEditing(null)
    },
  })

  const remove = useMutation({
    mutationFn: deleteProfile,
    onSuccess: async (_data, id) => {
      await refreshAll()
      setEditing((current) => (current?.kind === 'profile' && current.id === id ? null : current))
    },
  })

  function open(next: Editing) {
    save.reset()
    remove.reset()
    setEditing(next)
  }

  const all = profiles.data ?? []
  const target = editing?.kind === 'profile' ? (all.find((p) => p.id === editing.id) ?? null) : null
  const unregistered = all.filter((p) => !p.registered).sort((a, b) => compareNames(a.displayName, b.displayName))
  const otherRealNames = all.filter((p) => p.id !== target?.id && p.realName).map((p) => p.realName as string)

  return (
    <section>
      <div className="page-title-row">
        <h1>선수 관리</h1>
      </div>

      {editing?.kind === 'new' && (
        <PlayerEditor
          key="new"
          profile={null}
          otherRealNames={otherRealNames}
          pending={save.isPending}
          serverError={save.error}
          onSubmit={(request) => save.mutate(request)}
          onCancel={() => setEditing(null)}
        />
      )}
      {editing?.kind === 'profile' &&
        (target ? (
          <PlayerEditor
            key={target.id}
            profile={target}
            otherRealNames={otherRealNames}
            pending={save.isPending}
            serverError={save.error ?? remove.error}
            onSubmit={(request) => save.mutate(request)}
            onCancel={() => setEditing(null)}
            onDelete={() => remove.mutate(target.id)}
            deleting={remove.isPending}
          />
        ) : (
          <p className="muted-text">선수 정보를 불러오는 중입니다...</p>
        ))}

      <ErrorNotice error={profiles.error} />
      {editing === null && <ErrorNotice error={remove.error} />}

      <TierListPanel
        onEdit={editMode ? (id) => open({ kind: 'profile', id }) : undefined}
        onDelete={editMode ? (id) => remove.mutate(id) : undefined}
        emptyHint="사람 추가를 눌러 이름과 라인별 티어를 넣어 주세요."
        toolbar={
          <>
            <button
              type="button"
              className="header-button"
              aria-pressed={editMode}
              onClick={() => setEditMode(!editMode)}
            >
              편집 모드
            </button>
            <button type="button" className="primary-button" onClick={() => open({ kind: 'new' })}>
              사람 추가
            </button>
          </>
        }
      />

      {unregistered.length > 0 && (
        <details className="panel tier-unregistered">
          <summary>미등록 선수 {unregistered.length}명</summary>
          <p className="tier-help">
            결과창 등록 중에 자동으로 만들어진 선수예요. 실명과 라인 티어를 넣으면 등록 선수가 되고, 지금까지의 전적은 그대로 이어져요.
          </p>
          <ul className="tier-unregistered-list">
            {unregistered.map((p) => (
              <li key={p.id}>
                <button
                  type="button"
                  className="header-button"
                  onClick={() => open({ kind: 'profile', id: p.id })}
                >
                  {p.displayName}
                </button>
              </li>
            ))}
          </ul>
        </details>
      )}
    </section>
  )
}
