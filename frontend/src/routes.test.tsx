// 라우터 스모크 테스트: URL에 맞는 레이아웃과 페이지가 그려지는지
import { screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { jsonResponse, mockFetch } from './test/mockFetch'
import { DEFAULT_SCORE_TABLE } from './test/tierListFixtures'
import { renderApp } from './test/renderApp'

describe('routes', () => {
  it('/ranking은 공개 헤더와 랭킹 자리표시자를 그린다', () => {
    renderApp('/ranking')

    expect(screen.getByRole('heading', { name: '랭킹' })).toBeInTheDocument()
    expect(screen.getByRole('navigation', { name: '공개 메뉴' })).toBeInTheDocument()
  })

  it('/tiers는 로그인 없이 공개 헤더 안에서 열리고 편집 버튼이 없다', async () => {
    mockFetch({ 'GET /api/tier-list': () => jsonResponse(200, { scoreTable: null, players: [] }) })
    renderApp('/tiers')
    expect(screen.getByRole('heading', { level: 1, name: '티어리스트' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '팀 짜기' })).toBeInTheDocument()
    expect(await screen.findByText(/아직 등록된 사람이 없어요/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '사람 추가' })).not.toBeInTheDocument()
  })

  it('/team-maker는 공개 화면이다', () => {
    renderApp('/team-maker')
    expect(screen.getByRole('heading', { name: '팀 짜기' })).toBeInTheDocument()
  })

  it('로그인한 상태에서 /admin/score는 점수 설정 자리를 그리고 메뉴에 점수 설정이 있다', async () => {
    mockFetch({
      'GET /api/admin/me': () => jsonResponse(200, { username: 'admin' }),
      'GET /api/score-table': () => jsonResponse(200, DEFAULT_SCORE_TABLE),
    })
    renderApp('/admin/score')

    expect(await screen.findByRole('heading', { name: '점수 설정' })).toBeInTheDocument()
    expect(screen.getByRole('navigation', { name: '담당자 메뉴' })).toHaveTextContent('점수 설정')
    expect(await screen.findByLabelText('1티어')).toHaveValue(60)
  })

  it('로그인한 상태에서 /admin/players는 담당자 헤더와 새 선수 관리(티어리스트 + 편집)를 그린다', async () => {
    mockFetch({
      'GET /api/admin/me': () => jsonResponse(200, { username: 'admin' }),
      'GET /api/tier-list': () => jsonResponse(200, { scoreTable: DEFAULT_SCORE_TABLE, players: [] }),
      'GET /api/admin/player-profiles': () => jsonResponse(200, []),
    })
    renderApp('/admin/players')

    expect(await screen.findByRole('heading', { name: '선수 관리' })).toBeInTheDocument()
    expect(screen.getByRole('navigation', { name: '담당자 메뉴' })).toBeInTheDocument()
    expect(await screen.findByRole('button', { name: '사람 추가' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '편집 모드' })).toBeInTheDocument()
  })

  it('M3 임시 경로 /admin/players-legacy는 더 이상 없다', async () => {
    mockFetch({ 'GET /api/admin/me': () => jsonResponse(200, { username: 'admin' }) })
    renderApp('/admin/players-legacy')

    expect(await screen.findByText(/Not Found|404/i)).toBeInTheDocument()
  })
})
