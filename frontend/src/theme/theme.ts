// 테마 저장·적용 함수. 노트: docs/study/03-frontend-skeleton.md
export type Theme = 'dark' | 'light'

const STORAGE_KEY = 'theme'

// 저장된 값이 없거나 localStorage 접근이 막힌 환경(시크릿 모드 등)이면 다크로 둔다.
export function readStoredTheme(): Theme {
  try {
    return localStorage.getItem(STORAGE_KEY) === 'light' ? 'light' : 'dark'
  } catch {
    return 'dark'
  }
}

export function applyTheme(theme: Theme): void {
  document.documentElement.setAttribute('data-theme', theme)
}

export function saveTheme(theme: Theme): void {
  try {
    localStorage.setItem(STORAGE_KEY, theme)
  } catch {
    // 저장 실패해도 현재 화면의 테마 전환은 유지한다.
  }
}
