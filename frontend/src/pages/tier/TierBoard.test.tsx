// 티어표·사람별 표 렌더 테스트: 칸 배치, 칸 안 정렬, ▲▼, 편집 버튼, 삭제 두 번 확인
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { lane, tierPlayer } from '../../test/tierListFixtures'
import { TierBoard } from './TierBoard'
import { TierPeopleTable } from './TierPeopleTable'

const players = [
  tierPlayer(1, '다람', { MID: lane(2, 'LOW', 49), TOP: lane(3, 'MID', 44) }),
  tierPlayer(2, '나비', { MID: lane(2, 'HIGH', 55, { direction: 'UP', fromTier: 3, fromSub: 'MID' }) }),
  tierPlayer(3, '가나', {}),
]

describe('TierBoard', () => {
  it('각 칸(티어 × 라인)에 맞는 사람만, 상 → 중 → 하 순서로 이름과 점수를 보여 준다', () => {
    render(<TierBoard players={players} />)

    const midTier2 = within(screen.getByTestId('cell-2-MID'))
    const chips = midTier2.getAllByText(/다람|나비/).map((el) => el.textContent)
    expect(chips).toEqual(['나비', '다람'])
    expect(midTier2.getByText('55')).toBeInTheDocument()
    expect(within(screen.getByTestId('cell-3-TOP')).getByText('다람')).toBeInTheDocument()
    expect(screen.getByTestId('cell-1-MID').textContent).toBe('')
    expect(screen.queryByText('가나')).not.toBeInTheDocument() // 라인 티어가 없는 사람은 보드에 없다
  })

  it('변동이 있는 칩에만 ▲ 표시와 툴팁이 붙는다', () => {
    render(<TierBoard players={players} />)

    expect(screen.getAllByText('▲')).toHaveLength(1)
    expect(within(screen.getByTestId('cell-2-MID')).getByLabelText('3티어 → 2티어')).toBeInTheDocument()
  })

  it('onEdit이 없으면 칩은 버튼이 아니고, 있으면 누른 사람 id를 넘긴다', async () => {
    const user = userEvent.setup()
    const { rerender } = render(<TierBoard players={players} />)
    expect(screen.queryByRole('button')).not.toBeInTheDocument()

    const onEdit = vi.fn()
    rerender(<TierBoard players={players} onEdit={onEdit} />)
    await user.click(screen.getByRole('button', { name: /나비/ }))

    expect(onEdit).toHaveBeenCalledWith(2)
  })
})

describe('TierPeopleTable', () => {
  it('이름순으로 보여 주고, 티어 없는 라인은 —, 있는 라인은 "N티어 점수"만 보여 준다(상중하 글자 없음)', () => {
    render(<TierPeopleTable players={players} />)

    const rows = screen.getAllByRole('row').slice(1)
    expect(rows.map((row) => within(row).getAllByRole('cell')[0].textContent)).toEqual(['가나', '나비', '다람'])
    const nabi = within(rows[1])
    expect(nabi.getByText(/2티어/)).toBeInTheDocument()
    expect(nabi.getByText('55')).toBeInTheDocument()
    expect(nabi.getAllByText('—')).toHaveLength(4)
    expect(screen.queryByText('상')).not.toBeInTheDocument()
  })

  it('관리 모드의 삭제는 두 번 눌러야 onDelete가 불린다', async () => {
    const user = userEvent.setup()
    const onDelete = vi.fn()
    render(<TierPeopleTable players={players} onEdit={() => {}} onDelete={onDelete} />)

    await user.click(screen.getByRole('button', { name: '나비 삭제' }))
    expect(onDelete).not.toHaveBeenCalled()

    await user.click(screen.getByRole('button', { name: '나비 정말 삭제' }))
    expect(onDelete).toHaveBeenCalledWith(2)
  })

  it('공개 모드에는 수정·삭제 열이 없다', () => {
    render(<TierPeopleTable players={players} />)

    expect(screen.queryByRole('button')).not.toBeInTheDocument()
  })
})

describe('요주 인물 표시', () => {
  const flaggedPlayers = [tierPlayer(1, '다람', { MID: lane(2, 'LOW', 49) }, true), tierPlayer(2, '나비', { MID: lane(2, 'HIGH', 55) })]

  it('티어 보드와 사람별 표에서 flagged인 사람 이름 뒤에만 벌 아이콘이 붙는다', () => {
    const { unmount } = render(<TierBoard players={flaggedPlayers} />)
    expect(screen.getAllByRole('img', { name: '요주 인물' })).toHaveLength(1)
    unmount()

    render(<TierPeopleTable players={flaggedPlayers} />)
    const rows = screen.getAllByRole('row')
    expect(within(rows.find((row) => row.textContent?.includes('다람'))!).getByRole('img', { name: '요주 인물' })).toBeInTheDocument()
    expect(within(rows.find((row) => row.textContent?.includes('나비'))!).queryByRole('img')).not.toBeInTheDocument()
  })
})
