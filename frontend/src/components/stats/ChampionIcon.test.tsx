import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import type { ReactElement } from 'react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { jsonResponse, mockFetch } from '../../test/mockFetch'
import { ChampionIcon } from './ChampionIcon'

const catalog = {
  version: '15.1.1',
  champions: [{ id: 'Ahri', key: '103', name: '아리', iconUrl: 'https://cdn.test/Ahri.png' }],
}

function renderIcon(ui: ReactElement) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={client}>{ui}</QueryClientProvider>)
}

afterEach(() => vi.unstubAllGlobals())

describe('ChampionIcon', () => {
  it('카탈로그에 있으면 한글 이름과 아이콘을 보여 주고 크기 클래스를 붙인다', async () => {
    mockFetch({ 'GET /api/champions': () => jsonResponse(200, catalog) })
    const { container } = renderIcon(<ChampionIcon championKey="Ahri" size="sm" showName />)
    expect(await screen.findByRole('img', { name: '아리' })).toHaveClass('ci', 'sm')
    expect(container.querySelector('img')).toHaveAttribute('src', 'https://cdn.test/Ahri.png')
    expect(container.querySelector('.ci-with-name')).toHaveTextContent('아리')
  })

  it('카탈로그에 없으면 키 글자로 대신한다', async () => {
    mockFetch({ 'GET /api/champions': () => jsonResponse(200, catalog) })
    const { container } = renderIcon(<ChampionIcon championKey="Zed" />)
    const icon = await screen.findByRole('img', { name: 'Zed' })
    expect(icon).toHaveTextContent('Ze')
    expect(container.querySelector('img')).toBeNull()
  })

  it('카탈로그 요청이 실패해도 키 글자로 그린다', async () => {
    mockFetch({ 'GET /api/champions': () => jsonResponse(500, { status: 500, code: 'X' }) })
    renderIcon(<ChampionIcon championKey="Zed" />)
    expect(await screen.findByRole('img', { name: 'Zed' })).toHaveTextContent('Ze')
  })
})
