// 코인토스: 나온 면이 "처음 던질 때 블루 칸에 있던 묶음(t1)"의 진영이 된다. 블루 칸은 A팀이다.
// 난수와 지연 시간은 props로 주입할 수 있다(테스트). 움직임 줄이기 설정이면 애니메이션 없이 바로 결과를 낸다.
import { useEffect, useRef, useState, type CSSProperties } from 'react'
import type { Side } from './teamResultModel'

type Props = {
  blueNames: string[]
  redNames: string[]
  // 나온 면에 맞추려면 칸을 맞바꿔야 하면 true로 부른다
  onSettle: (needsSwap: boolean) => void
  random?: () => number
  delayMs?: number
}

type Toss = { t1: string[]; t2: string[]; side: Side; done: boolean }

const SIDE_LABEL: Record<Side, string> = { blue: '블루', red: '레드' }
const sameNames = (a: string[], b: string[]) => a.length === b.length && a.every((name, i) => name === b[i])
const prefersReducedMotion = () =>
  typeof window !== 'undefined' && typeof window.matchMedia === 'function'
    ? window.matchMedia('(prefers-reduced-motion: reduce)').matches
    : false

export function CoinToss({ blueNames, redNames, onSettle, random = Math.random, delayMs = 1500 }: Props) {
  const [toss, setToss] = useState<Toss | null>(null)
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null)

  useEffect(
    () => () => {
      if (timer.current) clearTimeout(timer.current)
    },
    [],
  )

  function start() {
    if (timer.current) return // 던지는 중에는 무시
    // 처음 던질 때 블루 칸의 묶음을 t1로 기억한다. 다시 던져도 t1은 그대로다.
    const t1 = toss?.t1 ?? blueNames
    const t2 = toss?.t2 ?? redNames
    const side: Side = random() < 0.5 ? 'red' : 'blue'
    const t1InBlueNow = sameNames(blueNames, t1)
    const finish = () => {
      timer.current = null
      setToss({ t1, t2, side, done: true })
      onSettle(t1InBlueNow !== (side === 'blue'))
    }
    setToss({ t1, t2, side, done: false })
    if (prefersReducedMotion() || delayMs <= 0) finish()
    else timer.current = setTimeout(finish, delayMs)
  }

  const label = toss ? SIDE_LABEL[toss.side] : ''
  const teamOfT1 = toss?.side === 'blue' ? 'A팀' : 'B팀'

  return (
    <div className="tm-coin-area">
      <button type="button" className="tm-btn small" onClick={start} disabled={toss !== null && !toss.done}>
        {toss?.done ? '다시 던지기' : '코인토스'}
      </button>
      {toss && (
        <div className="tm-coinbox" data-testid="coinbox">
          <div className="tm-coinwrap">
            <div
              className={`tm-coin${toss.done ? '' : ' flip'}`}
              style={{ '--end': toss.side === 'blue' ? '1800deg' : '1980deg' } as CSSProperties}
            >
              <span className="tm-face front">블루</span>
              <span className="tm-face back">레드</span>
            </div>
          </div>
          <div className="tm-coininfo">
            <div className="tm-cteams">
              <span>
                <b>묶음 1</b> {toss.t1.join(', ')}
              </span>
              <span>
                <b>묶음 2</b> {toss.t2.join(', ')}
              </span>
            </div>
            {toss.done ? (
              <p className="tm-cres" role="status">
                <b className={toss.side === 'blue' ? 'tm-tb' : 'tm-tr'}>{label}</b>가 나왔어요. 첫 묶음({toss.t1.join(', ')})이 {label}, 즉 {teamOfT1}이에요.
              </p>
            ) : (
              <p className="tm-cres muted">동전을 던지는 중… 나온 면이 첫 묶음의 진영이에요.</p>
            )}
          </div>
        </div>
      )}
    </div>
  )
}
