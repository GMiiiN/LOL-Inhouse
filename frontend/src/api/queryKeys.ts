import type { Period, RankingQuery, RecordsQuery } from './publicTypes'

// TanStack Query 키 모음
// 배열 앞부분이 같으면 한 번에 무효화할 수 있도록 ['admin', 종류, ...] 구조로 둔다.
export const ME_KEY = ['admin', 'me'] as const
export const PLAYERS_KEY = ['admin', 'players'] as const
export const SESSIONS_KEY = ['admin', 'inhouse-sessions'] as const
export const sessionKey = (id: number) => [...SESSIONS_KEY, id] as const
// M4 세트 등록
export const CHAMPIONS_KEY = ['admin', 'champions'] as const
export const registrationContextKey = (duelId: number) => ['admin', 'registration-context', duelId] as const
export const extractionKey = (jobId: number) => ['admin', 'extractions', jobId] as const
export const draftKey = (jobId: number) => ['admin', 'extractions', jobId, 'draft'] as const
// M4.5 라인 티어·점수표 (티어리스트·점수표는 공개 조회라 ['admin'] 아래가 아니다)
export const TIER_LIST_KEY = ['tier-list'] as const
export const SCORE_TABLE_KEY = ['score-table'] as const
export const PLAYER_PROFILES_KEY = ['admin', 'player-profiles'] as const
// M5 공개 조회. 모두 ['public', ...]로 시작해 담당자가 기록을 바꾸면 ['public'] 하나로 무효화한다(로그아웃해도 지우지 않는다)
export const PUBLIC_KEY = ['public'] as const
export const recordsKey = (query: RecordsQuery) => ['public', 'records', query] as const
export const duelKey = (duelId: number) => ['public', 'duel', duelId] as const
export const setKey = (setId: number) => ['public', 'set', setId] as const
export const publicPlayersKey = (period: Period) => ['public', 'players', period] as const
export const publicPlayerKey = (playerId: number, period: Period) => ['public', 'player', playerId, period] as const
export const rankingsKey = (query: RankingQuery) => ['public', 'rankings', query] as const
export const championStatsKey = (period: Period) => ['public', 'champion-stats', period] as const
export const duosKey = (period: Period) => ['public', 'duos', period] as const
export const HOME_KEY = ['public', 'home'] as const
export const PUBLIC_CHAMPIONS_KEY = ['public', 'champions'] as const
export const TIER_RECOMMENDATIONS_KEY = ['admin', 'tier-recommendations'] as const
export const DELETED_SETS_KEY = ['admin', 'deleted-sets'] as const
export const setFormKey = (setId: number) => ['admin', 'set-form', setId] as const
