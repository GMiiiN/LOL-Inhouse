// 대결별 개인 결과: 합계(N승 N무 N패)와 최근 대결 목록. 각 줄은 대결 상세로 간다.
import { Link } from 'react-router-dom'
import type { DuelResult, PlayerRecord } from '../../api/publicTypes'
import '../../styles/stats.css'

const RESULT_LABEL: Record<DuelResult, string> = { WIN: '승', DRAW: '무', LOSS: '패' }
const RESULT_CLASS: Record<DuelResult, string> = { WIN: 'stat-result-win', DRAW: '', LOSS: 'stat-result-loss' }

export function DuelResultList({ duels }: { duels: PlayerRecord['duels'] }) {
  return (
    <div>
      <p className="stat-duel-total">
        {duels.wins}승 {duels.draws}무 {duels.losses}패
      </p>
      <div className="stat-table-scroll">
        <table className="stat-table" aria-label="대결별 개인 결과">
          <thead>
            <tr>
              <th>내전 날짜</th>
              <th>대결</th>
              <th className="num">세트 승·패</th>
              <th>결과</th>
            </tr>
          </thead>
          <tbody>
            {duels.items.map((item) => (
              <tr key={item.duelId}>
                <td>
                  <Link className="stats-link" to={`/duels/${item.duelId}`}>
                    {item.sessionDate}
                  </Link>
                </td>
                <td>대결 {item.seq}</td>
                <td className="num">
                  {item.wins}승 {item.losses}패
                </td>
                <td className={RESULT_CLASS[item.result]}>{RESULT_LABEL[item.result]}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}
