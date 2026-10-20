// 내전 묶음 상세(/admin/sessions/:sessionId): 대결 목록, 새 대결(피어리스 방식 선택), 세트 등록 링크
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { createDuel, getSession } from '../../api/admin'
import { SESSIONS_KEY, sessionKey } from '../../api/queryKeys'
import type { FearlessMode } from '../../api/registrationTypes'
import { ErrorNotice } from '../../components/ErrorNotice'

const MODE_LABEL: Record<FearlessMode, string> = { RESET: '피어리스 초기화', CONTINUE: '피어리스 이어가기' }

export function SessionDetailPage() {
  const sessionId = Number(useParams().sessionId)
  const queryClient = useQueryClient()
  const session = useQuery({ queryKey: sessionKey(sessionId), queryFn: () => getSession(sessionId) })
  const [mode, setMode] = useState<FearlessMode>('RESET')

  const newDuel = useMutation({
    mutationFn: (fearlessMode: FearlessMode) => createDuel(sessionId, fearlessMode),
    // 상세와 목록(대결 수)이 모두 바뀌므로 SESSIONS_KEY 아래를 한꺼번에 무효화한다.
    onSuccess: () => queryClient.invalidateQueries({ queryKey: SESSIONS_KEY }),
  })

  const isFirstDuel = session.data?.duels.length === 0
  // 첫 대결은 이어갈 이전 대결이 없으므로 항상 초기화
  const effectiveMode: FearlessMode = isFirstDuel ? 'RESET' : mode

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
            {!isFirstDuel && (
              <fieldset className="choice-group">
                <legend>피어리스 방식</legend>
                {(['RESET', 'CONTINUE'] as const).map((candidate) => (
                  <label key={candidate}>
                    <input
                      type="radio"
                      name="fearless-mode"
                      value={candidate}
                      checked={mode === candidate}
                      onChange={() => setMode(candidate)}
                    />
                    {candidate === 'RESET' ? '초기화' : '이어가기'}
                  </label>
                ))}
              </fieldset>
            )}
            <button
              type="button"
              className="primary-button"
              disabled={newDuel.isPending}
              onClick={() => newDuel.mutate(effectiveMode)}
            >
              새 대결
            </button>
            <p className="muted-text">
              {isFirstDuel
                ? '첫 대결은 피어리스 제한 없이 시작합니다(초기화).'
                : effectiveMode === 'RESET'
                  ? '초기화: 피어리스 제한 챔피언과 점수가 0부터 새로 시작됩니다.'
                  : '이어가기: 점수는 0부터 시작하지만 마지막 초기화 이후 픽한 챔피언은 계속 제한됩니다.'}{' '}
              기존 대결은 그대로 남습니다.
            </p>
            <ErrorNotice error={newDuel.error} />
          </div>

          {session.data.duels.length === 0 && <p className="muted-text">아직 대결이 없습니다.</p>}
          <ul className="duel-list">
            {session.data.duels.map((duel) => (
              <li key={duel.id} className="duel-item">
                <strong>대결 {duel.seq}</strong>
                {duel.fearlessMode && <span className="badge badge-auto">{MODE_LABEL[duel.fearlessMode]}</span>}
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
