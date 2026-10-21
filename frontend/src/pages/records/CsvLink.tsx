// CSV 내려받기 링크. 현재 필터(커서 제외)가 href에 그대로 실린다.
import { recordsExportUrl } from '../../api/public'
import type { RecordsQuery } from '../../api/publicTypes'
import '../../styles/records.css'

export function CsvLink({ query }: { query: RecordsQuery }) {
  return (
    <a className="csv-link" href={recordsExportUrl(query)} download>
      CSV
    </a>
  )
}
