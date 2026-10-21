// 공개 조회 호출 함수: URL과 쿼리 파라미터 이름이 설계(docs/design/m5-design.md §3, §10)와 같은지
import { describe, expect, it } from 'vitest'
import { jsonResponse, mockFetch } from '../test/mockFetch'
import { getRankings, getRecords, recordsExportUrl, toQueryString, toRecordsQuery } from './public'

describe('toQueryString', () => {
  it('비어 있거나 undefined인 값은 빼고, 모두 비면 빈 문자열이다', () => {
    expect(toQueryString({ from: '2026-10-01', to: undefined, champion: '' })).toBe('?from=2026-10-01')
    expect(toQueryString({ from: undefined })).toBe('')
  })
})

describe('toRecordsQuery', () => {
  it('화면의 player를 API의 playerId 숫자로 바꾸고 숫자가 아니면 버린다', () => {
    expect(toRecordsQuery({ player: '3', champion: 'Ahri', championScope: 'PLAYER_PICK', from: '2026-10-01' })).toEqual({
      from: '2026-10-01',
      to: undefined,
      playerId: 3,
      champion: 'Ahri',
      championScope: 'PLAYER_PICK',
    })
    expect(toRecordsQuery({ player: 'abc' }).playerId).toBeUndefined()
  })
})

describe('공개 조회 URL', () => {
  it('CSV 링크는 현재 필터를 싣고 커서(before, limit)는 싣지 않는다', () => {
    const url = recordsExportUrl({
      from: '2026-10-01',
      to: '2026-10-31',
      playerId: 3,
      champion: 'Ahri',
      championScope: 'PLAYER_PICK',
      before: '2026-10-20',
      limit: 10,
    })
    expect(url).toBe(
      '/api/records/export?from=2026-10-01&to=2026-10-31&playerId=3&champion=Ahri&championScope=PLAYER_PICK',
    )
    expect(recordsExportUrl()).toBe('/api/records/export')
  })

  it('getRecords는 /api/records에 playerId·before·limit을 쿼리로 보낸다', async () => {
    const { calls } = mockFetch({
      'GET /api/records?playerId=3&before=2026-10-20&limit=10': () => jsonResponse(200, { sessions: [], nextBefore: null }),
    })
    await expect(getRecords({ playerId: 3, before: '2026-10-20', limit: 10 })).resolves.toEqual({
      sessions: [],
      nextBefore: null,
    })
    expect(calls).toHaveLength(1)
  })

  it('getRankings는 position·sort·기간을 보낸다', async () => {
    mockFetch({
      'GET /api/rankings?position=MID&sort=KDA&from=2026-10-01': () =>
        jsonResponse(200, { position: 'MID', sort: 'KDA', minSets: 5, totalSets: 0, entries: [] }),
    })
    const ranking = await getRankings({ position: 'MID', sort: 'KDA', from: '2026-10-01' })
    expect(ranking.minSets).toBe(5)
  })
})
