// 현재 테마를 React 상태로 들고, 바뀔 때 <html data-theme>와 localStorage를 갱신한다. 노트: docs/study/03-frontend-skeleton.md
import { useState } from 'react'
import { applyTheme, readStoredTheme, saveTheme, type Theme } from './theme'

export function useTheme() {
  const [theme, setTheme] = useState<Theme>(readStoredTheme)

  function toggleTheme() {
    const next: Theme = theme === 'dark' ? 'light' : 'dark'
    applyTheme(next)
    saveTheme(next)
    setTheme(next)
  }

  return { theme, toggleTheme }
}
