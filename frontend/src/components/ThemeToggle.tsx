// 다크/라이트 전환 버튼. 노트: docs/study/03-frontend-skeleton.md
import { useTheme } from '../theme/useTheme'

export function ThemeToggle() {
  const { theme, toggleTheme } = useTheme()

  return (
    <button type="button" className="header-button" onClick={toggleTheme}>
      테마: {theme === 'dark' ? '다크' : '라이트'}
    </button>
  )
}
