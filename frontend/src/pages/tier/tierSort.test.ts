// 티어리스트 정렬 규칙 테스트: 한국어 가나다순, 칸 안에서는 상 → 중 → 하
import { describe, expect, it } from 'vitest'
import { lane, tierPlayer } from '../../test/tierListFixtures'
import { boardCell, compareNames, sortByName } from './tierSort'

describe('compareNames', () => {
  it('한글을 가나다순으로 정렬한다', () => {
    expect(['다람', '가나', '나비', '마루'].sort(compareNames)).toEqual(['가나', '나비', '다람', '마루'])
  })
})

describe('sortByName', () => {
  it('이름순으로 정렬한 새 배열을 돌려주고 원본은 그대로 둔다', () => {
    const players = [tierPlayer(1, '다람', {}), tierPlayer(2, '가나', {}), tierPlayer(3, '나비', {})]

    expect(sortByName(players).map((p) => p.displayName)).toEqual(['가나', '나비', '다람'])
    expect(players.map((p) => p.displayName)).toEqual(['다람', '가나', '나비'])
  })
})

describe('boardCell', () => {
  const players = [
    tierPlayer(1, '다람', { MID: lane(2, 'LOW', 49) }),
    tierPlayer(2, '나비', { MID: lane(2, 'HIGH', 55) }),
    tierPlayer(3, '가나', { MID: lane(2, 'LOW', 49) }),
    tierPlayer(4, '마루', { MID: lane(2, 'MID', 52) }),
    tierPlayer(5, '바다', { MID: lane(3, 'HIGH', 47), TOP: lane(2, 'HIGH', 55) }),
  ]

  it('같은 칸 안에서 상 → 중 → 하, 같은 우열이면 이름순으로 정렬한다', () => {
    expect(boardCell(players, 2, 'MID').map((e) => e.player.displayName)).toEqual(['나비', '마루', '가나', '다람'])
  })

  it('다른 티어·다른 라인 사람은 넣지 않는다', () => {
    expect(boardCell(players, 3, 'MID').map((e) => e.player.displayName)).toEqual(['바다'])
    expect(boardCell(players, 2, 'TOP').map((e) => e.player.displayName)).toEqual(['바다'])
    expect(boardCell(players, 1, 'ADC')).toEqual([])
  })
})
