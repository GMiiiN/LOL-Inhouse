// TeamTable의 "초안 없음" 분기(세트 수정·직접 입력): 선수 칸이 ManualPlayerPicker로 바뀌고 밴은 초안 없이도 그려진다.
import { render, screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { buildDraft, champions } from '../../../test/registrationFixtures'
import { TeamTable } from './TeamTable'

const championsById = new Map(champions.champions.map((c) => [c.id, c]))

function renderTable(draftTeam: ReturnType<typeof buildDraft>['top'] | null) {
  const draft = buildDraft()
  render(
    <TeamTable
      teamKey="top"
      teamLabel="위쪽 팀"
      heading="위쪽 팀"
      team={draft.defaults.top}
      draftTeam={draftTeam}
      banLabels={['B1', 'B2', 'B3', 'B4', 'B5']}
      imageUrl={null}
      champions={champions.champions}
      championsById={championsById}
      restricted={new Set()}
      players={[]}
      issues={{}}
      aliasConflicts={new Map()}
      onUpdate={() => undefined}
    />,
  )
}

describe('TeamTable', () => {
  it('초안이 있으면 읽은 닉네임이 보이는 PlayerPicker를 쓴다', () => {
    renderTable(buildDraft().top)
    const row = screen.getByRole('group', { name: '위쪽 팀 결과창 1행' })
    expect(within(row).getByText('읽은 값')).toBeInTheDocument()
    expect(within(row).queryByLabelText('위쪽 팀 결과창 1행 선수 검색')).toBeNull()
  })

  it('초안이 없으면 ManualPlayerPicker와 밴 칸 5개를 그린다', () => {
    renderTable(null)
    const row = screen.getByRole('group', { name: '위쪽 팀 결과창 1행' })
    expect(within(row).queryByText('읽은 값')).toBeNull()
    expect(within(row).getByLabelText('위쪽 팀 결과창 1행 선수 검색')).toBeInTheDocument()
    expect(screen.queryByText('초안에 이 행이 없습니다.')).toBeNull()
    expect(within(screen.getByRole('group', { name: '위쪽 팀 밴' })).getAllByRole('searchbox')).toHaveLength(5)
  })
})
