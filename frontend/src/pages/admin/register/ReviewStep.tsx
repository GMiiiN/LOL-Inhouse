// 세트 등록 3단계(REVIEW): AI 초안을 확인·수정하고 저장한다.
// 편집 상태는 reviewModel(순수 함수), 서버 검사는 useDebouncedCheck(600ms), 저장은 registerSet.
// 팀 변경 감지(설계 §11.1): 맞라인 교환은 노란 안내, 포지션 변경·팀 재구성은 경고 + "새 대결로 등록".
// 설계: docs/design/m4-design.md §6·§11
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { listPlayers } from '../../../api/admin'
import { ApiError } from '../../../api/client'
import { PLAYERS_KEY, PUBLIC_KEY, registrationContextKey, SESSIONS_KEY } from '../../../api/queryKeys'
import { checkSet, registerSet } from '../../../api/registration'
import type {
  ChampionList,
  DraftResponse,
  DuelTeam,
  FearlessMode,
  NewDuelOption,
  RegistrationContext,
  SetRegistrationResponse,
  Side,
} from '../../../api/registrationTypes'
import { POSITION_LABEL, POSITIONS, type Position } from '../../../api/types'
import { ErrorNotice } from '../../../components/ErrorNotice'
import { ReviewIssuesList } from './ReviewIssuesList'
import { TeamTable } from './TeamTable'
import { useDebouncedCheck } from './useDebouncedCheck'
import {
  banLabels,
  buildRequest,
  canSave,
  changeCapturerPosition,
  initReviewState,
  issuesFromError,
  issuesFromEvaluation,
  slotIssues,
  TEAM_LABEL,
  type ReviewState,
} from './reviewModel'

const SIDE_LABEL = { BLUE: '블루', RED: '레드' } as const
const FEARLESS_LABEL: Record<FearlessMode, string> = { RESET: '피어리스 초기화', CONTINUE: '피어리스 이어가기' }

type ReviewStepProps = {
  duelId: number
  draft: DraftResponse
  context: RegistrationContext
  champions: ChampionList
  imageUrl: string | null
  chosen?: { topSide: Side; capturerPosition: Position } | null // 작업을 재사용했을 때 이번 업로드에서 고른 값
  onSaved: (result: SetRegistrationResponse) => void
}

export function ReviewStep({ duelId, draft, context, champions, imageUrl, chosen = null, onSaved }: ReviewStepProps) {
  const queryClient = useQueryClient()
  const [state, setState] = useState<ReviewState>(() => {
    // 재사용된 작업의 초안은 옛 선택값으로 만들어졌다. 지금 고른 값으로 덮어쓴다(서버 검사는 달라진 요청으로 자동 재실행).
    let initial = initReviewState(draft.defaults)
    if (chosen && chosen.topSide !== draft.topSide) initial = { ...initial, topSide: chosen.topSide }
    if (chosen && chosen.capturerPosition !== draft.capturerPosition) {
      initial = changeCapturerPosition(initial, chosen.capturerPosition)
    }
    return initial
  })
  const selectionApplied =
    chosen !== null && (chosen.topSide !== draft.topSide || chosen.capturerPosition !== draft.capturerPosition)
  const [newDuel, setNewDuel] = useState<NewDuelOption | null>(null)
  const [choosingMode, setChoosingMode] = useState<FearlessMode>('RESET')
  const [choosing, setChoosing] = useState(false)
  const [recheck, setRecheck] = useState(0) // SET_NO_CONFLICT 뒤 같은 입력을 다시 검사하게 하는 번호
  const [zoomed, setZoomed] = useState(false)
  const [saveFailure, setSaveFailure] = useState<{ key: string; error: unknown } | null>(null)

  const players = useQuery({ queryKey: PLAYERS_KEY, queryFn: listPlayers })
  const championsById = useMemo(() => new Map(champions.champions.map((c) => [c.id, c])), [champions])

  // 새 대결을 초기화(RESET)로 미리 보는 동안은 피어리스 제한이 없다. 이어가기면 기존 제한을 유지하고 최종 판단은 서버 검사가 한다.
  const restricted = useMemo(
    () => new Set(newDuel?.fearlessMode === 'RESET' ? [] : context.restrictedChampions),
    [newDuel, context.restrictedChampions],
  )

  const request = useMemo(() => buildRequest(state, newDuel), [state, newDuel])
  const requestJson = useMemo(() => JSON.stringify(request), [request])
  const initialJson = useMemo(() => JSON.stringify(buildRequest(initReviewState(draft.defaults), null)), [draft])
  const check = useDebouncedCheck(
    (body) => checkSet(duelId, body),
    request,
    `${requestJson}#${recheck}`,
    { key: `${initialJson}#0`, evaluation: draft.evaluation },
  )

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
    mutationFn: () => registerSet(duelId, request),
    onSuccess: async (result) => {
      setSaveFailure(null)
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: PLAYERS_KEY }),
        queryClient.invalidateQueries({ queryKey: registrationContextKey(duelId) }),
        queryClient.invalidateQueries({ queryKey: registrationContextKey(result.duelId) }),
        queryClient.invalidateQueries({ queryKey: SESSIONS_KEY }),
        queryClient.invalidateQueries({ queryKey: PUBLIC_KEY }),
      ])
      onSaved(result)
    },
    onError: (error) => {
      setSaveFailure({ key: requestJson, error })
      if (error instanceof ApiError && error.code === 'SET_NO_CONFLICT') {
        void queryClient.invalidateQueries({ queryKey: registrationContextKey(duelId) })
        setRecheck((n) => n + 1)
      }
    },
  })

  const evaluation = check.evaluation
  const failure = saveFailure && saveFailure.key === requestJson ? saveFailure.error : null
  const failureIssues = issuesFromError(failure)
  const issues = failureIssues ?? (evaluation ? issuesFromEvaluation(evaluation) : { errors: [], warnings: [] })
  const slots = useMemo(() => slotIssues(state, restricted), [state, restricted])
  const labels = banLabels(state.topSide)

  // 저장 때 ALIAS_CONFLICT가 지목한 닉네임 → 해당 행에 표시할 설명
  const aliasConflicts = new Map<string, string>()
  if (failure instanceof ApiError && failure.code === 'ALIAS_CONFLICT') {
    for (const c of failure.conflicts) {
      aliasConflicts.set(c.nickname.trim().toLowerCase(), `이미 ${c.playerDisplayName ?? `선수 #${c.playerId}`} 선수에게 연결된 닉네임입니다.`)
    }
  }

  const topTeam = evaluation?.topTeam ?? null
  const teamName = (key: 'top' | 'bottom') => {
    if (!topTeam) return key === 'top' ? '위쪽 팀' : '아래쪽 팀'
    const mine: DuelTeam = key === 'top' ? topTeam : topTeam === 'TEAM1' ? 'TEAM2' : 'TEAM1'
    return `${key === 'top' ? '위쪽 팀' : '아래쪽 팀'} · ${TEAM_LABEL[mine]}`
  }
  const sideName = (key: 'top' | 'bottom') => {
    const side = key === 'top' ? state.topSide : state.topSide === 'BLUE' ? 'RED' : 'BLUE'
    return SIDE_LABEL[side]
  }

  const change = evaluation?.teamChange
  const showChange = !newDuel && change && change.kind !== 'NONE' && change.kind !== 'PENDING'
  const saveEnabled = canSave(evaluation, check.settling, check.error !== null) && !save.isPending

  function startNewDuel() {
    setNewDuel({ fearlessMode: choosingMode })
    setChoosing(false)
  }

  const detection = evaluation?.detection
  const detectionText = !detection
    ? null
    : detection.method === 'FIRST_SET'
      ? '대결 첫 세트: 블루 팀이 A팀입니다.'
      : detection.method === 'OVERLAP'
        ? `직전 세트와 겹치는 선수 ${detection.overlap ?? 0}명으로 A팀/B팀을 판별했습니다.`
        : '선수를 모두 고르면 A팀/B팀을 판별합니다.'

  return (
    <div className="review-step">
      {selectionApplied && (
        <div className="notice" role="status">
          <p>이전에 추출한 결과를 다시 썼고, 지금 고른 진영·포지션을 적용했습니다.</p>
        </div>
      )}
      <ReviewIssuesList issues={issues} />
      {check.error !== null && <ErrorNotice error={check.error} />}
      {failure !== null && !failureIssues && <SaveFailureNotice error={failure} />}

      {evaluation?.duplicateSet && (
        <div className="error-notice" role="alert">
          <p>
            이미 {evaluation.duplicateSet.setNo}세트로 등록된 같은 경기입니다(날짜·경기 시간·킬·골드가 같음). 저장할 수 없습니다.{' '}
            <Link to={`/sets/${evaluation.duplicateSet.setId}`}>기존 세트 보기</Link>
          </p>
        </div>
      )}

      {showChange && change && (
        <div className={change.kind === 'SAME_LANE_SWAP' ? 'notice' : 'warn-notice'} role="status" aria-label="팀 변경 감지">
          {change.kind === 'SAME_LANE_SWAP' ? (
            <p>
              맞라인 교환 {change.swaps}건 감지 — 피어리스를 유지하면 이 대결로 저장, 초기화했다면 새 대결로 등록하세요.
            </p>
          ) : (
            <p>
              {change.kind === 'POSITION_CHANGE'
                ? '팀 구성은 같지만 포지션이 바뀐 선수가 있습니다.'
                : '직전 세트와 팀 구성이 크게 달라졌습니다.'}{' '}
              피어리스를 초기화했다면 새 대결로 등록하세요. 같은 대결로 저장할 수도 있습니다.
            </p>
          )}
          {change.details.length > 0 && (
            <ul>
              {change.details.map((line) => (
                <li key={line}>{line}</li>
              ))}
            </ul>
          )}
          {!choosing && (
            <button type="button" className="header-button" onClick={() => setChoosing(true)}>
              새 대결로 등록
            </button>
          )}
        </div>
      )}

      {choosing && (
        <div className="panel new-duel-chooser">
          <h2>새 대결로 등록</h2>
          <p className="muted-text">이 세트를 새 대결의 1세트로 저장합니다. 새 대결의 피어리스 방식을 고르세요.</p>
          <fieldset className="choice-group">
            <legend>피어리스 방식</legend>
            {(['RESET', 'CONTINUE'] as const).map((mode) => (
              <label key={mode}>
                <input
                  type="radio"
                  name="new-duel-mode"
                  value={mode}
                  checked={choosingMode === mode}
                  onChange={() => setChoosingMode(mode)}
                />
                {mode === 'RESET' ? '초기화(제한 없이 시작)' : '이어가기(이전 대결 픽 계속 제한)'}
              </label>
            ))}
          </fieldset>
          <div className="button-row">
            <button type="button" className="primary-button" onClick={startNewDuel}>
              새 대결로 미리 보기
            </button>
            <button type="button" className="header-button" onClick={() => setChoosing(false)}>
              취소
            </button>
          </div>
        </div>
      )}

      {newDuel && (
        <div className="notice strong" role="status">
          <p>
            <b>새 대결의 1세트</b>로 저장합니다 · {FEARLESS_LABEL[newDuel.fearlessMode]}
          </p>
          <button type="button" className="header-button" onClick={() => setNewDuel(null)}>
            새 대결 취소
          </button>
        </div>
      )}

      <div className="panel match-info">
        <div className="match-info-head">
          <h2>경기 정보</h2>
          {evaluation && (
            <span className="badge badge-ok">{newDuel ? '새 대결 1세트' : `${evaluation.setNo}세트`}</span>
          )}
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
          <label>
            캡처한 사람 포지션
            <select
              value={state.capturerPosition}
              onChange={(event) => setState((s) => changeCapturerPosition(s, event.target.value as Position))}
            >
              {POSITIONS.map((position) => (
                <option key={position} value={position}>
                  {POSITION_LABEL[position]}
                </option>
              ))}
            </select>
          </label>
        </div>
        {detectionText && (
          <p className="detection">
            {detectionText}
            {detection?.uncertain && ' (팀이 바뀌어 판별이 불확실합니다)'}
          </p>
        )}
        {imageUrl && (
          <button
            type="button"
            className={`source-thumb${zoomed ? ' zoomed' : ''}`}
            aria-label={zoomed ? '원본 이미지 줄이기' : '원본 이미지 확대'}
            onClick={() => setZoomed((z) => !z)}
          >
            <img src={imageUrl} alt="업로드한 결과창 원본" />
          </button>
        )}
      </div>

      {(['top', 'bottom'] as const).map((key) => (
        <TeamTable
          key={key}
          teamKey={key}
          teamLabel={key === 'top' ? '위쪽 팀' : '아래쪽 팀'}
          heading={`${teamName(key)} · ${sideName(key)}`}
          team={state[key]}
          draftTeam={draft[key]}
          banLabels={labels[key]}
          imageUrl={imageUrl}
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

// 422 외의 저장 실패: 문구 + 사례별 안내(기존 세트 링크 등)
function SaveFailureNotice({ error }: { error: unknown }) {
  if (!(error instanceof ApiError)) return <ErrorNotice error={error} />
  return (
    <>
      <ErrorNotice error={error} />
      {error.code === 'DUPLICATE_SET' && error.existingSetId !== undefined && (
        <p>
          <Link to={`/sets/${error.existingSetId}`}>기존 세트 보기</Link>
        </p>
      )}
      {error.code === 'EXTRACTION_ALREADY_SAVED' && error.savedSetId !== undefined && (
        <p>
          <Link to={`/sets/${error.savedSetId}`}>저장된 세트 보기</Link>
        </p>
      )}
    </>
  )
}
