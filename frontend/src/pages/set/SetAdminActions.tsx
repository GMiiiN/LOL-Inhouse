// 세트 상세의 담당자 전용 동작(수정·삭제). 방문자에게는 아무것도 그리지 않는다.
import { Link, useNavigate } from 'react-router-dom'
import { useAdminMe } from '../../auth/useAdminMe'
import { ErrorNotice } from '../../components/ErrorNotice'
import { useDeleteSet } from '../admin/history/useSetDeleteRestore'

type Props = { setId: number; sessionId: number; duelId: number; duelSeq: number; setNo: number }

export function SetAdminActions({ setId, sessionId, duelId, duelSeq, setNo }: Props) {
  const me = useAdminMe()
  const navigate = useNavigate()
  const remove = useDeleteSet()

  if (!me.data) return null

  const onDelete = () => {
    if (!window.confirm(`대결 ${duelSeq} · ${setNo}세트를 삭제할까요? 기록 관리에서 복구할 수 있어요.`)) return
    remove.mutate({ setId, duelId }, { onSuccess: () => navigate(`/admin/sessions/${sessionId}`) })
  }

  return (
    <div className="set-admin-actions">
      <Link to={`/admin/sets/${setId}/edit`}>수정</Link>
      <button type="button" onClick={onDelete} disabled={remove.isPending}>
        삭제
      </button>
      <ErrorNotice error={remove.error} />
    </div>
  )
}
