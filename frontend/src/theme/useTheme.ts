// 현재 테마를 React 상태로 들고, 바뀔 때 <html data-theme>와 localStorage를 갱신한다
import { useState } from 'react'
import { applyTheme, readAppliedTheme, saveTheme, type Theme } from './theme'

export function useTheme() {
  // 저장값이 아니라 지금 화면(<html>)에 적용된 테마로 시작한다. localStorage가 막혀 저장이 실패해도
  // 레이아웃이 바뀌어 훅이 다시 만들어질 때 버튼 표시와 실제 색이 어긋나지 않게 하려는 것.
  const [theme, setTheme] = useState<Theme>(readAppliedTheme)

  function toggleTheme() {
    const next: Theme = theme === 'dark' ? 'light' : 'dark'
    applyTheme(next)
    saveTheme(next)
    setTheme(next)
  }

  return { theme, toggleTheme }
}
