// CoinToss 테스트: 난수·지연 주입, 첫 묶음 기억, 칸 맞바꿈 요청, 움직임 줄이기
import { act, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { CoinToss } from './CoinToss'

afterEach(() => vi.useRealTimers())

describe('CoinToss', () => {
  it('블루가 나오면 칸은 그대로 두고 A팀 문구를 낸다. 지연이 지난 뒤에만 결과가 나온다', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    const onSettle = vi.fn()
    render(<CoinToss blueNames={['가', '나']} redNames={['다']} onSettle={onSettle} random={() => 0.9} delayMs={1500} />)

    await userEvent.click(screen.getByRole('button', { name: '코인토스' }))
    expect(screen.getByText(/동전을 던지는 중/)).toBeInTheDocument()
    expect(onSettle).not.toHaveBeenCalled()

    act(() => {
      vi.advanceTimersByTime(1500)
    })
    expect(onSettle).toHaveBeenCalledWith(false)
    expect(screen.getByRole('status')).toHaveTextContent('블루가 나왔어요. 첫 묶음(가, 나)이 블루, 즉 A팀이에요.')
  })

  it('레드가 나오면 첫 묶음을 레드로 보내려고 칸 맞바꿈을 요청한다. 다시 던져도 첫 묶음은 그대로다', async () => {
    const onSettle = vi.fn()
    const rolls = [0.1, 0.9]
    const random = () => rolls.shift()!
    const { rerender } = render(<CoinToss blueNames={['가']} redNames={['나']} onSettle={onSettle} random={random} delayMs={0} />)

    await userEvent.click(screen.getByRole('button', { name: '코인토스' }))
    expect(onSettle).toHaveBeenLastCalledWith(true)
    expect(screen.getByRole('status')).toHaveTextContent('레드가 나왔어요. 첫 묶음(가)이 레드, 즉 B팀이에요.')

    // 부모가 칸을 맞바꾼 뒤: 블루=나, 레드=가. 이번엔 블루가 나오므로 첫 묶음(가)을 블루로 돌려야 한다
    rerender(<CoinToss blueNames={['나']} redNames={['가']} onSettle={onSettle} random={random} delayMs={0} />)
    await userEvent.click(screen.getByRole('button', { name: '다시 던지기' }))
    expect(onSettle).toHaveBeenLastCalledWith(true)
    expect(screen.getByRole('status')).toHaveTextContent('첫 묶음(가)이 블루, 즉 A팀')
  })

  it('움직임 줄이기 설정이면 지연 없이 바로 결과를 낸다', async () => {
    vi.stubGlobal('matchMedia', () => ({ matches: true }))
    const onSettle = vi.fn()
    render(<CoinToss blueNames={['가']} redNames={['나']} onSettle={onSettle} random={() => 0.9} delayMs={5000} />)

    await userEvent.click(screen.getByRole('button', { name: '코인토스' }))

    expect(onSettle).toHaveBeenCalledWith(false)
    expect(screen.getByRole('status')).toBeInTheDocument()
  })
})
