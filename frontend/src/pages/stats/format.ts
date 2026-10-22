// 공개 통계 화면 공용 표시 함수. 값이 없을 때(0세트·0경기)는 EM_DASH를 돌려준다.
export const EM_DASH = '—'

// 초 → "mm:ss" (60분 이상이면 분이 두 자리를 넘는다). 경기 시간을 모르는 기록(null)은 "—"
export function formatDuration(seconds: number | null): string {
  if (seconds === null) return EM_DASH
  const total = Math.max(0, Math.round(seconds))
  const mm = Math.floor(total / 60)
  const ss = total % 60
  return `${String(mm).padStart(2, '0')}:${String(ss).padStart(2, '0')}`
}

// 합계 ÷ 세트 수, 소수 1자리. sets가 0이면 "—"
export function formatPerGame(total: number, sets: number): string {
  return sets > 0 ? (total / sets).toFixed(1) : EM_DASH
}

// 골드 천 단위 쉼표(로캘과 무관하게 고정)
export function formatGold(gold: number): string {
  return Math.round(gold).toLocaleString('en-US')
}

// 0~1 비율 → 반올림 정수 % ("57%")
export function formatPercent(ratio: number): string {
  return `${Math.round(ratio * 100)}%`
}

// ISO 시각 → 한국 시간 "MM-DD HH:mm". 읽을 수 없으면 원문 그대로
export function formatSeoulDateTime(value: string): string {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  const parts = new Intl.DateTimeFormat('en-GB', {
    timeZone: 'Asia/Seoul',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hourCycle: 'h23',
  }).formatToParts(date)
  const get = (type: string) => parts.find((part) => part.type === type)?.value ?? ''
  return `${get('month')}-${get('day')} ${get('hour')}:${get('minute')}`
}
