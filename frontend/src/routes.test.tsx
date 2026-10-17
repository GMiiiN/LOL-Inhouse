// 라우터 스모크 테스트: URL에 맞는 레이아웃과 페이지가 그려지는지
import { screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { jsonResponse, mockFetch } from './test/mockFetch'
import { renderApp } from './test/renderApp'

describe('routes', () => {
  it('/ranking은 공개 헤더와 랭킹 자리표시자를 그린다', () => {
    renderApp('/ranking')

    expect(screen.getByRole('heading', { name: '랭킹' })).toBeInTheDocument()
    expect(screen.getByRole('navigation', { name: '공개 메뉴' })).toBeInTheDocument()
  })

  it('로그인한 상태에서 /admin/players는 담당자 헤더와 선수 관리를 그린다', async () => {
    mockFetch({
      'GET /api/admin/me': () => jsonResponse(200, { username: 'admin' }),
      'GET /api/admin/players': () => jsonResponse(200, []),
    })
    renderApp('/admin/players')

    expect(await screen.findByRole('heading', { name: '선수 관리' })).toBeInTheDocument()
    expect(screen.getByRole('navigation', { name: '담당자 메뉴' })).toBeInTheDocument()
  })
})
