// 앱의 URL 지도. main.tsx는 createBrowserRouter, 테스트는 createMemoryRouter에 같은 배열을 쓴다.
import type { RouteObject } from 'react-router-dom'
import { AdminLayout } from './layouts/AdminLayout'
import { PublicLayout } from './layouts/PublicLayout'
import { LoginPage } from './pages/admin/LoginPage'
import { ChampionStatsPage } from './pages/champions/ChampionStatsPage'
import { DuelDetailPage } from './pages/duel/DuelDetailPage'
import { HomePage } from './pages/home/HomePage'
import { RecordManagePage } from './pages/admin/history/RecordManagePage'
import { PlayerTierAdminPage } from './pages/admin/players/PlayerTierAdminPage'
import { SessionDetailPage } from './pages/admin/SessionDetailPage'
import { SessionsPage } from './pages/admin/SessionsPage'
import { SetEditPage } from './pages/admin/set-form/SetEditPage'
import { SetRegisterPage } from './pages/admin/register/SetRegisterPage'
import { ScoreSettingsPage } from './pages/admin/score/ScoreSettingsPage'
import { PlayerProfilePage } from './pages/players/PlayerProfilePage'
import { PlayersPage } from './pages/players/PlayersPage'
import { RankingPage } from './pages/ranking/RankingPage'
import { RecordsPage } from './pages/records/RecordsPage'
import { SetDetailPage } from './pages/set/SetDetailPage'
import { TeamMakerPage } from './pages/team/TeamMakerPage'
import { TierListPage } from './pages/tier/TierListPage'

export const routes: RouteObject[] = [
  {
    element: <PublicLayout />,
    children: [
      { path: '/', element: <HomePage /> },
      { path: '/ranking', element: <RankingPage /> },
      { path: '/players', element: <PlayersPage /> },
      { path: '/players/:playerId', element: <PlayerProfilePage /> },
      { path: '/champions', element: <ChampionStatsPage /> },
      { path: '/records', element: <RecordsPage /> },
      { path: '/duels/:duelId', element: <DuelDetailPage /> },
      { path: '/sets/:setId', element: <SetDetailPage /> },
      { path: '/tiers', element: <TierListPage /> },
      { path: '/team-maker', element: <TeamMakerPage /> },
      { path: '/admin/login', element: <LoginPage /> },
    ],
  },
  {
    element: <AdminLayout />,
    children: [
      { path: '/admin', element: <SessionsPage /> },
      { path: '/admin/sessions/:sessionId', element: <SessionDetailPage /> },
      { path: '/admin/duels/:duelId/sets/new', element: <SetRegisterPage /> },
      { path: '/admin/sets/:setId/edit', element: <SetEditPage /> },
      { path: '/admin/players', element: <PlayerTierAdminPage /> },
      { path: '/admin/score', element: <ScoreSettingsPage /> },
      { path: '/admin/history', element: <RecordManagePage /> },
    ],
  },
]
