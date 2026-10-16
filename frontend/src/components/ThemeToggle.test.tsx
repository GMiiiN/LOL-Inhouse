// 테마 토글 테스트: data-theme 전환과 localStorage 저장. 노트: docs/study/03-frontend-skeleton.md
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { ThemeToggle } from './ThemeToggle'

describe('ThemeToggle', () => {
  it('누르면 data-theme이 light로 바뀌고 localStorage에 저장되며, 다시 누르면 dark로 돌아온다', async () => {
    const user = userEvent.setup()
    render(<ThemeToggle />)

    await user.click(screen.getByRole('button', { name: /테마: 다크/ }))
    expect(document.documentElement).toHaveAttribute('data-theme', 'light')
    expect(localStorage.getItem('theme')).toBe('light')

    await user.click(screen.getByRole('button', { name: /테마: 라이트/ }))
    expect(document.documentElement).toHaveAttribute('data-theme', 'dark')
    expect(localStorage.getItem('theme')).toBe('dark')
  })

  it('저장된 light 값이 있으면 라이트 상태로 시작한다', () => {
    localStorage.setItem('theme', 'light')
    render(<ThemeToggle />)

    expect(screen.getByRole('button', { name: /테마: 라이트/ })).toBeInTheDocument()
  })
})
