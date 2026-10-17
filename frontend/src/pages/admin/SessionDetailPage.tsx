// 내전 묶음 상세(/admin/sessions/:sessionId): 대결 목록, 새 대결, 세트 등록 링크(M4)
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useParams } from 'react-router-dom'
import { createDuel, getSession } from '../../api/admin'
import { SESSIONS_KEY, sessionKey } from '../../api/queryKeys'
import { ErrorNotice } from '../../components/ErrorNotice'

export function SessionDetailPage() {
  const sessionId = Number(useParams().sessionId)
  const queryClient = useQueryClient()
  const session = useQuery({ queryKey: sessionKey(sessionId), queryFn: () => getSession(sessionId) })

  const newDuel = useMutation({
    mutationFn: () => createDuel(sessionId),
    // 상세와 목록(대결 수)이 모두 바뀌므로 SESSIONS_KEY 아래를 한꺼번에 무효화한다.
    onSuccess: () => queryClient.invalidateQueries({ queryKey: SESSIONS_KEY }),
  })

  return (
    <section>
      <p>
        <Link to="/admin">← 내전 묶음 목록</Link>
      </p>
      <h1>내전 묶음 {session.data?.startDate}</h1>
      {session.isPending && <p className="muted-text">불러오는 중입니다...</p>}
      <ErrorNotice error={session.error} />

      {session.data && (
        <>
          <div className="panel">
            <button
              type="button"
              className="primary-button"
              disabled={newDuel.isPending}
              onClick={() => newDuel.mutate()}
            >
              새 대결
            </button>
            <p className="muted-text">
              새 대결을 만들면 피어리스 제한 챔피언과 팀 구성이 새로 시작됩니다(초기화). 기존 대결은 그대로 남습니다.
            </p>
            <ErrorNotice error={newDuel.error} />
          </div>

          {session.data.duels.length === 0 && <p className="muted-text">아직 대결이 없습니다.</p>}
          <ul className="duel-list">
            {session.data.duels.map((duel) => (
              <li key={duel.id} className="duel-item">
                <strong>대결 {duel.seq}</strong>
                <Link className="header-button push-right" to={`/admin/duels/${duel.id}/sets/new`}>
                  세트 등록
                </Link>
              </li>
            ))}
          </ul>
        </>
      )}
    </section>
  )
}
