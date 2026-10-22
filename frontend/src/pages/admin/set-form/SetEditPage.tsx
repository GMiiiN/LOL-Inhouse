// 저장된 세트 수정(/admin/sets/:setId/edit): 폼·챔피언 목록을 불러와 SetFormPage에 넘기고, 저장하면 캐시를 무효화한 뒤 세트 상세로 보낸다.
// 설계: docs/design/m6-design.md §9.2
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useNavigate, useParams } from 'react-router-dom'
import {
  CHAMPIONS_KEY,
  DELETED_SETS_KEY,
  PLAYER_PROFILES_KEY,
  PLAYERS_KEY,
  PUBLIC_KEY,
  registrationContextKey,
  SESSIONS_KEY,
  setFormKey,
  TIER_RECOMMENDATIONS_KEY,
} from '../../../api/queryKeys'
import { checkSetEdit, getSetForm, updateSet } from '../../../api/recordEdit'
import type { SetEditSavedState } from '../../../api/recordEditTypes'
import { listChampions } from '../../../api/registration'
import { ErrorNotice } from '../../../components/ErrorNotice'
import { OriginalImageButton } from '../../set/OriginalImageButton'
import { SetFormPage } from './SetFormPage'
import { buildEditRequest, formFromRequest, type FormState } from './setFormModel'

export function SetEditPage() {
  const setId = Number(useParams().setId)
  const valid = Number.isInteger(setId) && setId > 0
  const queryClient = useQueryClient()
  const navigate = useNavigate()

  // 편집 중에 다시 불러와 입력을 덮지 않도록 한 번만 받는다("새로 불러오기"만 다시 받는다).
  const form = useQuery({
    queryKey: setFormKey(setId),
    queryFn: () => getSetForm(setId),
    enabled: valid,
    staleTime: Infinity,
    gcTime: 0,
    retry: false,
  })
  const champions = useQuery({ queryKey: CHAMPIONS_KEY, queryFn: listChampions, staleTime: Infinity })

  if (!valid) return <p className="muted-text">잘못된 주소예요.</p>
  const data = form.data

  const save = async (state: FormState) => {
    if (!data) return
    const result = await updateSet(setId, buildEditRequest(state, data.updatedAt))
    const keys: (readonly unknown[])[] = [PUBLIC_KEY, SESSIONS_KEY, registrationContextKey(data.duelId), DELETED_SETS_KEY, TIER_RECOMMENDATIONS_KEY]
    if (result.createdPlayers.length > 0 || result.addedAliases.length > 0) keys.push(PLAYERS_KEY, PLAYER_PROFILES_KEY)
    await Promise.all(keys.map((queryKey) => queryClient.invalidateQueries({ queryKey })))
    const saved: SetEditSavedState = { editWarnings: result.warnings, fearlessConflicts: result.fearlessConflicts }
    navigate(`/sets/${setId}`, { state: saved })
  }

  return (
    <section className="register-page">
      <p>
        {data ? <Link to={`/admin/sessions/${data.sessionId}`}>← 내전 묶음으로</Link> : <Link to="/admin">← 내전·대결</Link>}
      </p>
      <h1>세트 수정</h1>
      {form.isPending && form.fetchStatus !== 'idle' && <p className="muted-text">불러오는 중입니다...</p>}
      <ErrorNotice error={form.error} />
      {form.isError && (
        <button type="button" className="header-button" onClick={() => void form.refetch()}>
          다시 불러오기
        </button>
      )}
      <ErrorNotice error={champions.error} />

      {data && (
        <div className="panel register-head">
          <div className="page-title-row">
            <h2>
              대결 {data.duelSeq} · {data.setNo}세트 수정
            </h2>
            {data.hasImage && !data.deleted && <OriginalImageButton setId={data.setId} />}
          </div>
          <p className="muted-text">내전 묶음 {data.sessionStartDate} · 세트 번호와 대결은 바꿀 수 없어요.</p>
        </div>
      )}

      {data?.deleted && (
        <div className="warn-notice" role="status">
          <p>
            삭제된 세트예요. 먼저 복구하세요. 복구하면 다시 고칠 수 있어요. <Link to="/admin/history">기록 관리로</Link>
          </p>
        </div>
      )}

      {data && !data.deleted && champions.data && (
        <SetFormPage
          key={form.dataUpdatedAt}
          initial={formFromRequest(data.form)}
          initialEvaluation={data.evaluation}
          restrictedChampions={data.restrictedChampions}
          champions={champions.data}
          onCheck={(state) => checkSetEdit(setId, buildEditRequest(state, data.updatedAt))}
          onSave={save}
          onReload={() => void form.refetch()}
        />
      )}
      {data && !data.deleted && !champions.data && !champions.isError && <p className="muted-text">챔피언 목록을 불러오는 중입니다...</p>}
    </section>
  )
}
