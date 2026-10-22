// 저장된 세트 수정 폼(수정 화면 SetEditPage가 쓰고, 직접 입력 화면도 쓸 수 있게 검사·저장 동작은 props로 받는다).
// 경기 정보 + 팀 표(초안 없음) + 오류·경고 + 뒤 세트 피어리스 충돌. 검사는 600ms 디바운스, 저장 실패는 입력이 바뀌면 사라진다.
// 설계: docs/design/m6-design.md §3.2·§9.2
import { useMutation, useQuery } from '@tanstack/react-query'
import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { listPlayers } from '../../../api/admin'
import { ApiError } from '../../../api/client'
import { PLAYERS_KEY } from '../../../api/queryKeys'
import type { EditEvaluation } from '../../../api/recordEditTypes'
import type { ChampionList, DuelTeam } from '../../../api/registrationTypes'
import { ErrorNotice } from '../../../components/ErrorNotice'
import { ReviewIssuesList } from '../register/ReviewIssuesList'
import { TeamTable } from '../register/TeamTable'
import { useDebouncedCheck } from '../register/useDebouncedCheck'
import {
  banLabels,
  canSave,
  issuesFromError,
  issuesFromEvaluation,
  slotIssues,
  TEAM_LABEL,
} from '../register/reviewModel'
import { cleanFormState, isDurationValid, type FormState } from './setFormModel'

const SIDE_LABEL = { BLUE: '블루', RED: '레드' } as const

type SetFormPageProps = {
  initial: FormState
  initialEvaluation: EditEvaluation
  restrictedChampions: string[] // 이 세트에 적용되는 피어리스 제한(앞 세트 픽)
  champions: ChampionList
  onCheck: (state: FormState) => Promise<EditEvaluation>
  onSave: (state: FormState) => Promise<void> // 성공하면 호출한 쪽이 캐시를 무효화하고 화면을 옮긴다. 실패는 던진다.
  onReload: () => void // SET_MODIFIED 때 "새로 불러오기"
}

export function SetFormPage({
  initial,
  initialEvaluation,
  restrictedChampions,
  champions,
  onCheck,
  onSave,
  onReload,
}: SetFormPageProps) {
  const [state, setState] = useState<FormState>(() => initial)
  const [saveFailure, setSaveFailure] = useState<{ key: string; error: unknown } | null>(null)

  const players = useQuery({ queryKey: PLAYERS_KEY, queryFn: listPlayers })
  const championsById = useMemo(() => new Map(champions.champions.map((c) => [c.id, c])), [champions])
  const restricted = useMemo(() => new Set(restrictedChampions), [restrictedChampions])

  const request = useMemo(() => cleanFormState(state), [state])
  const requestKey = useMemo(() => JSON.stringify(request), [request])
  const initialKey = useMemo(() => JSON.stringify(cleanFormState(initial)), [initial])
  const check = useDebouncedCheck(onCheck, request, requestKey, { key: initialKey, evaluation: initialEvaluation })

  // 편집 내용이 사라지는 새로고침·탭 닫기 전에 경고한다.
  useEffect(() => {
    const listener = (event: BeforeUnloadEvent) => {
      event.preventDefault()
      event.returnValue = ''
    }
    window.addEventListener('beforeunload', listener)
    return () => window.removeEventListener('beforeunload', listener)
  }, [])

  const save = useMutation({
    mutationFn: () => onSave(request),
    onError: (error) => setSaveFailure({ key: requestKey, error }),
  })

  const evaluation = check.evaluation
  const failure = saveFailure && saveFailure.key === requestKey ? saveFailure.error : null
  const failureIssues = issuesFromError(failure)
  const issues = failureIssues ?? (evaluation ? issuesFromEvaluation(evaluation) : { errors: [], warnings: [] })
  const slots = useMemo(() => slotIssues(state, restricted), [state, restricted])
  const labels = banLabels(state.topSide)
  const durationOk = isDurationValid(state.duration)
  const saveEnabled = canSave(evaluation, check.settling, check.error !== null) && durationOk && !save.isPending

  const aliasConflicts = new Map<string, string>()
  if (failure instanceof ApiError && failure.code === 'ALIAS_CONFLICT') {
    for (const c of failure.conflicts) {
      aliasConflicts.set(c.nickname.trim().toLowerCase(), `이미 ${c.playerDisplayName ?? `선수 #${c.playerId}`} 선수에게 연결된 닉네임입니다.`)
    }
  }

  const topTeam = evaluation?.topTeam ?? null
  const teamName = (key: 'top' | 'bottom') => {
    const label = key === 'top' ? '위쪽 팀' : '아래쪽 팀'
    if (!topTeam) return label
    const mine: DuelTeam = key === 'top' ? topTeam : topTeam === 'TEAM1' ? 'TEAM2' : 'TEAM1'
    return `${label} · ${TEAM_LABEL[mine]}`
  }
  const sideName = (key: 'top' | 'bottom') =>
    SIDE_LABEL[key === 'top' ? state.topSide : state.topSide === 'BLUE' ? 'RED' : 'BLUE']
  const championName = (id: string) => championsById.get(id)?.name ?? id

  const detection = evaluation?.detection
  const conflicts = evaluation?.fearlessConflicts ?? []

  return (
    <div className="review-step set-form">
      <ReviewIssuesList issues={issues} />
      {check.error !== null && <ErrorNotice error={check.error} />}
      {failure !== null && !failureIssues && (
        <>
          <ErrorNotice error={failure} />
          {failure instanceof ApiError && failure.code === 'DUPLICATE_SET' && failure.existingSetId !== undefined && (
            <p>
              <Link to={`/sets/${failure.existingSetId}`}>기존 세트 보기</Link>
            </p>
          )}
          {failure instanceof ApiError && failure.code === 'SET_MODIFIED' && (
            <button type="button" className="header-button" onClick={onReload}>
              새로 불러오기
            </button>
          )}
        </>
      )}

      {evaluation?.duplicateSet && (
        <div className="error-notice" role="alert">
          <p>
            이미 {evaluation.duplicateSet.setNo}세트로 등록된 같은 경기입니다(날짜·경기 시간·킬·골드가 같음). 저장할 수 없습니다.{' '}
            <Link to={`/sets/${evaluation.duplicateSet.setId}`}>기존 세트 보기</Link>
          </p>
        </div>
      )}

      {conflicts.length > 0 && (
        <div className="warn-notice" role="status" aria-label="뒤 세트 피어리스 충돌">
          <p>뒤 세트에서 이미 쓴 챔피언과 겹칩니다(저장은 할 수 있습니다)</p>
          <ul>
            {conflicts.map((conflict) => (
              <li key={`${conflict.setId}:${conflict.championKey}`}>
                <Link to={`/sets/${conflict.setId}`}>
                  대결 {conflict.duelSeq} · {conflict.setNo}세트
                </Link>{' '}
                — {championName(conflict.championKey)}
              </li>
            ))}
          </ul>
        </div>
      )}

      {restrictedChampions.length > 0 && (
        <details className="restricted-list">
          <summary>피어리스 제한 챔피언 {restrictedChampions.length}명</summary>
          <p>{restrictedChampions.map(championName).join(', ')}</p>
        </details>
      )}

      <div className="panel match-info">
        <div className="match-info-head">
          <h2>경기 정보</h2>
          {evaluation && <span className="badge badge-ok">{evaluation.setNo}세트</span>}
        </div>
        <div className="info-grid">
          <label>
            결과창 날짜
            <input
              type="date"
              value={state.shotDate}
              onChange={(event) => setState((s) => ({ ...s, shotDate: event.target.value }))}
            />
          </label>
          <label>
            경기 시간(mm:ss)
            <input
              type="text"
              placeholder="모르면 비워 두세요"
              value={state.duration}
              onChange={(event) => setState((s) => ({ ...s, duration: event.target.value }))}
            />
          </label>
          <label>
            위쪽 팀 결과
            <select
              value={state.topResult}
              onChange={(event) => setState((s) => ({ ...s, topResult: event.target.value as 'WIN' | 'LOSS' }))}
            >
              <option value="WIN">승리</option>
              <option value="LOSS">패배</option>
            </select>
          </label>
          <label>
            위쪽 팀 진영
            <select
              value={state.topSide}
              onChange={(event) => setState((s) => ({ ...s, topSide: event.target.value as 'BLUE' | 'RED' }))}
            >
              <option value="BLUE">블루</option>
              <option value="RED">레드</option>
            </select>
          </label>
        </div>
        {state.duration.trim() === '' && <p className="muted-text">경기 시간을 비워 두면 &quot;모름&quot;으로 저장됩니다.</p>}
        {!durationOk && <p className="slot-issue">경기 시간은 25:42처럼 분:초로 적거나 비워 주세요.</p>}
        {detection?.method === 'OVERLAP' && (
          <p className="detection">
            이 세트의 기존 A팀 선수와 겹치는 선수 {detection.overlap ?? 0}명으로 A팀/B팀을 판별했습니다.
          </p>
        )}
      </div>

      {(['top', 'bottom'] as const).map((key) => (
        <TeamTable
          key={key}
          teamKey={key}
          teamLabel={key === 'top' ? '위쪽 팀' : '아래쪽 팀'}
          heading={`${teamName(key)} · ${sideName(key)}`}
          team={state[key]}
          draftTeam={null}
          banLabels={labels[key]}
          imageUrl={null}
          champions={champions.champions}
          championsById={championsById}
          restricted={restricted}
          players={players.data}
          issues={slots}
          aliasConflicts={aliasConflicts}
          onUpdate={(fn) => setState((s) => ({ ...s, ...fn(s) }))}
        />
      ))}

      <div className="save-bar">
        <button type="button" className="primary-button" disabled={!saveEnabled} onClick={() => save.mutate()}>
          {save.isPending ? '저장 중...' : '저장'}
        </button>
        {check.settling && <span className="muted-text">검사 중...</span>}
      </div>
    </div>
  )
}
