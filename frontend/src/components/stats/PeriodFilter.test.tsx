import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, useLocation } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import { PeriodFilter } from './PeriodFilter'

function Probe() {
  return <output data-testid="search">{useLocation().search}</output>
}

function setup(url: string) {
  render(
    <MemoryRouter initialEntries={[url]}>
      <PeriodFilter />
      <Probe />
    </MemoryRouter>,
  )
}

describe('PeriodFilter', () => {
  it('URL의 기간을 입력칸에 보여 준다', () => {
    setup('/records?from=2026-10-01')
    expect(screen.getByLabelText('시작일')).toHaveValue('2026-10-01')
    expect(screen.getByLabelText('종료일')).toHaveValue('')
    expect(screen.getByRole('button', { name: '기간 지우기' })).toBeEnabled()
  })

  it('입력하면 URL에 반영되고 다른 파라미터는 남는다', async () => {
    setup('/records?player=3')
    expect(screen.getByRole('button', { name: '기간 지우기' })).toBeDisabled()
    await userEvent.setup().type(screen.getByLabelText('종료일'), '2026-10-15')
    const params = new URLSearchParams(screen.getByTestId('search').textContent ?? '')
    expect(params.get('to')).toBe('2026-10-15')
    expect(params.get('player')).toBe('3')
  })

  it('지우기는 from·to만 없앤다', async () => {
    setup('/records?player=3&from=2026-10-01&to=2026-10-15')
    await userEvent.setup().click(screen.getByRole('button', { name: '기간 지우기' }))
    expect(screen.getByTestId('search')).toHaveTextContent(/^\?player=3$/)
  })
})
