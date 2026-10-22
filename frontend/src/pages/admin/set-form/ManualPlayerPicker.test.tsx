// 초안 없는 선수 칸 테스트: 초성 검색 선택, 새 미등록 선수, 별칭 추가
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { describe, expect, it } from 'vitest'
import type { RowInput } from '../../../api/registrationTypes'
import type { Player } from '../../../api/types'
import { ManualPlayerPicker } from './ManualPlayerPicker'

const players: Player[] = [
  { id: 1, displayName: '홍길동', realName: '홍길동', registered: true, mainPosition: null, aliases: [{ id: 1, nickname: '길동이' }] },
  { id: 2, displayName: '허수아비', realName: null, registered: false, mainPosition: null, aliases: [{ id: 2, nickname: 'Scarecrow' }] },
]

const emptyRow: RowInput = {
  screenRow: 1,
  position: 'TOP',
  nickname: '',
  resolution: 'UNRESOLVED',
  playerId: null,
  addAlias: false,
  championKey: null,
  kills: 0,
  deaths: 0,
  assists: 0,
  cs: 0,
  gold: 0,
}

// 상태를 들고 있는 시험용 껍데기. 마지막 행 값을 밖으로 노출한다.
function Harness({ initial = emptyRow, onRow }: { initial?: RowInput; onRow: (row: RowInput) => void }) {
  const [row, setRow] = useState(initial)
  return (
    <ManualPlayerPicker
      rowLabel="위쪽 팀 결과창 1행"
      row={row}
      players={players}
      conflict={null}
      onChange={(next) => {
        setRow(next)
        onRow(next)
      }}
    />
  )
}

describe('ManualPlayerPicker', () => {
  it('초성으로 등록 선수를 찾아 고르면 EXISTING으로 연결된다', async () => {
    let latest: RowInput = emptyRow
    render(<Harness onRow={(r) => (latest = r)} />)

    await userEvent.type(screen.getByLabelText('위쪽 팀 결과창 1행 선수 검색'), 'ㅎㄱㄷ')
    await userEvent.click(screen.getByRole('button', { name: /홍길동/ }))

    expect(latest).toMatchObject({ resolution: 'EXISTING', playerId: 1, nickname: '길동이', addAlias: false })
    expect(screen.getByLabelText('위쪽 팀 결과창 1행 선택된 선수')).toHaveTextContent('홍길동')
    expect(screen.getByLabelText('위쪽 팀 결과창 1행 닉네임')).toHaveValue('길동이')
  })

  it('미등록 선수와 별칭 닉네임으로도 검색된다', async () => {
    render(<Harness onRow={() => undefined} />)
    await userEvent.type(screen.getByLabelText('위쪽 팀 결과창 1행 선수 검색'), 'scare')
    expect(await screen.findByRole('button', { name: /허수아비/ })).toHaveTextContent('미등록')
  })

  it('새 미등록 선수를 고르면 닉네임을 입력할 수 있다', async () => {
    let latest: RowInput = emptyRow
    render(<Harness onRow={(r) => (latest = r)} />)

    await userEvent.click(screen.getByRole('button', { name: /새 미등록 선수/ }))
    await userEvent.type(screen.getByLabelText('위쪽 팀 결과창 1행 닉네임'), '뉴페이스')

    expect(latest).toMatchObject({ resolution: 'NEW', playerId: null, nickname: '뉴페이스', addAlias: false })
    expect(screen.queryByRole('checkbox')).toBeNull()
  })

  it('기존 선수가 모르는 닉네임을 적으면 별칭 추가 체크박스가 나온다', async () => {
    let latest: RowInput = emptyRow
    render(<Harness onRow={(r) => (latest = r)} />)
    await userEvent.type(screen.getByLabelText('위쪽 팀 결과창 1행 선수 검색'), '홍길동')
    await userEvent.click(screen.getByRole('button', { name: /홍길동/ }))
    expect(screen.queryByRole('checkbox')).toBeNull()

    const nickname = screen.getByLabelText('위쪽 팀 결과창 1행 닉네임')
    await userEvent.clear(nickname)
    await userEvent.type(nickname, '새별명')
    await userEvent.click(screen.getByRole('checkbox', { name: '이 닉네임을 별칭으로 추가' }))

    expect(latest).toMatchObject({ resolution: 'EXISTING', playerId: 1, nickname: '새별명', addAlias: true })
  })
})
