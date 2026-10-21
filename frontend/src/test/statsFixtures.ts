// 통계 화면 테스트용 자료와 렌더 도우미 (가상 이름만 쓴다)
import { QueryClientProvider } from '@tanstack/react-query'
import { render } from '@testing-library/react'
import { createElement, type ReactElement } from 'react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import type { SummaryView } from '../api/publicTypes'
import type { ChampionList } from '../api/registrationTypes'
import { createQueryClient } from '../queryClient'
import { jsonResponse, type MockHandler } from './mockFetch'

export const summary = (sets: number, wins: number, kills = 0, deaths = 0, assists = 0): SummaryView => ({
  sets,
  wins,
  losses: sets - wins,
  winRate: sets > 0 ? wins / sets : 0,
  kda: { kills, deaths, assists, ratio: sets === 0 ? null : deaths === 0 ? null : (kills + assists) / deaths },
  cs: sets * 200,
  gold: sets * 10000,
})

const NAMES: Record<string, string> = { Ahri: '아리', Zed: '제드', Lux: '럭스', Jinx: '징크스' }

export const championCatalog: ChampionList = {
  version: '14.1.1',
  champions: Object.entries(NAMES).map(([id, name]) => ({ id, key: id, name, iconUrl: `https://cdn.test/${id}.png` })),
}

export const championsHandler: MockHandler = () => jsonResponse(200, championCatalog)

// path: 테스트가 보고 싶은 주소('/ranking' 등), pattern: 라우트 패턴('/players/:playerId')
export function renderPage(element: ReactElement, url: string, pattern = '*') {
  const client = createQueryClient()
  client.setDefaultOptions({ queries: { ...client.getDefaultOptions().queries, retry: false } })
  return render(
    createElement(
      QueryClientProvider,
      { client },
      createElement(
        MemoryRouter,
        { initialEntries: [url] },
        createElement(Routes, null, createElement(Route, { path: pattern, element })),
      ),
    ),
  )
}
