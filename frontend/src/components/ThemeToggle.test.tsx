// 테마 토글 테스트: data-theme 전환과 localStorage 저장
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { applyTheme, readStoredTheme } from '../theme/theme'
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
    applyTheme(readStoredTheme()) // main.tsx가 시작할 때 하는 일
    render(<ThemeToggle />)

    expect(screen.getByRole('button', { name: /테마: 라이트/ })).toBeInTheDocument()
  })

  it('localStorage가 막혀 있어도 다시 그려질 때 화면 테마와 버튼 표시가 일치한다', async () => {
    const blocked = vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('blocked')
    })
    const user = userEvent.setup()
    const { unmount } = render(<ThemeToggle />)
    await user.click(screen.getByRole('button', { name: /테마: 다크/ }))
    unmount() // 공개 → 담당자 레이아웃 이동처럼 토글이 새로 만들어지는 상황

    render(<ThemeToggle />)
    expect(document.documentElement).toHaveAttribute('data-theme', 'light')
    expect(screen.getByRole('button', { name: /테마: 라이트/ })).toBeInTheDocument()
    blocked.mockRestore()
  })
})
