// 꼭 같은 팀 / 꼭 다른 팀 조건(kind로 구분, 같은 UI): 참가자 중에서만 고르고 최대 5쌍. 빠진 사람의 쌍은 상태 훅이 지운다.
import { useState } from 'react'
import { MAX_TOGETHER_PAIRS, type TogetherPair } from '../../api/tierTypes'

type Props = {
  kind?: 'together' | 'apart'
  participants: number[]
  pairs: TogetherPair[]
  nameOf: (playerId: number) => string
  onAdd: (a: number | null, b: number | null) => string | null
  onRemove: (index: number) => void
}

export function TogetherPairs({ kind = 'together', participants, pairs, nameOf, onAdd, onRemove }: Props) {
  const [a, setA] = useState('')
  const [b, setB] = useState('')
  const [error, setError] = useState<string | null>(null)
  const apart = kind === 'apart'
  const prefix = apart ? '다른 팀 ' : ''
  const toId = (value: string) => (value ? Number(value) : null)

  function add() {
    const message = onAdd(toId(a), toId(b))
    setError(message)
    if (!message) {
      setA('')
      setB('')
    }
  }

  const options = participants.map((id) => (
    <option key={id} value={id}>
      {nameOf(id)}
    </option>
  ))
  return (
    <>
      <h3>{apart ? '꼭 다른 팀' : '꼭 같은 팀'}</h3>
      <p className="tm-help">
        {apart ? '서로 다른 팀이어야 하는 두 사람을 고르세요.' : '듀오처럼 같은 팀이어야 하는 두 사람을 고르세요.'} 참가자 중에서만 고를 수 있어요(최대 {MAX_TOGETHER_PAIRS}쌍).
      </p>
      {pairs.length > 0 && (
        <div className="tm-pairs">
          {pairs.map(([x, y], i) => (
            <span key={`${x}-${y}`} className="tm-pair">
              {nameOf(x)} {apart ? '↔' : '+'} {nameOf(y)}
              <button type="button" aria-label={`${prefix}${nameOf(x)} + ${nameOf(y)} 조건 삭제`} onClick={() => onRemove(i)}>
                ✕
              </button>
            </span>
          ))}
        </div>
      )}
      <div className="tm-pairform">
        <select aria-label={`${prefix}첫 번째 사람`} value={a} onChange={(event) => setA(event.target.value)}>
          <option value="">사람 1</option>
          {options}
        </select>
        <span className="muted">+</span>
        <select aria-label={`${prefix}두 번째 사람`} value={b} onChange={(event) => setB(event.target.value)}>
          <option value="">사람 2</option>
          {options}
        </select>
        <button type="button" className="tm-btn small" aria-label={apart ? '다른 팀 조건 추가' : undefined} onClick={add}>
          추가
        </button>
      </div>
      {error && (
        <p className="tm-err" role="alert">
          {error}
        </p>
      )}
    </>
  )
}
