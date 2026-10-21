// 대결 한 덩어리: "대결 N · 피어리스 방식 · A n : m B" 머리 + 세트 줄들. 점수는 필터와 무관한 전체 유효 세트 기준이다.
import { Link } from 'react-router-dom'
import type { DuelBlock as DuelBlockData, DuelHead } from '../../api/publicTypes'
import { SetRow } from './SetRow'
import '../../styles/records.css'
import { FEARLESS_LABEL } from './recordView'

// "A 2 : 1 B". 앞선 팀은 강조만 하고 승리라고 쓰지 않는다.
export function DuelScoreLine({ score }: { score: DuelHead['score'] }) {
  const lead = score.TEAM1 === score.TEAM2 ? null : score.TEAM1 > score.TEAM2 ? 'TEAM1' : 'TEAM2'
  return (
    <span className="duel-score" aria-label={`A팀 ${score.TEAM1} 대 ${score.TEAM2} B팀`}>
      A <strong className={lead === 'TEAM1' ? 'duel-lead' : undefined}>{score.TEAM1}</strong> :{' '}
      <strong className={lead === 'TEAM2' ? 'duel-lead' : undefined}>{score.TEAM2}</strong> B
    </span>
  )
}

type DuelBlockProps = {
  duel: DuelBlockData // sets는 필터에 맞는 세트만, score·totalSets는 전체 기준
  defaultExpanded?: boolean // 세트 줄 기본 펼침
  linkToDetail?: boolean // 기본 true: 머리의 "대결 N"이 /duels/:id 링크
}

export function DuelBlock({ duel, defaultExpanded = false, linkToDetail = true }: DuelBlockProps) {
  const title = `대결 ${duel.seq}`
  return (
    <section className="duel-block" aria-label={title}>
      <header className="duel-head">
        <h3>{linkToDetail ? <Link to={`/duels/${duel.duelId}`}>{title}</Link> : title}</h3>
        <span className="duel-mode">{FEARLESS_LABEL[duel.fearlessMode]}</span>
        <DuelScoreLine score={duel.score} />
        {duel.sets.length < duel.totalSets && (
          <span className="duel-match">
            조건에 맞는 세트 {duel.sets.length}/{duel.totalSets}
          </span>
        )}
      </header>
      {duel.sets.map((set) => (
        <SetRow key={set.setId} set={set} defaultExpanded={defaultExpanded} />
      ))}
    </section>
  )
}
