// 기간(from~to, 내전 시작일 기준) 입력. 값은 URL 쿼리에 저장된다(usePeriodParams).
import { usePeriodParams } from '../../pages/stats/usePeriodParams'
import '../../styles/stats-common.css'

export function PeriodFilter() {
  const { period, setPeriod, clearPeriod } = usePeriodParams()
  const empty = !period.from && !period.to

  return (
    <div className="period-filter" role="group" aria-label="기간 필터">
      <label>
        시작일
        <input
          type="date"
          value={period.from ?? ''}
          max={period.to}
          onChange={(e) => setPeriod({ from: e.target.value })}
        />
      </label>
      <span aria-hidden="true">~</span>
      <label>
        종료일
        <input
          type="date"
          value={period.to ?? ''}
          min={period.from}
          onChange={(e) => setPeriod({ to: e.target.value })}
        />
      </label>
      <button type="button" onClick={clearPeriod} disabled={empty}>
        기간 지우기
      </button>
    </div>
  )
}
