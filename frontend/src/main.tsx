// 앱 진입점: 테마 적용 → Provider 구성 → 라우터 렌더. 노트: docs/study/03-frontend-skeleton.md
import { QueryClientProvider } from '@tanstack/react-query'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { createBrowserRouter, RouterProvider } from 'react-router-dom'
import { queryClient } from './queryClient'
import { routes } from './routes'
import './styles/theme.css'
import './styles/global.css'
import { applyTheme, readStoredTheme } from './theme/theme'

// 첫 렌더 전에 적용해야 라이트 테마 사용자가 다크 화면을 잠깐 보지 않는다.
applyTheme(readStoredTheme())

const router = createBrowserRouter(routes)

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>
  </StrictMode>,
)
