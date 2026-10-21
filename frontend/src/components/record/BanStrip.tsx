// 밴 10개를 B1 → R1 → … → R5 순서(턴 오름차순)로 순번 라벨·화살표와 함께 보여 준다.
import { Fragment } from 'react'
import type { BanView } from '../../api/publicTypes'
import { ChampionIcon } from '../stats/ChampionIcon'
import '../../styles/records.css'
import { banLabels } from './recordView'

export function BanStrip({ bans }: { bans: BanView[] }) {
  const labeled = banLabels(bans)
  const rows = [labeled.slice(0, 6), labeled.slice(6)]
  return (
    <div className="ban-strip" role="group" aria-label="밴">
      {rows.map(
        (row, index) =>
          row.length > 0 && (
            <div key={index} className="ban-row">
              {row.map(({ ban, label }, i) => (
                <Fragment key={ban.turn}>
                  {(i > 0 || index > 0) && (
                    <span className="ban-arrow" aria-hidden="true">
                      →
                    </span>
                  )}
                  <span className={`ban-item ${ban.side === 'BLUE' ? 'ban-blue' : 'ban-red'}`}>
                    <span className="ban-label">{label}</span>
                    <ChampionIcon championKey={ban.championKey} size="sm" />
                  </span>
                </Fragment>
              ))}
            </div>
          ),
      )}
    </div>
  )
}
