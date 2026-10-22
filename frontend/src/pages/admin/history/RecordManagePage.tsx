// 기록 관리(/admin/history): 삭제된 세트 목록과 복구. 변경 이력·사유는 만들지 않는다(m6-design §14).
import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { ApiError } from '../../../api/client'
import { DELETED_SETS_KEY } from '../../../api/queryKeys'
import { listDeletedSets } from '../../../api/recordEdit'
import type { DeletedSetRow, RestoreResult } from '../../../api/recordEditTypes'
import { ErrorNotice } from '../../../components/ErrorNotice'
import { OriginalImageButton } from '../../set/OriginalImageButton'
import { formatDuration, formatSeoulDateTime } from '../../stats/format'
import { useRestoreSet } from './useSetDeleteRestore'
import '../../../styles/records.css'

type RestoredNotice = { label: string; result: RestoreResult }

function BlockerLink({ setId, label }: { setId: number; label: string }) {
  return (
    <Link to={`/sets/${setId}`} target="_blank" rel="noreferrer">
      {label}
    </Link>
  )
}

function DeletedSetRowView({
  row,
  onRestored,
}: {
  row: DeletedSetRow
  onRestored: (notice: RestoredNotice) => void
}) {
  const restore = useRestoreSet()
  const { fingerprintSetId, setNoSetId } = row.blockers
  const blocked = fingerprintSetId !== null || setNoSetId !== null
  const error = restore.error
  // 목록을 본 뒤 생긴 충돌은 오류에 실린 막는 세트 id로 링크를 보여 준다
  const errorBlockerId = error instanceof ApiError ? error.existingSetId : undefined

  return (
    <>
      <tr>
        <td>{row.sessionStartDate}</td>
        <td>대결 {row.duelSeq}</td>
        <td>{row.setNo}세트</td>
        <td>{row.shotDate ?? '—'}</td>
        <td>{formatDuration(row.durationSec)}</td>
        <td>
          {row.team1Kills} : {row.team2Kills}
        </td>
        <td>{formatSeoulDateTime(row.deletedAt)}</td>
        <td className="deleted-set-actions">
          <OriginalImageButton setId={row.setId} />
          {!blocked && (
            <button
              type="button"
              disabled={restore.isPending}
              onClick={() =>
                restore.mutate(
                  { setId: row.setId, duelId: row.duelId },
                  {
                    onSuccess: (result) =>
                      onRestored({ label: `대결 ${row.duelSeq} · ${row.setNo}세트`, result }),
                  },
                )
              }
            >
              {restore.isPending ? '복구 중…' : '복구'}
            </button>
          )}
        </td>
      </tr>
      {(blocked || row.fearlessConflicts.length > 0 || error) && (
        <tr className="deleted-set-note-row">
          <td colSpan={8}>
            {fingerprintSetId !== null && (
              <p>
                같은 경기가 이미 등록돼 있어 복구할 수 없어요. 막는 세트:{' '}
                <BlockerLink setId={fingerprintSetId} label="같은 경기 세트 보기" />
              </p>
            )}
            {setNoSetId !== null && (
              <p>
                같은 대결에 같은 번호 세트가 있어 복구할 수 없어요. 막는 세트:{' '}
                <BlockerLink setId={setNoSetId} label="같은 번호 세트 보기" />
              </p>
            )}
            {blocked && <p className="muted-text">막는 세트를 먼저 정리하면 복구할 수 있어요.</p>}
            {row.fearlessConflicts.length > 0 && (
              <div className="warn-notice">
                <p>복구하면 다른 세트와 피어리스 챔피언이 겹쳐요. 그래도 복구할 수 있어요.</p>
                <ul>
                  {row.fearlessConflicts.map((c) => (
                    <li key={`${c.setId}-${c.championKey}`}>
                      대결 {c.duelSeq} · {c.setNo}세트 · {c.championKey}
                    </li>
                  ))}
                </ul>
              </div>
            )}
            <ErrorNotice error={error} />
            {errorBlockerId !== undefined && (
              <p>
                <BlockerLink setId={errorBlockerId} label="막는 세트 보기" />
              </p>
            )}
          </td>
        </tr>
      )}
    </>
  )
}

export function RecordManagePage() {
  const deleted = useQuery({ queryKey: DELETED_SETS_KEY, queryFn: listDeletedSets })
  const [notice, setNotice] = useState<RestoredNotice | null>(null)

  return (
    <section>
      <h1>기록 관리</h1>
      <h2>삭제된 세트</h2>
      {deleted.isPending && <p className="muted-text">불러오는 중입니다...</p>}
      <ErrorNotice error={deleted.error} />
      {notice && (
        <div className="warn-notice" role="status">
          <p>{notice.label}를 복구했어요.</p>
          {notice.result.warnings.length > 0 && (
            <ul>
              {notice.result.warnings.map((w) => (
                <li key={w}>{w}</li>
              ))}
            </ul>
          )}
          {notice.result.fearlessConflicts.length > 0 && (
            <ul>
              {notice.result.fearlessConflicts.map((c) => (
                <li key={`${c.setId}-${c.championKey}`}>
                  피어리스 겹침: 대결 {c.duelSeq} · {c.setNo}세트 · {c.championKey}
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
      {deleted.data && deleted.data.length === 0 && <p className="muted-text">삭제된 세트가 없어요</p>}
      {deleted.data && deleted.data.length > 0 && (
        <table className="data-table data-table-large set-table" aria-label="삭제된 세트">
          <thead>
            <tr>
              <th>묶음</th>
              <th>대결</th>
              <th>세트</th>
              <th>결과창 날짜</th>
              <th>경기 시간</th>
              <th>킬 (A : B)</th>
              <th>삭제 시각</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {deleted.data.map((row) => (
              <DeletedSetRowView key={row.setId} row={row} onRestored={setNotice} />
            ))}
          </tbody>
        </table>
      )}
    </section>
  )
}
