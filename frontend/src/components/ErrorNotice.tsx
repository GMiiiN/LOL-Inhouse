// API 오류를 한국어 안내 상자로 보여 준다(필드 오류·별칭 충돌 목록 포함)
import { describeError } from '../api/errorMessages'

export function ErrorNotice({ error }: { error: unknown }) {
  if (!error) return null
  const view = describeError(error)
  return (
    <div className="error-notice" role="alert">
      <p>{view.message}</p>
      {view.conflicts.length > 0 && (
        <ul>
          {view.conflicts.map((line) => (
            <li key={line}>{line}</li>
          ))}
        </ul>
      )}
      {view.fieldErrors.length > 0 && (
        <ul>
          {view.fieldErrors.map((line) => (
            <li key={line}>{line}</li>
          ))}
        </ul>
      )}
    </div>
  )
}
