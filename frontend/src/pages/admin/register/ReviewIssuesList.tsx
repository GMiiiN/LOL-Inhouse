// 오류(빨강, 저장 막음)와 경고(노랑, 저장 가능) 목록. check 응답과 422 응답이 모두 ReviewIssues 하나로 들어온다.
import type { ReviewIssues } from './reviewModel'

export function ReviewIssuesList({ issues }: { issues: ReviewIssues }) {
  if (issues.errors.length === 0 && issues.warnings.length === 0) return null
  return (
    <div className="review-issues">
      {issues.errors.length > 0 && (
        <div className="error-notice" role="alert" aria-label="저장을 막는 오류">
          <p>저장 전에 고쳐야 합니다</p>
          <ul>
            {issues.errors.map((message) => (
              <li key={message}>{message}</li>
            ))}
          </ul>
        </div>
      )}
      {issues.warnings.length > 0 && (
        <div className="warn-notice" role="status" aria-label="경고">
          <p>경고(저장은 할 수 있습니다)</p>
          <ul>
            {issues.warnings.map((message) => (
              <li key={message}>{message}</li>
            ))}
          </ul>
        </div>
      )}
    </div>
  )
}
