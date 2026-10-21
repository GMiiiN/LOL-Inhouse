// 블루(왼쪽)·라인·레드(오른쪽) 5줄 표. 블루 칸은 KDA·CS·골드·챔피언·이름, 레드 칸은 반대 순서.
import { Link } from 'react-router-dom'
import type { PlayerLine, SetView } from '../../api/publicTypes'
import { POSITION_LABEL } from '../../api/types'
import { formatGold } from '../../pages/stats/format'
import { ChampionIcon } from '../stats/ChampionIcon'
import '../../styles/records.css'
import { sidesOf } from './recordView'

function PlayerName({ line }: { line: PlayerLine }) {
  return (
    <Link className="sb-name" to={`/players/${line.playerId}`}>
      {line.displayName}
      {!line.registered && <span className="sb-unregistered"> (미등록)</span>}
    </Link>
  )
}

function Stats({ line }: { line: PlayerLine }) {
  return (
    <>
      <span className="sb-kda">
        {line.kills}/{line.deaths}/{line.assists}
      </span>
      <span className="sb-num">CS {line.cs}</span>
      <span className="sb-num">{formatGold(line.gold)}</span>
    </>
  )
}

type SetScoreboardProps = {
  set: SetView
  wide?: boolean // 세트 상세: 넓은 배치
}

export function SetScoreboard({ set, wide = false }: SetScoreboardProps) {
  const { blue, red } = sidesOf(set)
  return (
    <div className="scroll">
      <div className={`sb${wide ? ' sb-wide' : ''}`} role="table" aria-label="세트 스코어보드">
        {blue.players.map((bluePlayer, index) => {
          const redPlayer = red.players[index]
          return (
            <div key={bluePlayer.position} className="sb-row" role="row">
              <div className="sb-cell sb-blue" role="cell">
                <Stats line={bluePlayer} />
                <ChampionIcon championKey={bluePlayer.championKey} size="sm" />
                <PlayerName line={bluePlayer} />
              </div>
              <div className="sb-pos" role="cell">
                {POSITION_LABEL[bluePlayer.position]}
              </div>
              <div className="sb-cell sb-red" role="cell">
                <PlayerName line={redPlayer} />
                <ChampionIcon championKey={redPlayer.championKey} size="sm" />
                <Stats line={redPlayer} />
              </div>
            </div>
          )
        })}
      </div>
    </div>
  )
}
