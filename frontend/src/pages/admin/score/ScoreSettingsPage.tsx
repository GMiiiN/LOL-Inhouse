// 점수 설정(/admin/score): 기본 점수 6칸 + 우열 보정 3칸 + 라인 보정 5칸, 6×3 점수표 미리보기, 기본값으로.
// 입력이 올바르면 400ms 뒤 자동 저장한다(useScoreSaver가 한 번에 하나씩 순서대로 보낸다).
import { useQuery } from '@tanstack/react-query'
import { useCallback, useEffect, useRef, useState, type CSSProperties } from 'react'
import { getScoreTable } from '../../../api/tier'
import { SCORE_TABLE_KEY } from '../../../api/queryKeys'
import { TIERS, TIER_SUB_LABEL, TIER_SUBS, SCORE_MAX, SCORE_MIN, type ScoreTable, type ScoreTableResponse } from '../../../api/tierTypes'
import { POSITIONS, POSITION_LABEL } from '../../../api/types'
import { describeError } from '../../../api/errorMessages'
import { ErrorNotice } from '../../../components/ErrorNotice'
import '../../../styles/tier.css'
import { draftFromTable, gridValue, parseCell, parseDraft, type ScoreDraft } from './scoreModel'
import { useScoreSaver } from './useScoreSaver'

const SAVE_DELAY_MS = 400

type ScoreInputProps = { label: string; value: string; disabled: boolean; onChange: (text: string) => void }

function ScoreInput({ label, value, disabled, onChange }: ScoreInputProps) {
  return (
    <label className="score-input">
      {label}
      <input
        type="number"
        inputMode="numeric"
        min={SCORE_MIN}
        max={SCORE_MAX}
        step={1}
        value={value}
        disabled={disabled}
        aria-invalid={parseCell(value) === null}
        onChange={(e) => onChange(e.target.value)}
      />
    </label>
  )
}

export function ScoreSettingsPage() {
  const table = useQuery({ queryKey: SCORE_TABLE_KEY, queryFn: getScoreTable })
  // 사용자가 손댄 입력. null이면 서버에서 받은 값을 그대로 보여 준다.
  const [edited, setEdited] = useState<ScoreDraft | null>(null)
  const [waiting, setWaiting] = useState(false) // 저장 대기(디바운스) 중
  const [resetting, setResetting] = useState(false) // 기본값 복원 요청이 큐에 있거나 진행 중
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null)
  const latest = useRef<ScoreTable | null>(null)

  const saver = useScoreSaver() // 저장 큐는 모듈 하나를 모든 화면 인스턴스가 공유한다
  const { enqueue } = saver

  const draft = edited ?? (table.data ? draftFromTable(table.data) : null)
  const parsed = draft ? parseDraft(draft) : null
  const invalid = draft !== null && parsed === null

  const clearTimer = useCallback(() => {
    if (timer.current) clearTimeout(timer.current)
    timer.current = null
    setWaiting(false)
  }, [])

  // 화면을 떠날 때 아직 보내지 않은 입력이 있으면 버리지 않고 바로 저장한다
  useEffect(
    () => () => {
      if (timer.current) {
        clearTimeout(timer.current)
        if (latest.current) enqueue({ kind: 'put', table: latest.current })
      }
    },
    [enqueue],
  )

  function change(update: (current: ScoreDraft) => ScoreDraft) {
    if (!draft) return
    const next = update(draft)
    setEdited(next)
    const nextParsed = parseDraft(next)
    latest.current = nextParsed
    clearTimer()
    if (!nextParsed) return // 올바르지 않은 입력은 저장하지 않는다
    setWaiting(true)
    timer.current = setTimeout(() => {
      timer.current = null
      setWaiting(false)
      enqueue({ kind: 'put', table: nextParsed })
    }, SAVE_DELAY_MS)
  }

  function reset() {
    clearTimer() // 보내지 않은 입력은 기본값이 덮어쓴다
    latest.current = null
    setResetting(true)
    enqueue({
      kind: 'reset',
      onDone: (result: ScoreTableResponse | undefined) => {
        setEdited(result?.base ? draftFromTable(result) : null) // 응답이 없으면 새로 받은 서버 값을 쓴다
        setResetting(false)
      },
      onFailed: () => setResetting(false),
    })
  }

  function retry() {
    if (parsed) enqueue({ kind: 'put', table: parsed })
  }

  if (table.isPending) {
    return (
      <section>
        <div className="page-title-row">
          <h1>점수 설정</h1>
        </div>
        <p className="muted-text">불러오는 중입니다...</p>
      </section>
    )
  }
  if (!draft) {
    return (
      <section>
        <div className="page-title-row">
          <h1>점수 설정</h1>
        </div>
        <ErrorNotice error={table.error} />
        <button type="button" className="header-button" onClick={() => table.refetch()}>
          다시 시도
        </button>
      </section>
    )
  }

  let statusText: string
  if (resetting) statusText = '기본값으로 되돌리는 중...'
  else if (invalid) statusText = '저장하지 않았어요. 빈칸이나 범위를 벗어난 값이 있어요.'
  else if (waiting) statusText = '변경 사항을 저장하려고 기다리는 중...'
  else if (saver.status === 'saving') statusText = '저장 중...'
  else if (saver.status === 'saved') statusText = '저장됨'
  else if (saver.status === 'error') statusText = `저장하지 못했어요. ${describeError(saver.error).message}`
  else statusText = '값을 바꾸면 자동으로 저장돼요.'

  return (
    <section>
      <div className="page-title-row">
        <h1>점수 설정</h1>
      </div>

      <div className="panel">
        <p className="tier-help">
          라인 점수 = 티어 기본 점수 + 상·중·하 보정 + 라인 보정. 1티어가 가장 잘하는 단계예요. 점수는 {SCORE_MIN}~{SCORE_MAX} 사이 정수로 넣어 주세요.
        </p>

        <h2>티어 기본 점수</h2>
        <div className="score-inputs">
          {TIERS.map((tier) => (
            <ScoreInput
              key={tier}
              label={`${tier}티어`}
              value={draft.base[`${tier}`]}
              disabled={resetting}
              onChange={(text) => change((d) => ({ ...d, base: { ...d.base, [`${tier}`]: text } }))}
            />
          ))}
        </div>

        <h2>티어 안 우열 보정</h2>
        <div className="score-inputs">
          {TIER_SUBS.map((sub) => (
            <ScoreInput
              key={sub}
              label={TIER_SUB_LABEL[sub]}
              value={draft.sub[sub]}
              disabled={resetting}
              onChange={(text) => change((d) => ({ ...d, sub: { ...d.sub, [sub]: text } }))}
            />
          ))}
        </div>

        <h2>라인 보정</h2>
        <div className="score-inputs">
          {POSITIONS.map((position) => (
            <ScoreInput
              key={position}
              label={POSITION_LABEL[position]}
              value={draft.lane[position]}
              disabled={resetting}
              onChange={(text) => change((d) => ({ ...d, lane: { ...d.lane, [position]: text } }))}
            />
          ))}
        </div>
        <p className="tier-help">
          라인 보정은 라인마다 게임에 미치는 영향이 다른 점을 반영해요. 같은 라인끼리 비교할 때는 팀 총점 차이에 영향이 없고, 자동 배정에서 누구를 어느 라인에 보낼지 정할 때 쓰여요.
        </p>

        <h2>
          티어 × 우열 점수표 <span className="muted-text">(라인 보정 전)</span>
        </h2>
        <table className="score-grid" aria-label="티어와 우열별 점수표">
          <thead>
            <tr>
              <td />
              {TIER_SUBS.map((sub) => (
                <th key={sub} scope="col">
                  {TIER_SUB_LABEL[sub]}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {TIERS.map((tier) => (
              <tr key={tier} style={{ '--tc': `var(--t${tier})` } as CSSProperties}>
                <th scope="row">{tier}티어</th>
                {TIER_SUBS.map((sub) => (
                  <td key={sub} data-testid={`grid-${tier}-${sub}`}>
                    {gridValue(draft, tier, sub) ?? '—'}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>

        <div className="score-footer">
          <button type="button" className="header-button" disabled={resetting} onClick={reset}>
            기본값으로
          </button>
          <span className={`score-status ${invalid || saver.status === 'error' ? 'is-error' : ''}`} role="status">
            {statusText}
          </span>
          {saver.status === 'error' && !invalid && !resetting && (
            <button type="button" className="header-button" onClick={retry}>
              다시 저장
            </button>
          )}
        </div>
      </div>
    </section>
  )
}
