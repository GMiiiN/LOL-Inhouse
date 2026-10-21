import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { WinRate } from './WinRate'

describe('WinRate', () => {
  it('50% 이상은 wgood, 미만은 wbad다', () => {
    const { rerender } = render(<WinRate winRate={0.5} sets={4} />)
    expect(screen.getByText('50%')).toHaveClass('wgood')
    rerender(<WinRate winRate={0.25} sets={4} />)
    expect(screen.getByText('25%')).toHaveClass('wbad')
  })

  it('반올림해서 보여 준다', () => {
    render(<WinRate winRate={2 / 3} sets={3} />)
    expect(screen.getByText('67%')).toHaveClass('wgood')
  })

  it('세트가 0이면 대시이고 색 클래스가 없다', () => {
    render(<WinRate winRate={0} sets={0} />)
    const dash = screen.getByText('—')
    expect(dash).not.toHaveClass('wgood')
    expect(dash).not.toHaveClass('wbad')
  })
})
