// 팀 짜기 결과: 방안 카드(최대 4개), 선택한 방안의 상세·판정·라인 줄, 수동 교체, 진영 바꾸기, 코인토스, 복사.
// 새 계산 결과마다 부모가 key를 바꿔 이 컴포넌트를 새로 만든다(선택·교체·코인 상태 초기화).
import { useState } from 'react'
import { POSITION_LABEL, POSITIONS, type Position } from '../../api/types'
import type { ScoreMatrix, SlotView, TeamOption } from '../../api/tierTypes'
import { CoinToss } from './CoinToss'
import {
  laneGapText,
  resultText,
  revert,
  swapPlayers,
  swapSides,
  toCandidates,
  verdictOf,
  type Candidate,
  type Side,
  type SlotRef,
} from './teamResultModel'

type Props = {
  options: TeamOption[]
  scoreMatrix: ScoreMatrix
  nameOf: (playerId: number) => string
  fixedLanes?: Record<number, Position> // 이 계산 때 자동 모드에서 고정한 라인. 그 칸에만 "· 고정"을 붙인다
  random?: () => number
  coinDelayMs?: number
}

// 칸의 티어 표기. 티어 없는 라인은 경고색, 추정 티어는 "(추정)", 고정한 라인이면 "· 고정"(수동 교체로 다른 라인에 간 사람은 표시 안 함)
function SlotTier({ slot, fixedLanes }: { slot: SlotView; fixedLanes: Record<number, Position> }) {
  return (
    <>
      {slot.tier === null ? <span className="tm-nolane">티어 없음</span> : `${slot.tier}티어`}
      {slot.estimated && ' (추정)'}
      {fixedLanes[slot.playerId] === slot.position && (
        <>
          {' · '}
          <span className="tm-fixtag">고정</span>
        </>
      )}
    </>
  )
}

export function TeamResults({ options, scoreMatrix, nameOf, fixedLanes = {}, random, coinDelayMs }: Props) {
  const [candidates, setCandidates] = useState<Candidate[]>(() => toCandidates(options))
  const [selected, setSelected] = useState(0)
  const [picked, setPicked] = useState<SlotRef | null>(null)
  const [coinKey, setCoinKey] = useState(0) // 올리면 코인 결과가 지워진다
  const [copyText, setCopyText] = useState<string | null>(null)
  const [copied, setCopied] = useState(false)

  const candidate = candidates[selected]
  const option = candidate.current
  const verdict = verdictOf(option)
  const total = option.blueSum + option.redSum || 1

  // 결과가 바뀌면 칸 선택(좌표라 다른 사람을 가리키게 됨)과 복사 상태(옛 글)를 함께 지운다
  const replaceSelected = (next: Candidate) => {
    setCandidates((list) => list.map((item, i) => (i === selected ? next : item)))
    setPicked(null)
    setCopyText(null)
    setCopied(false)
  }

  function select(index: number) {
    setSelected(index)
    setPicked(null)
    setCoinKey((key) => key + 1)
    setCopyText(null)
    setCopied(false)
  }

  function clickSlot(ref: SlotRef) {
    if (!picked) return setPicked(ref)
    if (picked.side === ref.side && picked.i === ref.i) return setPicked(null)
    replaceSelected(swapPlayers(candidate, picked, ref, scoreMatrix))
    setPicked(null)
    setCoinKey((key) => key + 1)
  }

  function changeSides() {
    replaceSelected(swapSides(candidate))
    setPicked(null)
    setCoinKey((key) => key + 1)
  }

  function undoEdits() {
    replaceSelected(revert(candidate))
    setPicked(null)
    setCoinKey((key) => key + 1)
  }

  async function copyResult() {
    const text = resultText(option, nameOf)
    try {
      await navigator.clipboard.writeText(text)
      setCopied(true)
      setCopyText(null)
    } catch {
      setCopied(false)
      setCopyText(text)
    }
  }

  const names = (slots: SlotView[]) => slots.map((slot) => nameOf(slot.playerId))
  const isPicked = (side: Side, i: number) => picked?.side === side && picked.i === i

  return (
    <>
      <section className="tm-panel" aria-label="팀 방안">
        <div className="tm-bar">
          <h2>팀 방안 {candidates.length}개</h2>
          <span className="muted tm-small">점수 차와 라인 맞대결 격차가 작은 순서예요</span>
        </div>
        <div className="tm-options">
          {candidates.map((item, i) => (
            <button
              key={i}
              type="button"
              className="tm-opt"
              aria-pressed={selected === i}
              aria-label={`방안 ${i + 1}`}
              onClick={() => select(i)}
            >
              <div className="oh">
                <span>
                  방안 {i + 1}
                  {item.original && <em className="tm-edtag">수정됨</em>}
                </span>
                <span className="os">
                  <span className="b">{item.current.blueSum}</span> : <span className="r">{item.current.redSum}</span>
                </span>
              </div>
              <div className="om">
                점수 차 <b className="num">{item.current.diff}</b> · 최대 라인 격차 <b className="num">{item.current.gap}</b>
              </div>
              <div className="om">
                <span className="tm-blue">A팀·블루</span> {names(item.current.blue).join(', ')}
                <br />
                <span className="tm-red">B팀·레드</span> {names(item.current.red).join(', ')}
              </div>
            </button>
          ))}
        </div>
      </section>

      <section className="tm-panel" aria-label={`방안 ${selected + 1} 자세히`}>
        <div className="tm-bar">
          <h2>방안 {selected + 1} 자세히</h2>
          <div className="tm-group">
            <CoinToss
              key={coinKey}
              blueNames={names(option.blue)}
              redNames={names(option.red)}
              onSettle={(needsSwap) => needsSwap && replaceSelected(swapSides(candidate))}
              random={random}
              delayMs={coinDelayMs}
            />
            <button type="button" className="tm-btn small" onClick={changeSides}>
              진영 바꾸기
            </button>
            <button type="button" className="tm-btn small" onClick={copyResult}>
              {copied ? '복사했어요' : '결과 복사'}
            </button>
          </div>
        </div>

        <div className="tm-score-head">
          <div className="side b">
            <span>A팀 · 블루</span>
            <b>{option.blueSum}</b>
          </div>
          <div className="tm-diff">
            점수 차<b>{option.diff}</b>
          </div>
          <div className="side r">
            <span>B팀 · 레드</span>
            <b>{option.redSum}</b>
          </div>
        </div>
        <div className="tm-meter" role="img" aria-label={`블루 ${option.blueSum}점, 레드 ${option.redSum}점`}>
          <i className="mb" style={{ width: `${(option.blueSum / total) * 100}%` }} />
          <i className="mr" style={{ width: `${(option.redSum / total) * 100}%` }} />
        </div>

        <p className="tm-help">
          마음에 드는 팀이 없으면 직접 고칠 수 있어요. 두 사람을 차례로 누르면 자리가 바뀌어요. 같은 팀 안에서 라인을
          바꾸거나, 상대 팀 사람과 맞바꿀 수 있어요.{' '}
          {candidate.original && (
            <button type="button" className="tm-btn small" onClick={undoEdits}>
              처음 방안으로
            </button>
          )}
        </p>

        <div className="tm-lanes">
          {POSITIONS.map((position, i) => {
            const blue = option.blue[i]
            const red = option.red[i]
            return (
              <div key={position} className="tm-lanerow">
                <button
                  type="button"
                  className={`tm-pl b${isPicked('blue', i) ? ' picked' : ''}`}
                  aria-pressed={isPicked('blue', i)}
                  onClick={() => clickSlot({ side: 'blue', i })}
                >
                  <span className="nm">{nameOf(blue.playerId)}</span>
                  <span className="tg">
                    <SlotTier slot={blue} fixedLanes={fixedLanes} /> · <span className="num">{blue.score}</span>
                  </span>
                </button>
                <div className="tm-mid">
                  {POSITION_LABEL[position]}
                  <small className="num">{laneGapText(blue.score, red.score)}</small>
                </div>
                <button
                  type="button"
                  className={`tm-pl r${isPicked('red', i) ? ' picked' : ''}`}
                  aria-pressed={isPicked('red', i)}
                  onClick={() => clickSlot({ side: 'red', i })}
                >
                  <span className="nm">{nameOf(red.playerId)}</span>
                  <span className="tg">
                    <span className="num">{red.score}</span> · <SlotTier slot={red} fixedLanes={fixedLanes} />
                  </span>
                </button>
              </div>
            )
          })}
        </div>
        <p className={`tm-verdict ${verdict.tone}`}>{verdict.text}</p>

        {copyText !== null && (
          <div>
            <p className="tm-help">자동 복사가 안 됐어요. 아래 글을 길게 눌러 복사해 주세요.</p>
            <textarea className="tm-copybox" readOnly value={copyText} aria-label="복사할 결과" />
          </div>
        )}
      </section>
    </>
  )
}
