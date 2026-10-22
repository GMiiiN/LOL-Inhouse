// 챔피언 선택 위젯의 검색: 초성·부분 일치·영문 키
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import type { ChampionInfo } from '../../../api/registrationTypes'
import { ChampionPicker } from './ChampionPicker'

const champions: ChampionInfo[] = [
  { id: 'Ahri', key: '103', name: '아리', iconUrl: 'x' },
  { id: 'LeeSin', key: '64', name: '리 신', iconUrl: 'x' },
  { id: 'Zed', key: '238', name: '제드', iconUrl: 'x' },
]

function renderPicker(onChange = vi.fn()) {
  render(
    <ChampionPicker
      label="탑"
      value={null}
      guess={null}
      box={null}
      imageUrl={null}
      champions={champions}
      championsById={new Map(champions.map((c) => [c.id, c]))}
      restricted={new Set()}
      issues={[]}
      onChange={onChange}
    />,
  )
  return onChange
}

describe('ChampionPicker 검색', () => {
  it('초성으로 찾아 고를 수 있다', async () => {
    const onChange = renderPicker()

    await userEvent.type(screen.getByLabelText('탑 검색'), 'ㄹㅅ')
    const results = screen.getByRole('list', { name: '탑 검색 결과' })
    expect(results).toHaveTextContent('리 신')
    expect(results).not.toHaveTextContent('제드')

    await userEvent.click(screen.getByRole('button', { name: '리 신' }))
    expect(onChange).toHaveBeenCalledWith('LeeSin')
  })

  it('영문 키는 대소문자를 무시한다', async () => {
    renderPicker()
    await userEvent.type(screen.getByLabelText('탑 검색'), 'ZED')
    expect(screen.getByRole('list', { name: '탑 검색 결과' })).toHaveTextContent('제드')
  })
})
