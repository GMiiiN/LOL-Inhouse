// 내전 관리(/admin): 내전 묶음 목록(서버가 시작일 내림차순으로 준다) + 새 묶음 만들기
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type SyntheticEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { createSession, deleteSession, listSessions } from '../../api/admin'
import { ApiError } from '../../api/client'
import { PUBLIC_KEY, SESSIONS_KEY } from '../../api/queryKeys'
import { ErrorNotice } from '../../components/ErrorNotice'

export function SessionsPage() {
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const [startDate, setStartDate] = useState('')
  const sessions = useQuery({
    queryKey: SESSIONS_KEY,
    queryFn: listSessions,
    // 서버가 이미 내림차순으로 주지만 화면에서도 최신 날짜가 위에 오도록 보장한다
    select: (rows) => [...rows].sort((a, b) => b.startDate.localeCompare(a.startDate)),
  })

  const create = useMutation({
    mutationFn: createSession,
    onSuccess: async (created) => {
      await queryClient.invalidateQueries({ queryKey: SESSIONS_KEY })
      navigate(`/admin/sessions/${created.id}`)
    },
  })

  const remove = useMutation({
    mutationFn: deleteSession,
    onSuccess: () =>
      Promise.all([
        queryClient.invalidateQueries({ queryKey: SESSIONS_KEY }),
        queryClient.invalidateQueries({ queryKey: PUBLIC_KEY }),
      ]),
  })

  function handleDelete(id: number, startDate: string) {
    if (!window.confirm(`${startDate} 내전 묶음을 삭제할까요? 되돌릴 수 없어요.`)) {
      return
    }
    remove.mutate(id)
  }

  function handleSubmit(event: SyntheticEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!startDate) return
    create.mutate(startDate)
  }

  const conflict =
    create.error instanceof ApiError && create.error.code === 'SESSION_DATE_CONFLICT' ? create.error : null

  return (
    <section>
      <h1>내전·대결</h1>

      <div className="panel">
        <h2>새 내전 묶음</h2>
        <form className="inline-form" onSubmit={handleSubmit} noValidate>
          <label>
            시작일
            <input type="date" value={startDate} onChange={(e) => setStartDate(e.target.value)} />
          </label>
          <button type="submit" className="primary-button" disabled={!startDate || create.isPending}>
            묶음 만들기
          </button>
        </form>
        <p className="muted-text">날짜를 직접 골라야 만들 수 있어요(실수 방지). 자정이 지나 이어진 내전도 시작한 날짜의 묶음에 넣습니다.</p>
        {conflict ? (
          <div className="error-notice" role="alert">
            <p>이 시작일의 내전 묶음이 이미 있습니다.</p>
            {conflict.existingSessionId !== undefined && (
              <p>
                <Link to={`/admin/sessions/${conflict.existingSessionId}`}>기존 묶음으로 이동</Link>
              </p>
            )}
          </div>
        ) : (
          <ErrorNotice error={create.error} />
        )}
      </div>

      <h2>내전 묶음 목록</h2>
      {sessions.isPending && <p className="muted-text">불러오는 중입니다...</p>}
      <ErrorNotice error={sessions.error} />
      <ErrorNotice error={remove.error} />
      {sessions.data?.length === 0 && <p className="muted-text">아직 내전 묶음이 없습니다.</p>}
      {sessions.data && sessions.data.length > 0 && (
        <table className="data-table data-table-large">
          <thead>
            <tr>
              <th>시작일</th>
              <th>대결 수</th>
              <th>세트 수</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {sessions.data.map((session) => (
              <tr key={session.id}>
                <td>{session.startDate}</td>
                <td>{session.duelCount}</td>
                <td>{session.activeSetCount ?? session.setCount}</td>
                <td>
                  <Link to={`/admin/sessions/${session.id}`}>열기</Link>
                  {session.setCount === 0 && (
                    <button
                      type="button"
                      className="header-button tier-danger"
                      aria-label={`${session.startDate} 묶음 삭제`}
                      disabled={remove.isPending}
                      onClick={() => handleDelete(session.id, session.startDate)}
                    >
                      삭제
                    </button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  )
}
