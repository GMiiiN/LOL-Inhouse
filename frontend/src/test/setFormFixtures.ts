// 세트 수정 화면 테스트용 가짜 서버 데이터(GET /api/admin/sets/{id}/form 응답). 선수 id는 1~10, 챔피언은 registrationFixtures와 같다.
import type { EditEvaluation, SetFormResponse } from '../api/recordEditTypes'
import type { RowInput, TeamInput } from '../api/registrationTypes'
import { baseEvaluation, buildDraft } from './registrationFixtures'

export const FORM_UPDATED_AT = '2026-10-20T01:02:03.456Z'

export const editEvaluation: EditEvaluation = {
  ...baseEvaluation,
  setNo: 2,
  detection: { method: 'OVERLAP', overlap: 5, uncertain: false },
  fearlessConflicts: [],
}

function existing(team: TeamInput, firstPlayerId: number): TeamInput {
  const rows: RowInput[] = team.rows.map((row, index) => ({
    ...row,
    resolution: 'EXISTING',
    playerId: firstPlayerId + index,
    nickname: `닉${firstPlayerId + index}`,
    addAlias: false,
  }))
  return { ...team, rows }
}

export function buildSetForm(overrides: Partial<SetFormResponse> = {}): SetFormResponse {
  const { defaults } = buildDraft()
  return {
    setId: 7,
    duelId: 11,
    duelSeq: 2,
    sessionId: 5,
    sessionStartDate: '2026-10-20',
    setNo: 2,
    deleted: false,
    updatedAt: FORM_UPDATED_AT,
    hasImage: true,
    form: {
      topSide: 'BLUE',
      topResult: 'WIN',
      shotDate: '2026-10-12',
      duration: '25:42',
      top: existing(defaults.top, 1),
      bottom: existing(defaults.bottom, 6),
      expectedUpdatedAt: FORM_UPDATED_AT,
    },
    evaluation: editEvaluation,
    restrictedChampions: ['Champion03'],
    fearlessConflicts: [],
    ...overrides,
  }
}

export const formPlayers = Array.from({ length: 10 }, (_, index) => ({
  id: index + 1,
  displayName: `선수${index + 1}`,
  realName: `선수${index + 1}`,
  registered: true,
  mainPosition: null,
  aliases: [{ id: index + 1, nickname: `닉${index + 1}` }],
}))
