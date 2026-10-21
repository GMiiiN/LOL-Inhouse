// ▲▼ 표시 테스트: 방향 글자와 툴팁 문구 두 종류
import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { TrendMark } from './TrendMark'

describe('TrendMark', () => {
  it('trend가 없으면 아무것도 그리지 않는다', () => {
    const { container } = render(<TrendMark trend={null} currentTier={2} />)
    expect(container).toBeEmptyDOMElement()
  })

  it('티어가 바뀐 상승은 "이전 → 현재" 툴팁과 ▲를 보여 준다', () => {
    render(<TrendMark trend={{ direction: 'UP', fromTier: 3, fromSub: 'MID' }} currentTier={2} />)

    const mark = screen.getByLabelText('3티어 → 2티어')
    expect(mark).toHaveTextContent('▲')
    expect(mark).toHaveAttribute('title', '3티어 → 2티어')
    expect(mark).toHaveClass('up')
  })

  it('하락은 ▼와 down 클래스를 쓴다', () => {
    render(<TrendMark trend={{ direction: 'DOWN', fromTier: 1, fromSub: 'LOW' }} currentTier={2} />)

    expect(screen.getByLabelText('1티어 → 2티어')).toHaveTextContent('▼')
    expect(screen.getByLabelText('1티어 → 2티어')).toHaveClass('down')
  })

  it('같은 티어 안의 변화는 "같은 티어 안에서 상승/하락"이라고 말한다', () => {
    const { rerender } = render(<TrendMark trend={{ direction: 'UP', fromTier: 2, fromSub: 'LOW' }} currentTier={2} />)
    expect(screen.getByLabelText('같은 티어 안에서 상승')).toBeInTheDocument()

    rerender(<TrendMark trend={{ direction: 'DOWN', fromTier: 2, fromSub: 'HIGH' }} currentTier={2} />)
    expect(screen.getByLabelText('같은 티어 안에서 하락')).toBeInTheDocument()
  })
})
