// 앱의 URL 지도. main.tsx는 createBrowserRouter, 테스트는 createMemoryRouter에 같은 배열을 쓴다.
import type { RouteObject } from 'react-router-dom'
import { AdminLayout } from './layouts/AdminLayout'
import { PublicLayout } from './layouts/PublicLayout'
import { LoginPage } from './pages/admin/LoginPage'
import { PlayersPage } from './pages/admin/PlayersPage'
import { SessionDetailPage } from './pages/admin/SessionDetailPage'
import { SessionsPage } from './pages/admin/SessionsPage'
import { SetRegisterPage } from './pages/admin/register/SetRegisterPage'
import { PlaceholderPage } from './pages/PlaceholderPage'

export const routes: RouteObject[] = [
  {
    element: <PublicLayout />,
    children: [
      { path: '/', element: <PlaceholderPage title="홈" milestone="M5" /> },
      { path: '/ranking', element: <PlaceholderPage title="랭킹" milestone="M5" /> },
      { path: '/players', element: <PlaceholderPage title="선수" milestone="M5" /> },
      { path: '/players/:playerId', element: <PlaceholderPage title="선수 프로필" milestone="M5" /> },
      { path: '/records', element: <PlaceholderPage title="내전 기록" milestone="M5" /> },
      { path: '/duels/:duelId', element: <PlaceholderPage title="대결 상세" milestone="M5" /> },
      { path: '/sets/:setId', element: <PlaceholderPage title="세트 상세" milestone="M5" /> },
      { path: '/admin/login', element: <LoginPage /> },
    ],
  },
  {
    element: <AdminLayout />,
    children: [
      { path: '/admin', element: <SessionsPage /> },
      { path: '/admin/sessions/:sessionId', element: <SessionDetailPage /> },
      { path: '/admin/duels/:duelId/sets/new', element: <SetRegisterPage /> },
      { path: '/admin/players', element: <PlayersPage /> },
      { path: '/admin/history', element: <PlaceholderPage title="기록 관리" milestone="M6" /> },
    ],
  },
]
