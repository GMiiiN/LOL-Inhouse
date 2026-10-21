import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { TeamLabel } from './TeamLabel'

describe('TeamLabel', () => {
  it('TEAM1은 A팀, TEAM2는 B팀이고 진영을 붙인다', () => {
    const { rerender } = render(<TeamLabel team="TEAM1" side="BLUE" />)
    expect(screen.getByText('A팀 · 블루')).toHaveClass('team-blue')
    rerender(<TeamLabel team="TEAM2" side="RED" />)
    expect(screen.getByText('B팀 · 레드')).toHaveClass('team-red')
    rerender(<TeamLabel team="TEAM1" side="RED" />)
    expect(screen.getByText('A팀 · 레드')).toBeInTheDocument()
  })
})
