// 공개 화면 공통 틀(헤더 + 본문 자리 Outlet)
import { Link, NavLink, Outlet } from 'react-router-dom'
import { ThemeToggle } from '../components/ThemeToggle'

export function PublicLayout() {
  return (
    <>
      <header className="app-header">
        <Link to="/" className="app-logo">
          내전기록
        </Link>
        <nav className="app-nav" aria-label="공개 메뉴">
          <NavLink to="/" end>홈</NavLink>
          <NavLink to="/ranking">랭킹</NavLink>
          <NavLink to="/players">선수</NavLink>
          <NavLink to="/records">내전 기록</NavLink>
        </nav>
        <div className="header-actions">
          <Link to="/admin">담당자</Link>
          <ThemeToggle />
        </div>
      </header>
      <main className="app-main">
        <Outlet />
      </main>
    </>
  )
}
