// 세트 등록(/admin/duels/:duelId/sets/new): SETUP → EXTRACTING → REVIEW → SAVED 상태 머신.
// 설계: docs/design/m4-design.md §6, §11
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useCallback, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { ApiError } from '../../../api/client'
import { CHAMPIONS_KEY, draftKey, registrationContextKey } from '../../../api/queryKeys'
import { getDraft, getRegistrationContext, listChampions, startExtraction } from '../../../api/registration'
import type { ChampionList, SetRegistrationResponse, Side } from '../../../api/registrationTypes'
import type { Position } from '../../../api/types'
import { ErrorNotice } from '../../../components/ErrorNotice'
import { ExtractionProgress } from './ExtractionProgress'
import { ReviewStep } from './ReviewStep'
import { TEAM_LABEL } from './reviewModel'
import { UploadStep, type UploadSelection } from './UploadStep'
import { useObjectUrl } from './useObjectUrl'

type Stage =
  | { kind: 'SETUP' }
  | { kind: 'EXTRACTING'; jobId: number; startedAt: number; reused: boolean }
  | { kind: 'REVIEW'; jobId: number; reused: boolean }
  | { kind: 'SAVED'; result: SetRegistrationResponse }

type Timing = { pollMs?: number; giveUpMs?: number } // 테스트에서 짧게 줄이기 위한 옵션

export function SetRegisterPage(timing: Timing) {
  const duelId = Number(useParams().duelId)
  // 대결이 바뀌면(새 대결로 저장한 뒤 이동) 상태를 통째로 새로 시작한다.
  return <RegisterFlow key={duelId} duelId={duelId} {...timing} />
}

function RegisterFlow({ duelId, pollMs, giveUpMs }: { duelId: number } & Timing) {
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const [stage, setStage] = useState<Stage>({ kind: 'SETUP' })
  const [image, setImage] = useState<File | null>(null)
  const [selection, setSelection] = useState<{ topSide: Side; capturerPosition: Position } | null>(null)
  const imageUrl = useObjectUrl(image)

  const context = useQuery({ queryKey: registrationContextKey(duelId), queryFn: () => getRegistrationContext(duelId), staleTime: 0 })
  const champions = useQuery({ queryKey: CHAMPIONS_KEY, queryFn: listChampions, staleTime: Infinity })

  const start = useMutation({
    mutationFn: (input: { selection: UploadSelection; retry: boolean }) =>
      startExtraction(
        duelId,
        { image: input.selection.image, topSide: input.selection.topSide, capturerPosition: input.selection.capturerPosition },
        input.retry,
      ),
    onSuccess: (response) => setStage({ kind: 'EXTRACTING', jobId: response.jobId, startedAt: Date.now(), reused: response.reused }),
  })

  function begin(next: UploadSelection) {
    setImage(next.image)
    setSelection({ topSide: next.topSide, capturerPosition: next.capturerPosition })
    start.mutate({ selection: next, retry: false })
  }

  function retryExtraction() {
    if (image && selection) start.mutate({ selection: { image, ...selection }, retry: true })
  }

  const handleSucceeded = useCallback(() => {
    setStage((current) => (current.kind === 'EXTRACTING' ? { kind: 'REVIEW', jobId: current.jobId, reused: current.reused } : current))
  }, [])

  function backToSetup() {
    start.reset()
    setImage(null)
    setSelection(null)
    setStage({ kind: 'SETUP' })
    void queryClient.invalidateQueries({ queryKey: registrationContextKey(duelId) })
  }

  const ctx = context.data

  return (
    <section className="register-page">
      <p>{ctx ? <Link to={`/admin/sessions/${ctx.sessionId}`}>← 내전 묶음으로</Link> : <Link to="/admin">← 내전·대결</Link>}</p>
      <h1>세트 등록</h1>
      {context.isPending && <p className="muted-text">불러오는 중입니다...</p>}
      <ErrorNotice error={context.error} />

      {ctx && (
        <div className="panel register-head">
          <h2>
            대결 {ctx.duelSeq} · {ctx.nextSetNo}세트로 등록
          </h2>
          <p className="muted-text">내전 묶음 {ctx.sessionStartDate} · 세트는 순서대로 올려 주세요(번호는 서버가 정합니다).</p>
          {ctx.registeredSets.length > 0 && (
            <ul className="registered-sets" aria-label="등록된 세트">
              {ctx.registeredSets.map((set) => (
                <li key={set.id}>
                  <b>{set.setNo}세트</b> · A팀 {set.team1Side === 'BLUE' ? '블루' : '레드'} · {TEAM_LABEL[set.winnerTeam]} 승
                </li>
              ))}
            </ul>
          )}
          {ctx.restrictedChampions.length > 0 && (
            <details className="restricted-list">
              <summary>피어리스 제한 챔피언 {ctx.restrictedChampions.length}명</summary>
              <p>
                {ctx.restrictedChampions
                  .map((id) => champions.data?.champions.find((c) => c.id === id)?.name ?? id)
                  .join(', ')}
              </p>
            </details>
          )}
        </div>
      )}
      {champions.isError && (
        <div>
          <ErrorNotice error={champions.error} />
          <button type="button" className="header-button" onClick={() => void champions.refetch()}>
            챔피언 목록 다시 불러오기
          </button>
        </div>
      )}

      {ctx && stage.kind === 'SETUP' && (
        <UploadStep
          key={ctx.nextSetNo}
          defaultTopSide={ctx.defaultTopSide}
          pending={start.isPending}
          error={start.error}
          onStart={begin}
        />
      )}

      {stage.kind === 'EXTRACTING' && (
        <>
          <ExtractionProgress
            key={stage.jobId}
            jobId={stage.jobId}
            startedAt={stage.startedAt}
            retrying={start.isPending}
            onSucceeded={handleSucceeded}
            onRetry={retryExtraction}
            pollMs={pollMs}
            giveUpMs={giveUpMs}
          />
          <ErrorNotice error={start.error} />
        </>
      )}

      {stage.kind === 'REVIEW' && ctx && (
        <ReviewLoader
          key={stage.jobId}
          duelId={duelId}
          jobId={stage.jobId}
          imageUrl={imageUrl}
          chosen={stage.reused ? selection : null}
          championsReady={champions.data}
          onSaved={(result) => setStage({ kind: 'SAVED', result })}
          onRestart={backToSetup}
        />
      )}

      {stage.kind === 'SAVED' && (
        <div className="panel saved-step">
          <h2>{stage.result.setNo}세트 저장됨</h2>
          {stage.result.warnings.length > 0 && (
            <ul className="muted-text">
              {stage.result.warnings.map((w) => (
                <li key={w}>{w}</li>
              ))}
            </ul>
          )}
          {stage.result.createdPlayers.length > 0 && (
            <p className="muted-text">새로 만든 미등록 선수: {stage.result.createdPlayers.map((p) => p.nickname).join(', ')}</p>
          )}
          {stage.result.addedAliases.length > 0 && (
            <p className="muted-text">추가한 별칭: {stage.result.addedAliases.map((p) => p.nickname).join(', ')}</p>
          )}
          <div className="button-row">
            <button
              type="button"
              className="primary-button"
              onClick={() => {
                // 새 대결로 저장했으면 그 대결에서 이어서 등록한다
                if (stage.result.duelId !== duelId) navigate(`/admin/duels/${stage.result.duelId}/sets/new`)
                else backToSetup()
              }}
            >
              다음 세트 등록
            </button>
            <Link className="header-button" to={ctx ? `/admin/sessions/${ctx.sessionId}` : '/admin'}>
              내전 묶음으로
            </Link>
          </div>
        </div>
      )}
    </section>
  )
}

type ReviewLoaderProps = {
  duelId: number
  jobId: number
  imageUrl: string | null
  chosen: { topSide: Side; capturerPosition: Position } | null
  championsReady: ChampionList | undefined
  onSaved: (result: SetRegistrationResponse) => void
  onRestart: () => void
}

// 초안을 불러와 확인 화면을 그린다. 초안/챔피언 목록/등록 맥락이 모두 있어야 시작한다.
function ReviewLoader({ duelId, jobId, imageUrl, chosen, championsReady, onSaved, onRestart }: ReviewLoaderProps) {
  const context = useQuery({ queryKey: registrationContextKey(duelId), queryFn: () => getRegistrationContext(duelId) })
  const draft = useQuery({
    queryKey: draftKey(jobId),
    queryFn: () => getDraft(jobId),
    staleTime: Infinity,
    gcTime: 0,
    retry: false,
  })

  if (draft.isError) {
    const error = draft.error
    return (
      <div className="panel">
        <ErrorNotice error={error} />
        {error instanceof ApiError && error.code === 'EXTRACTION_ALREADY_SAVED' && error.savedSetId !== undefined && (
          <p>
            <Link to={`/sets/${error.savedSetId}`}>저장된 세트 보기</Link>
          </p>
        )}
        <div className="button-row">
          <button type="button" className="header-button" onClick={() => void draft.refetch()}>
            다시 불러오기
          </button>
          <button type="button" className="header-button" onClick={onRestart}>
            처음부터 다시
          </button>
        </div>
      </div>
    )
  }
  if (!draft.data || !context.data) return <p className="muted-text">초안을 불러오는 중입니다...</p>
  if (!championsReady) return <p className="muted-text">챔피언 목록을 불러오는 중입니다...</p>
  return (
    <ReviewStep
      duelId={duelId}
      draft={draft.data}
      context={context.data}
      champions={championsReady}
      imageUrl={imageUrl}
      chosen={chosen}
      onSaved={onSaved}
    />
  )
}
