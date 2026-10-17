// TanStack Query 키 모음
// 배열 앞부분이 같으면 한 번에 무효화할 수 있도록 ['admin', 종류, ...] 구조로 둔다.
export const ME_KEY = ['admin', 'me'] as const
export const PLAYERS_KEY = ['admin', 'players'] as const
export const SESSIONS_KEY = ['admin', 'inhouse-sessions'] as const
export const sessionKey = (id: number) => [...SESSIONS_KEY, id] as const
