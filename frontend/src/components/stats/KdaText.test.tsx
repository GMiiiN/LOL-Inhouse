import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { KdaText } from './KdaText'

const kda = (ratio: number | null) => ({ kills: 1, deaths: 0, assists: 1, ratio })

describe('KdaText', () => {
  it('숫자는 소수 2자리다', () => {
    render(<KdaText kda={kda(3.5)} sets={2} />)
    expect(screen.getByText('3.50')).toBeInTheDocument()
  })

  it('ratio가 null이고 세트가 있으면 PERFECT다', () => {
    render(<KdaText kda={kda(null)} sets={1} />)
    expect(screen.getByText('PERFECT')).toBeInTheDocument()
  })

  it('세트가 0이면 대시다', () => {
    render(<KdaText kda={kda(null)} sets={0} />)
    expect(screen.getByText('—')).toBeInTheDocument()
    expect(screen.queryByText('PERFECT')).toBeNull()
  })
})
