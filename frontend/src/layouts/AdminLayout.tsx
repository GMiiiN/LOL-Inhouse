// 담당자 화면 공통 틀. 공개 헤더와 메뉴만 다르다. 노트: docs/study/03-frontend-skeleton.md
import { Link, NavLink, Outlet } from 'react-router-dom'
import { ThemeToggle } from '../components/ThemeToggle'

export function AdminLayout() {
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
          <Link to="/">공개 화면</Link>
          {/* 실제 로그아웃(세션 종료)은 M3에서 연결. 지금은 로그인 화면으로 이동만 한다. */}
          <Link to="/admin/login">로그아웃</Link>
          <ThemeToggle />
        </div>
      </header>
      <main className="app-main">
        <Outlet />
      </main>
    </>
  )
}
