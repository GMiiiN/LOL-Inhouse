// 담당자 화면 공통 틀. 로그인 상태를 확인해 미로그인이면 로그인 화면으로 보낸다.
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, Navigate, NavLink, Outlet, useLocation } from 'react-router-dom'
import { logout } from '../api/admin'
import { initCsrf } from '../api/client'
import { ME_KEY, PLAYERS_KEY, SESSIONS_KEY } from '../api/queryKeys'
import { useAdminMe } from '../auth/useAdminMe'
import { ErrorNotice } from '../components/ErrorNotice'
import { ThemeToggle } from '../components/ThemeToggle'

export function AdminLayout() {
  const me = useAdminMe()
  const location = useLocation()
  const queryClient = useQueryClient()
  // 사용자가 직접 로그아웃한 경우는 로그인 화면이 아니라 공개 홈으로 보낸다.
  const [loggedOut, setLoggedOut] = useState(false)

  const logoutMutation = useMutation({
    mutationFn: logout,
    onSuccess: () => {
      setLoggedOut(true)
      queryClient.setQueryData(ME_KEY, null)
      queryClient.removeQueries({ queryKey: PLAYERS_KEY })
      queryClient.removeQueries({ queryKey: SESSIONS_KEY })
      // 세션이 끝나면 토큰도 바뀐다. 새로 받되 실패해도 로그아웃은 성공이며, 다음 변경 요청의 CSRF_INVALID 재시도가 보완한다.
      void initCsrf()
    },
  })

  if (me.isPending) {
    return (
      <main className="app-main">
        <p className="muted-text">로그인 상태를 확인하는 중입니다...</p>
      </main>
    )
  }

  if (me.isError) {
    return (
      <main className="app-main">
        <ErrorNotice error={me.error} />
        <button type="button" className="header-button" onClick={() => me.refetch()}>
          다시 시도
        </button>
      </main>
    )
  }

  if (!me.data) {
    if (loggedOut) return <Navigate to="/" replace />
    // 원래 가려던 곳을 state로 넘겨, 로그인 성공 뒤 되돌아오게 한다.
    return (
      <Navigate
        to="/admin/login"
        replace
        state={{ from: { pathname: location.pathname, search: location.search } }}
      />
    )
  }

  return (
    <>
      <header className="app-header app-header-admin">
        <Link to="/admin" className="app-logo">
          내전기록 담당자
        </Link>
        <nav className="app-nav" aria-label="담당자 메뉴">
          <NavLink to="/admin" end>내전·대결</NavLink>
          <NavLink to="/admin/players">선수 관리</NavLink>
          <NavLink to="/admin/history">기록 관리</NavLink>
        </nav>
        <div className="header-actions">
          <span>{me.data.username}</span>
          <Link to="/">공개 화면</Link>
          <button
            type="button"
            className="header-button"
            disabled={logoutMutation.isPending}
            onClick={() => logoutMutation.mutate()}
          >
            로그아웃
          </button>
          <ThemeToggle />
        </div>
      </header>
      <main className="app-main">
        <ErrorNotice error={logoutMutation.error} />
        <Outlet />
      </main>
    </>
  )
}
