// Vitest 공통 준비: jest-dom 매처 등록, 테스트 사이 DOM·localStorage·쿠키·가짜 fetch 정리.
import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterEach, vi } from 'vitest'

afterEach(() => {
  cleanup()
  localStorage.clear()
  document.documentElement.removeAttribute('data-theme')
  vi.unstubAllGlobals() // mockFetch가 바꿔 둔 전역 fetch를 원래대로
  for (const cookie of document.cookie.split('; ')) {
    const name = cookie.split('=')[0]
    if (name) document.cookie = `${name}=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/`
  }
})
