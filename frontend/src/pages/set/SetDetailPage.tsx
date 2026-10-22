// 세트 상세(/sets/:setId): 머리 요약 + 넓은 스코어보드 + 밴 + 피어리스 제한. 담당자 로그인이면 원본 보기.
import { useQuery } from '@tanstack/react-query'
import { Link, useLocation, useParams } from 'react-router-dom'
import { getSet } from '../../api/public'
import type { TeamView } from '../../api/publicTypes'
import type { SetEditSavedState } from '../../api/recordEditTypes'
import { setKey } from '../../api/queryKeys'
import { ErrorNotice } from '../../components/ErrorNotice'
import { BanStrip } from '../../components/record/BanStrip'
import { FearlessStrip } from '../../components/record/FearlessStrip'
import { SetScoreboard } from '../../components/record/SetScoreboard'
import { sidesOf } from '../../components/record/recordView'
import { TeamLabel } from '../../components/stats/TeamLabel'
import { formatDuration, formatGold } from '../stats/format'
import { OriginalImageButton } from './OriginalImageButton'
import { SetAdminActions } from './SetAdminActions'
import '../../styles/records.css'

function TeamTotals({ team }: { team: TeamView }) {
  const d = team.displayed
  return (
    <li className="set-total">
      <TeamLabel team={team.team} side={team.side} /> {d.kills}/{d.deaths}/{d.assists} · {formatGold(d.gold)} 골드
    </li>
  )
}

export function SetDetailPage() {
  const setId = Number(useParams().setId)
  const valid = Number.isInteger(setId) && setId > 0
  const saved = useLocation().state as SetEditSavedState | null // 수정 저장 직후에만 있다
  const set = useQuery({ queryKey: setKey(setId), queryFn: () => getSet(setId), enabled: valid })

  if (!valid) return <p className="muted-text">잘못된 주소예요.</p>
  if (set.isError) return <ErrorNotice error={set.error} />
  if (!set.data) return <section><h1>세트 상세</h1><p className="muted-text">불러오는 중…</p></section>

  const data = set.data
  const { blue, red } = sidesOf(data)
  return (
    <section className="set-detail">
      <div className="page-title-row">
        <h1>세트 상세</h1>
        <OriginalImageButton setId={data.setId} />
        <SetAdminActions
          setId={data.setId}
          sessionId={data.sessionId}
          duelId={data.duelId}
          duelSeq={data.duelSeq}
          setNo={data.setNo}
        />
      </div>
      {saved && (
        <div className="warn-notice" role="status" aria-label="수정 결과">
          <p>수정했어요.</p>
          <ul>
            {saved.editWarnings.map((warning) => (
              <li key={warning}>{warning}</li>
            ))}
            {saved.fearlessConflicts.map((conflict) => (
              <li key={`${conflict.setId}:${conflict.championKey}`}>
                뒤 세트 피어리스 충돌: <Link to={`/sets/${conflict.setId}`}>대결 {conflict.duelSeq} · {conflict.setNo}세트</Link>{' '}
                ({conflict.championKey})
              </li>
            ))}
          </ul>
        </div>
      )}
      <p className="set-detail-meta">
        {data.sessionDate} · <Link to={`/duels/${data.duelId}`}>대결 {data.duelSeq}</Link> · {data.setNo}세트
      </p>
      <p className="set-detail-meta">
        결과창 날짜 {data.shotDate} · 경기 시간 {formatDuration(data.durationSec)} ·{' '}
        <span className="wtag">{data.winnerTeam === 'TEAM1' ? 'A팀' : 'B팀'} 승</span>
      </p>
      <ul className="set-totals" aria-label="팀 합계">
        <TeamTotals team={blue} />
        <TeamTotals team={red} />
      </ul>
      <SetScoreboard set={data} wide />
      <h2 className="set-detail-sub">밴</h2>
      <BanStrip bans={data.bans} />
      <FearlessStrip championKeys={data.fearlessRestricted} defaultOpen />
    </section>
  )
}
