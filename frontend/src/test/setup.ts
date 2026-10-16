// Vitest 공통 준비: jest-dom 매처 등록, 테스트 사이 DOM·localStorage 정리. 노트: docs/study/03-frontend-skeleton.md
import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterEach } from 'vitest'

afterEach(() => {
  cleanup()
  localStorage.clear()
  document.documentElement.removeAttribute('data-theme')
})
