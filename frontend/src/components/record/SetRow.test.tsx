import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { jsonResponse, mockFetch } from '../../test/mockFetch'
import { makeDuel, makeSet } from '../../test/recordFixtures'
import { DuelBlock } from './DuelBlock'
import { SetRow } from './SetRow'

function renderWith(ui: React.ReactElement) {
  mockFetch({ 'GET /api/champions': () => jsonResponse(200, { version: '1', champions: [] }) })
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter>{ui}</MemoryRouter>
    </QueryClientProvider>,
  )
}

afterEach(() => vi.unstubAllGlobals())

describe('SetRow', () => {
  it('접힌 채로 시작하고 누르면 펼치고 다시 누르면 접는다', async () => {
    renderWith(<SetRow set={makeSet()} />)
    const toggle = screen.getByRole('button', { name: /1세트/ })
    expect(toggle).toHaveAttribute('aria-expanded', 'false')
    expect(screen.queryByRole('table', { name: '세트 스코어보드' })).toBeNull()

    await userEvent.click(toggle)
    expect(toggle).toHaveAttribute('aria-expanded', 'true')
    expect(screen.getByRole('table', { name: '세트 스코어보드' })).toBeInTheDocument()

    await userEvent.click(toggle)
    expect(toggle).toHaveAttribute('aria-expanded', 'false')
  })

  it('A팀이 블루면 A팀 선수가 왼쪽, 레드(짝수 세트)면 B팀 선수가 왼쪽이다', async () => {
    const { unmount } = renderWith(<SetRow set={makeSet({ team1Side: 'BLUE' })} defaultExpanded />)
    let row = screen.getAllByRole('row')[0]
    let cells = within(row).getAllByRole('cell')
    expect(cells[0]).toHaveTextContent('ATOP')
    expect(cells[2]).toHaveTextContent('BTOP')
    expect(screen.getByTestId('picks-blue')).toBeInTheDocument()
    expect(screen.getByLabelText('팀 킬 블루 대 레드')).toHaveTextContent('23 : 17')
    unmount()

    renderWith(<SetRow set={makeSet({ setNo: 2, team1Side: 'RED' })} defaultExpanded />)
    row = screen.getAllByRole('row')[0]
    cells = within(row).getAllByRole('cell')
    expect(cells[0]).toHaveTextContent('BTOP')
    expect(cells[2]).toHaveTextContent('ATOP')
    // 블루 킬이 왼쪽: 이 세트는 B팀(17)이 블루
    expect(screen.getByLabelText('팀 킬 블루 대 레드')).toHaveTextContent('17 : 23')
  })

  it('밴을 B1 R1 B2 R2 ... B5 R5 라벨로 보여 준다', () => {
    renderWith(<SetRow set={makeSet()} defaultExpanded />)
    const labels = Array.from(document.querySelectorAll('.ban-label')).map((el) => el.textContent)
    expect(labels).toEqual(['B1', 'R1', 'B2', 'R2', 'B3', 'R3', 'B4', 'R4', 'B5', 'R5'])
  })

  it('피어리스 제한 줄에 개수를 쓴다', () => {
    renderWith(<SetRow set={makeSet()} defaultExpanded />)
    expect(screen.getByText('이 세트 시점 제한 2개')).toBeInTheDocument()
  })

  it('승리 팀 태그와 경기 시간을 보여 준다', () => {
    renderWith(<SetRow set={makeSet({ winnerTeam: 'TEAM2' })} />)
    expect(screen.getByText('B팀 승')).toBeInTheDocument()
    expect(screen.getByText('31:05')).toBeInTheDocument()
  })
})

describe('DuelBlock', () => {
  it('점수와 필터된 세트 수를 보여 준다(승리 단정 없음)', () => {
    renderWith(<DuelBlock duel={makeDuel({ totalSets: 4 })} />)
    expect(screen.getByLabelText('A팀 2 대 1 B팀')).toBeInTheDocument()
    expect(screen.getByText('조건에 맞는 세트 1/4')).toBeInTheDocument()
    expect(screen.getByText('피어리스 초기화')).toBeInTheDocument()
    expect(screen.queryByText(/승리/)).toBeNull()
  })
})
