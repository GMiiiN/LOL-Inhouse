// 세트 한 줄(N세트·진영·승리 태그·시간·양 팀 픽 아이콘·팀 킬) + 펼치면 스코어보드·밴·피어리스 제한.
// 기록 목록, 대결 상세, 홈, 프로필 최근 경기가 함께 쓴다. 블루가 항상 왼쪽이다.
import { useState } from 'react'
import { Link } from 'react-router-dom'
import type { PlayerLine, SetView } from '../../api/publicTypes'
import { formatDuration } from '../../pages/stats/format'
import { ChampionIcon } from '../stats/ChampionIcon'
import { TeamLabel } from '../stats/TeamLabel'
import { BanStrip } from './BanStrip'
import { FearlessStrip } from './FearlessStrip'
import { SetScoreboard } from './SetScoreboard'
import { sidesOf } from './recordView'
import '../../styles/records.css'

type SetRowProps = {
  set: SetView
  defaultExpanded?: boolean // 기본 접힘
  showDuelLink?: boolean // 홈·프로필처럼 대결 맥락이 없는 곳에서 "대결 N" 링크를 함께 보여 준다
  showNames?: boolean // 접힌 줄에서도 챔피언 아이콘 아래에 선수 이름을 보여 준다(홈·프로필 최근 경기)
}

export function SetRow({ set, defaultExpanded = false, showDuelLink = false, showNames = false }: SetRowProps) {
  const [expanded, setExpanded] = useState(defaultExpanded)
  const { blue, red } = sidesOf(set)
  const team1 = set.teams[0]
  const winnerName = set.winnerTeam === 'TEAM1' ? 'A팀' : 'B팀'
  const panelId = `set-row-panel-${set.setId}`
  // 양 팀 픽과 킬. 이름을 함께 보일 때는 줄 폭이 넉넉하면 한 줄, 좁으면 아래 줄로 내린다(records.css 컨테이너 쿼리)
  const lineup = (
    <>
      <span className="set-row-picks set-row-picks-blue" data-testid="picks-blue">
        {blue.players.map((p) => (
          <Pick key={p.position} line={p} showName={showNames} />
        ))}
      </span>
      <span className="set-row-kills" aria-label="팀 킬 블루 대 레드">
        <span className="team-blue">{blue.displayed.kills}</span> :{' '}
        <span className="team-red">{red.displayed.kills}</span>
      </span>
      <span className="set-row-picks set-row-picks-red" data-testid="picks-red">
        {red.players.map((p) => (
          <Pick key={p.position} line={p} showName={showNames} />
        ))}
      </span>
    </>
  )

  return (
    <div className={`set-row${expanded ? ' open' : ''}`}>
      <div className={`set-row-head${showNames ? ' with-names' : ''}`}>
        <button
          type="button"
          className="set-row-toggle"
          aria-expanded={expanded}
          aria-controls={panelId}
          aria-label={`${set.setNo}세트 ${expanded ? '접기' : '펼치기'}`}
          onClick={() => setExpanded((value) => !value)}
        >
          <span className="set-row-caret" aria-hidden="true">
            {expanded ? '▾' : '▸'}
          </span>
          <span className="set-row-no">{set.setNo}세트</span>
        </button>
        <TeamLabel team="TEAM1" side={team1.side} />
        <span className="wtag">{winnerName} 승</span>
        <span className="set-row-time">{formatDuration(set.durationSec)}</span>
        {showNames ? <div className="set-row-lineup">{lineup}</div> : lineup}
        {showDuelLink && (
          <Link className="set-row-link" to={`/duels/${set.duelId}`}>
            대결 {set.duelSeq}
          </Link>
        )}
        <Link className="set-row-link" to={`/sets/${set.setId}`}>
          세트 상세
        </Link>
      </div>
      {expanded && (
        <div className="set-row-panel" id={panelId}>
          <SetScoreboard set={set} />
          <BanStrip bans={set.bans} />
          <FearlessStrip championKeys={set.fearlessRestricted} />
        </div>
      )}
    </div>
  )
}

function Pick({ line, showName }: { line: PlayerLine; showName: boolean }) {
  if (!showName) return <ChampionIcon championKey={line.championKey} size="sm" />
  return (
    <span className="set-row-pick" title={line.displayName}>
      <ChampionIcon championKey={line.championKey} size="sm" />
      <span className="set-row-pick-name">{line.displayName}</span>
    </span>
  )
}
