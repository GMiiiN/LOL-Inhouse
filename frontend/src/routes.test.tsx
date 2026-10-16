// 라우터 스모크 테스트: URL에 맞는 레이아웃과 자리표시자 페이지가 그려지는지. 노트: docs/study/03-frontend-skeleton.md
import { render, screen } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import { routes } from './routes'

function renderAt(path: string) {
  render(<RouterProvider router={createMemoryRouter(routes, { initialEntries: [path] })} />)
}

describe('routes', () => {
  it('/ranking은 공개 헤더와 랭킹 자리표시자를 그린다', () => {
    renderAt('/ranking')

    expect(screen.getByRole('heading', { name: '랭킹' })).toBeInTheDocument()
    expect(screen.getByRole('navigation', { name: '공개 메뉴' })).toBeInTheDocument()
  })

  it('/admin/players는 담당자 헤더를 그린다', () => {
    renderAt('/admin/players')

    expect(screen.getByRole('heading', { name: '선수 관리' })).toBeInTheDocument()
    expect(screen.getByRole('navigation', { name: '담당자 메뉴' })).toBeInTheDocument()
  })
})
