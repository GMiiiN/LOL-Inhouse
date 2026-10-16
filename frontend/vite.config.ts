// 개발 서버(프록시)와 Vitest 설정. 노트: docs/study/03-frontend-skeleton.md
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vitest/config'

export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      // 브라우저는 같은 출처(5173)로 /api를 호출하고, Vite가 Spring(8080)으로 전달한다.
      '/api': 'http://localhost:8080',
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
  },
})
