// 앱 진입점: 테마 적용 → Provider 구성 → 라우터 렌더
import { QueryClientProvider } from '@tanstack/react-query'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { createBrowserRouter, RouterProvider } from 'react-router-dom'
import { initCsrf } from './api/client'
import { queryClient } from './queryClient'
import { routes } from './routes'
import './styles/theme.css'
import './styles/global.css'
import './styles/admin.css'
import './styles/register.css'
import { applyTheme, readStoredTheme } from './theme/theme'

// 첫 렌더 전에 적용해야 라이트 테마 사용자가 다크 화면을 잠깐 보지 않는다.
applyTheme(readStoredTheme())

// 변경 요청에 쓸 CSRF 쿠키를 앱 시작 때 미리 받아 둔다. 실패해도 앱은 뜬다.
void initCsrf()

const router = createBrowserRouter(routes)

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>
  </StrictMode>,
)
