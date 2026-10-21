// 점수표 저장 순서 보장(설계 §11 P2-5): PUT/초기화는 한 번에 하나만 보낸다.
// 진행 중에 새 요청이 오면 "대기값" 하나로 합치고(최신이 이긴다), 끝난 뒤 한 번 더 보낸다.
// 초기화도 같은 큐를 쓰므로 먼저 보낸 PUT보다 앞서 도착해 덮어쓰이는 일이 없다.
// 큐는 화면(컴포넌트)이 아니라 모듈에 하나만 있다. 응답을 기다리다 화면을 떠났다 돌아와도 같은 큐를 이어받아,
// "이전 요청 -> 최신 요청 -> 이전 대기값" 순서로 옛 값이 최신을 덮는 일이 없다.
import { useQueryClient, type QueryClient } from '@tanstack/react-query'
import { useCallback, useEffect, useSyncExternalStore } from 'react'
import { putScoreTable, resetScoreTable } from '../../../api/tier'
import { SCORE_TABLE_KEY, TIER_LIST_KEY } from '../../../api/queryKeys'
import type { ScoreTable, ScoreTableResponse } from '../../../api/tierTypes'

// reset 요청은 끝났을 때 불릴 콜백을 가진다(화면이 이미 사라졌어도 무해하다)
export type SaveOp =
  | { kind: 'put'; table: ScoreTable }
  | { kind: 'reset'; onDone: (table: ScoreTableResponse | undefined) => void; onFailed: () => void }
export type SaveStatus = 'idle' | 'saving' | 'saved' | 'error'

type Snapshot = { status: SaveStatus; error: unknown }

let pending: SaveOp | null = null
let running = false
let snapshot: Snapshot = { status: 'idle', error: null }
let client: QueryClient | null = null
const listeners = new Set<() => void>()

function publish(next: Snapshot) {
  snapshot = next
  listeners.forEach((listener) => listener())
}

async function drain() {
  if (running) return // 이미 보내는 중이면 대기값만 남기고 돌아간다
  running = true
  let lastError: unknown = null
  try {
    while (pending) {
      const op = pending
      pending = null
      publish({ status: 'saving', error: null })
      try {
        if (op.kind === 'put') {
          await putScoreTable(op.table)
        } else {
          op.onDone(await resetScoreTable())
        }
        lastError = null
      } catch (caught) {
        lastError = caught
        if (op.kind === 'reset') op.onFailed()
      }
    }
  } finally {
    running = false
  }
  publish({ status: lastError ? 'error' : 'saved', error: lastError })
  if (!lastError && client) {
    // 점수표를 쓰는 조회(티어리스트의 점수, 점수표 캐시)를 새로 받는다
    void client.invalidateQueries({ queryKey: TIER_LIST_KEY })
    void client.invalidateQueries({ queryKey: SCORE_TABLE_KEY })
  }
}

function enqueue(op: SaveOp) {
  pending = op // 대기 중이던 것은 이 최신 요청으로 대체된다
  void drain()
}

const subscribe = (listener: () => void) => {
  listeners.add(listener)
  return () => listeners.delete(listener)
}
const getSnapshot = () => snapshot

// 테스트 전용: 모듈에 남은 큐 상태를 비운다
export function resetScoreSaverForTests() {
  pending = null
  running = false
  snapshot = { status: 'idle', error: null }
  client = null
  listeners.clear()
}

export function useScoreSaver() {
  const queryClient = useQueryClient()
  useEffect(() => {
    client = queryClient
  }, [queryClient])
  const { status, error } = useSyncExternalStore(subscribe, getSnapshot)
  const add = useCallback((op: SaveOp) => enqueue(op), [])
  return { status, error, enqueue: add }
}
