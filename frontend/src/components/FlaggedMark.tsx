// 요주 인물 표시: 이름 뒤에 붙는 벌 아이콘. flagged가 아니면 아무것도 그리지 않는다.
import beeUrl from '../assets/bee.webp'
import '../styles/tier.css'

export function FlaggedMark({ flagged }: { flagged: boolean }) {
  return flagged ? <img className="bee" src={beeUrl} alt="요주 인물" title="요주 인물" /> : null
}
