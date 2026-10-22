// 기록 필터의 챔피언 선택: 검색 입력으로 초성·부분 일치 필터, 고른 챔피언은 항상 남긴다
import { QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { createQueryClient } from '../../queryClient'
import { jsonResponse, mockFetch } from '../../test/mockFetch'
import { RecordFilters } from './RecordFilters'

const champions = {
  version: '1',
  champions: [
    { id: 'Ahri', key: '103', name: '아리', iconUrl: 'x' },
    { id: 'LeeSin', key: '64', name: '리 신', iconUrl: 'x' },
    { id: 'Zed', key: '238', name: '제드', iconUrl: 'x' },
  ],
}

afterEach(() => vi.unstubAllGlobals())

function renderFilters(champion?: string) {
  mockFetch({
    'GET /api/players': () => jsonResponse(200, { totalSets: 0, players: [] }),
    'GET /api/champions': () => jsonResponse(200, champions),
  })
  const client = createQueryClient()
  render(
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <RecordFilters player={undefined} champion={champion} championScope={undefined} hasFilter={false} onClear={() => {}} />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('RecordFilters 챔피언 검색', () => {
  it('초성으로 챔피언 목록을 좁힌다', async () => {
    renderFilters()
    await screen.findByRole('option', { name: '제드' })

    await userEvent.type(screen.getByLabelText('챔피언 검색'), 'ㄹㅅ')

    expect(screen.getByRole('option', { name: '리 신' })).toBeInTheDocument()
    expect(screen.queryByRole('option', { name: '제드' })).not.toBeInTheDocument()
  })

  it('영문 키로도 찾고, 이미 고른 챔피언은 검색과 상관없이 남는다', async () => {
    renderFilters('Zed')
    await screen.findByRole('option', { name: '제드' })

    await userEvent.type(screen.getByLabelText('챔피언 검색'), 'lee')

    expect(screen.getByRole('option', { name: '리 신' })).toBeInTheDocument()
    expect(screen.getByRole('option', { name: '제드' })).toBeInTheDocument()
    expect(screen.queryByRole('option', { name: '아리' })).not.toBeInTheDocument()
  })
})
