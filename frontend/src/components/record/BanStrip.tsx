// 밴 10개를 B1 R1 B2 R2 B3 R3 / B4 R4 B5 R5 순서(턴 오름차순)로 순번 라벨과 함께 보여 준다.
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
              {row.map(({ ban, label }) => (
                <span key={ban.turn} className={`ban-item ${ban.side === 'BLUE' ? 'ban-blue' : 'ban-red'}`}>
                  <span className="ban-label">{label}</span>
                  <ChampionIcon championKey={ban.championKey} size="sm" />
                </span>
              ))}
            </div>
          ),
      )}
    </div>
  )
}
