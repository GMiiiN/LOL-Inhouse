// M5 공개 조회 호출 함수(로그인 없이). URL과 쿼리 파라미터 이름은 docs/design/m5-design.md §3, §10을 따른다.
import { apiRequest } from './client'
import type {
  ChampionStats,
  DuelDetail,
  Home,
  Period,
  PlayerList,
  PlayerRecord,
  Ranking,
  RankingQuery,
  RecordPage,
  RecordsPageParams,
  RecordsQuery,
  SetView,
} from './publicTypes'
import type { ChampionList } from './registrationTypes'

// 값이 없는 파라미터는 빼고 '?a=1&b=2'를 만든다(키 순서는 넘긴 순서). 값이 하나도 없으면 빈 문자열.
export function toQueryString(params: Record<string, string | number | undefined | null>): string {
  const search = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== null && value !== '') search.set(key, String(value))
  }
  const text = search.toString()
  return text ? `?${text}` : ''
}

// 기록 화면의 URL 파라미터(player)를 API 파라미터(playerId)로 바꾼다. 숫자가 아닌 player는 버린다.
export function toRecordsQuery(page: RecordsPageParams): RecordsQuery {
  const playerId = page.player !== undefined && /^\d+$/.test(page.player) ? Number(page.player) : undefined
  return { from: page.from, to: page.to, playerId, champion: page.champion, championScope: page.championScope }
}

const recordsParams = (query: RecordsQuery) => ({
  from: query.from,
  to: query.to,
  playerId: query.playerId,
  champion: query.champion,
  championScope: query.championScope,
  before: query.before,
  limit: query.limit,
})

export const getRecords = (query: RecordsQuery = {}) =>
  apiRequest<RecordPage>(`/api/records${toQueryString(recordsParams(query))}`)

// CSV 링크(<a href download>). 쿠키가 필요 없는 GET이며 기록 화면의 현재 필터가 그대로 실린다(커서는 없음).
export const recordsExportUrl = (query: RecordsQuery = {}) =>
  `/api/records/export${toQueryString({ ...recordsParams(query), before: undefined, limit: undefined })}`

export const getDuel = (duelId: number) => apiRequest<DuelDetail>(`/api/duels/${duelId}`)
export const getSet = (setId: number) => apiRequest<SetView>(`/api/sets/${setId}`)

const periodParams = (period: Period) => ({ from: period.from, to: period.to })

export const getPlayers = (period: Period = {}) =>
  apiRequest<PlayerList>(`/api/players${toQueryString(periodParams(period))}`)

export const getPlayer = (playerId: number, period: Period = {}) =>
  apiRequest<PlayerRecord>(`/api/players/${playerId}${toQueryString(periodParams(period))}`)

export const getRankings = (query: RankingQuery = {}) =>
  apiRequest<Ranking>(
    `/api/rankings${toQueryString({ position: query.position, sort: query.sort, ...periodParams(query) })}`,
  )

export const getChampionStats = (period: Period = {}) =>
  apiRequest<ChampionStats>(`/api/champion-stats${toQueryString(periodParams(period))}`)

export const getHome = () => apiRequest<Home>('/api/home')

// 공개 챔피언 목록(이름·아이콘). 관리용 /api/admin/champions와 같은 응답이다.
export const getPublicChampions = () => apiRequest<ChampionList>('/api/champions')

// 담당자 전용 원본 결과창 이미지 경로(blob으로 받는다). 수동 입력 세트는 404 SET_IMAGE_NOT_FOUND.
export const adminSetImagePath = (setId: number) => `/api/admin/sets/${setId}/image`
