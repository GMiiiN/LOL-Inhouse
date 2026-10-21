// 챔피언 통계: seg 정렬 4가지, 같은 값의 정렬 순서, 15개/전체 보기
import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import type { ChampionStatItem, ChampionStats } from '../../api/publicTypes'
import { jsonResponse, mockFetch } from '../../test/mockFetch'
import { championsHandler, renderPage } from '../../test/statsFixtures'
import { sortChampions } from './championSort'
import { ChampionStatsPage } from './ChampionStatsPage'

const item = (championKey: string, picks: number, bans: number, wins: number, both: number): ChampionStatItem => ({
  championKey,
  picks,
  bans,
  wins,
  losses: picks - wins,
  setsWithPickOrBan: both,
})

// 아리: 픽 3(2승) 밴 1 / 제드: 픽 1(1승) 밴 5 / 럭스: 픽 2(0승) 밴 2 / 징크스: 럭스와 같은 값
const stats: ChampionStats = {
  totalSets: 10,
  champions: [item('Zed', 1, 5, 1, 6), item('Lux', 2, 2, 0, 4), item('Ahri', 3, 1, 2, 4), item('Jinx', 2, 2, 0, 4)],
}

const names = () => within(screen.getByRole('table')).getAllByRole('row').slice(1).map((r) => r.querySelector('td')!.textContent)

function setup(data: ChampionStats = stats, url = '/champions') {
  mockFetch({ 'GET /api/champions': championsHandler, 'GET /api/champion-stats': () => jsonResponse(200, data) })
  renderPage(<ChampionStatsPage />, url)
}

describe('sortChampions', () => {
  const nameOf = (k: string) => ({ Ahri: '아리', Zed: '제드', Lux: '럭스', Jinx: '징크스' })[k] ?? k
  it('기준 값이 같으면 밴+픽 횟수, 그다음 한국어 이름순', () => {
    // 아리·럭스·징크스는 setsWithPickOrBan 4로 같다. 밴+픽: 아리 4, 럭스 4, 징크스 4 -> 이름순(럭스, 아리, 징크스)
    const result = sortChampions(stats.champions, 'PICK_BAN_RATE', nameOf).map((c) => c.championKey)
    expect(result).toEqual(['Zed', 'Lux', 'Ahri', 'Jinx'])
  })
  it('승률 정렬은 승/(승+패)이고 픽이 없으면 0', () => {
    const result = sortChampions(stats.champions, 'WIN_RATE', nameOf).map((c) => c.championKey)
    expect(result).toEqual(['Zed', 'Ahri', 'Lux', 'Jinx'])
  })
})

describe('ChampionStatsPage', () => {
  it('기본은 밴픽률 순이고 seg로 픽·밴·승률 정렬을 바꾼다', async () => {
    setup()
    await screen.findByText('제드')
    expect(names()).toEqual(['제드', '럭스', '아리', '징크스'])

    await userEvent.click(screen.getByRole('button', { name: '픽' }))
    expect(names()).toEqual(['아리', '럭스', '징크스', '제드'])

    await userEvent.click(screen.getByRole('button', { name: '밴' }))
    expect(names()).toEqual(['제드', '럭스', '징크스', '아리'])

    await userEvent.click(screen.getByRole('button', { name: '승률' }))
    expect(names()).toEqual(['제드', '아리', '럭스', '징크스'])
  })

  it('정렬은 URL의 sort에서 읽어 새로고침해도 유지된다', async () => {
    setup(stats, '/champions?sort=BANS')
    await screen.findByText('제드')
    expect(names()).toEqual(['제드', '럭스', '징크스', '아리'])
    expect(screen.getByRole('button', { name: '밴' })).toHaveAttribute('aria-pressed', 'true')
  })

  it('머리에 총 경기 수와 밴픽률 도움말이 있다', async () => {
    setup()
    await screen.findByText('제드')
    expect(screen.getByText('총 10경기')).toBeInTheDocument()
    expect(screen.getByText(/밴이나 픽된 비율/)).toBeInTheDocument()
  })

  it('기본 15개만 보이고 "전체 N개 보기"를 누르면 모두 보인다. 행은 기록 링크', async () => {
    const many: ChampionStats = {
      totalSets: 30,
      champions: Array.from({ length: 20 }, (_, i) => item(`Champ${String(i).padStart(2, '0')}`, 1, 0, 1, 20 - i)),
    }
    setup(many)
    await screen.findByText('총 30경기')
    expect(await screen.findAllByRole('row')).toHaveLength(16) // 머리 + 15
    expect(screen.getAllByRole('link')[0]).toHaveAttribute('href', '/records?champion=Champ00')

    await userEvent.click(screen.getByRole('button', { name: '전체 20개 보기' }))
    expect(screen.getAllByRole('row')).toHaveLength(21)
    expect(screen.queryByRole('button', { name: /전체 20개 보기/ })).not.toBeInTheDocument()
  })
})
