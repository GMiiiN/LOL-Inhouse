// 대결 상세(/duels/:duelId): 머리(점수) + 세트 줄(기본 펼침) + 대결별 개인 결과 + 피어리스 제한.
import { useQuery } from '@tanstack/react-query'
import { useParams } from 'react-router-dom'
import { getDuel } from '../../api/public'
import type { DuelResult } from '../../api/publicTypes'
import { duelKey } from '../../api/queryKeys'
import { ErrorNotice } from '../../components/ErrorNotice'
import { DuelScoreLine } from '../../components/record/DuelBlock'
import { FEARLESS_LABEL } from '../../components/record/recordView'
import { FearlessStrip } from '../../components/record/FearlessStrip'
import { SetRow } from '../../components/record/SetRow'
import '../../styles/records.css'

const RESULT_LABEL: Record<DuelResult, string> = { WIN: '승', DRAW: '무', LOSS: '패' }
const TEAM_LETTER = { TEAM1: 'A', TEAM2: 'B' } as const

export function DuelDetailPage() {
  const duelId = Number(useParams().duelId)
  const valid = Number.isInteger(duelId) && duelId > 0
  const duel = useQuery({ queryKey: duelKey(duelId), queryFn: () => getDuel(duelId), enabled: valid })

  if (!valid) return <p className="muted-text">잘못된 주소예요.</p>
  if (duel.isError) return <ErrorNotice error={duel.error} />
  if (!duel.data) return <section><h1>대결 상세</h1><p className="muted-text">불러오는 중…</p></section>

  const data = duel.data
  return (
    <section className="duel-detail">
      <div className="page-title-row">
        <h1>
          대결 {data.seq}
        </h1>
      </div>
      <p className="set-detail-meta">
        {data.sessionDate} · {FEARLESS_LABEL[data.fearlessMode]} · <DuelScoreLine score={data.score} />
      </p>
      <div className="duel-sets">
        {data.sets.map((set) => (
          <SetRow key={set.setId} set={set} defaultExpanded />
        ))}
      </div>
      <h2 className="set-detail-sub">대결별 개인 결과</h2>
      <div className="scroll">
        <table className="personal-table">
          <thead>
            <tr>
              <th>선수</th>
              <th>팀</th>
              <th>세트</th>
              <th>승</th>
              <th>패</th>
              <th>결과</th>
            </tr>
          </thead>
          <tbody>
            {data.personal.map((p) => (
              <tr key={p.playerId}>
                <td>{p.displayName}</td>
                <td>{p.teams.map((t) => TEAM_LETTER[t]).join('→')}</td>
                <td>{p.wins + p.losses}</td>
                <td>{p.wins}</td>
                <td>{p.losses}</td>
                <td>{RESULT_LABEL[p.result]}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <h2 className="set-detail-sub">피어리스 제한 (다음 세트 기준)</h2>
      <FearlessStrip championKeys={data.fearless.inherited} title="이전 대결에서 이어온 제한" defaultOpen />
      <FearlessStrip championKeys={data.fearless.picked} title="이 대결에서 픽된 챔피언" defaultOpen />
    </section>
  )
}
