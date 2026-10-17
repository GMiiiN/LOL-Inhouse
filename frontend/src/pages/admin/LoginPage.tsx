// 담당자 로그인 화면(/admin/login). 성공하면 원래 가려던 곳(state.from) 또는 /admin으로 이동한다.
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState, type SyntheticEvent } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { login } from '../../api/admin'
import { ME_KEY } from '../../api/queryKeys'
import { useAdminMe } from '../../auth/useAdminMe'
import { ErrorNotice } from '../../components/ErrorNotice'

// 이동 대상은 담당자 영역 안으로만 제한한다(state가 조작돼도 다른 곳으로 보내지 않음).
function redirectTarget(state: unknown): string {
  const from = (state as { from?: { pathname?: unknown; search?: unknown } } | null)?.from
  const pathname = typeof from?.pathname === 'string' ? from.pathname : ''
  const search = typeof from?.search === 'string' ? from.search : ''
  if (pathname.startsWith('/admin') && pathname !== '/admin/login') return pathname + search
  return '/admin'
}

export function LoginPage() {
  const me = useAdminMe()
  const location = useLocation()
  const queryClient = useQueryClient()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')

  const loginMutation = useMutation({
    mutationFn: () => login(username, password),
    // 로그인 상태 캐시를 채우면 아래 Navigate가 동작한다.
    // 아직 오는 중인 초기 /me(401) 응답이 나중에 도착해 캐시를 null로 덮어쓰지 않도록 먼저 취소한다.
    onSuccess: async (data) => {
      await queryClient.cancelQueries({ queryKey: ME_KEY })
      queryClient.setQueryData(ME_KEY, data)
    },
  })

  if (me.data) return <Navigate to={redirectTarget(location.state)} replace />

  function handleSubmit(event: SyntheticEvent<HTMLFormElement>) {
    event.preventDefault()
    loginMutation.mutate()
  }

  return (
    <section className="form-page">
      <h1>담당자 로그인</h1>
      <form className="stack-form" onSubmit={handleSubmit}>
        <label>
          아이디
          <input
            type="text"
            name="username"
            autoComplete="username"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            required
          />
        </label>
        <label>
          비밀번호
          <input
            type="password"
            name="password"
            autoComplete="current-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
          />
        </label>
        <ErrorNotice error={loginMutation.error} />
        <button type="submit" className="primary-button" disabled={loginMutation.isPending}>
          로그인
        </button>
      </form>
    </section>
  )
}
