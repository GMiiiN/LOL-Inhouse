// reviewModel 순수 함수 테스트: 라인 정렬, 포지션 맞바꾸기, 캡처한 사람 포지션 재배정, 밴 라벨, 선수 resolution·별칭 규칙, 명령 만들기
import { describe, expect, it } from 'vitest'
import { ApiError } from '../../../api/client'
import type { DraftRow, RowInput } from '../../../api/registrationTypes'
import { buildDraft, baseEvaluation, championId } from '../../../test/registrationFixtures'
import {
  aliasControl,
  banLabels,
  buildRequest,
  canSave,
  changeCapturerPosition,
  initReviewState,
  issuesFromError,
  issuesFromEvaluation,
  playerStatus,
  rowsByLane,
  selectExistingPlayer,
  selectNewPlayer,
  setBan,
  setRowNickname,
  slotIssues,
  swapPosition,
  topTeamPositions,
} from './reviewModel'

const draft = buildDraft()
const fresh = () => initReviewState(draft.defaults)
const draftRowOf = (team: 'top' | 'bottom', screenRow: number): DraftRow =>
  draft[team].rows.find((r) => r.screenRow === screenRow)!
const rowOf = (state: ReturnType<typeof fresh>, team: 'top' | 'bottom', screenRow: number): RowInput =>
  state[team].rows.find((r) => r.screenRow === screenRow)!

describe('정렬과 포지션', () => {
  it('라인 순(탑→서포터)으로 정렬하되 결과창 행 번호는 유지한다 (캡처한 사람 원딜: 1행이 원딜)', () => {
    const rows = rowsByLane(fresh().top)

    expect(rows.map((r) => r.position)).toEqual(['TOP', 'JUNGLE', 'MID', 'ADC', 'SUPPORT'])
    expect(rows.map((r) => r.screenRow)).toEqual([2, 3, 4, 1, 5])
  })

  it('topTeamPositions: 캡처한 사람 포지션이 첫 행, 나머지는 기본 순서', () => {
    expect(topTeamPositions('ADC')).toEqual(['ADC', 'TOP', 'JUNGLE', 'MID', 'SUPPORT'])
    expect(topTeamPositions('SUPPORT')).toEqual(['SUPPORT', 'TOP', 'JUNGLE', 'MID', 'ADC'])
  })

  it('포지션을 바꾸면 그 포지션을 가진 같은 팀 행과 맞바꾼다', () => {
    const next = swapPosition(fresh(), 'top', 2, 'MID') // 2행 탑 → 미드, 4행 미드 → 탑

    expect(rowOf(next, 'top', 2).position).toBe('MID')
    expect(rowOf(next, 'top', 4).position).toBe('TOP')
    expect(rowOf(next, 'top', 1).position).toBe('ADC')
    expect(rowOf(next, 'bottom', 1).position).toBe('TOP') // 다른 팀은 그대로
  })

  it('같은 포지션을 고르거나 없는 행이면 변하지 않는다', () => {
    const state = fresh()

    expect(swapPosition(state, 'top', 2, 'TOP')).toEqual(state)
  })

  it('캡처한 사람 포지션을 바꾸면 위쪽 팀만 규칙대로 다시 배정한다 (직접 맞바꾼 것은 사라짐)', () => {
    const swapped = swapPosition(fresh(), 'top', 2, 'MID')

    const next = changeCapturerPosition(swapped, 'SUPPORT')

    expect(next.capturerPosition).toBe('SUPPORT')
    expect(next.top.rows.map((r) => r.position)).toEqual(['SUPPORT', 'TOP', 'JUNGLE', 'MID', 'ADC'])
    expect(next.bottom.rows.map((r) => r.position)).toEqual(['TOP', 'JUNGLE', 'MID', 'ADC', 'SUPPORT'])
  })
})

describe('banLabels', () => {
  it('위쪽 팀이 블루면 위쪽은 B1~B5, 아래쪽은 R1~R5', () => {
    expect(banLabels('BLUE')).toEqual({
      top: ['B1', 'B2', 'B3', 'B4', 'B5'],
      bottom: ['R1', 'R2', 'R3', 'R4', 'R5'],
    })
  })

  it('위쪽 팀이 레드면 반대', () => {
    expect(banLabels('RED').top[0]).toBe('R1')
    expect(banLabels('RED').bottom[4]).toBe('B5')
  })
})

describe('선수 resolution', () => {
  it('초기 상태: 정확 일치는 EXISTING, 후보 상태는 UNRESOLVED', () => {
    const state = fresh()

    expect(rowOf(state, 'top', 1)).toMatchObject({ resolution: 'EXISTING', playerId: 1, addAlias: false })
    expect(rowOf(state, 'top', 2)).toMatchObject({ resolution: 'UNRESOLVED', playerId: null })
  })

  it('후보 상태 그대로는 UNRESOLVED로 명령에 담긴다 (저장은 서버가 거부)', () => {
    const request = buildRequest(fresh(), null)

    expect(request.top.rows[1]).toMatchObject({ resolution: 'UNRESOLVED', playerId: null, addAlias: false })
  })

  it('후보를 고르면 EXISTING + 직접 고른 선수라 별칭 추가가 기본 체크된다', () => {
    const next = selectExistingPlayer(rowOf(fresh(), 'top', 2), draftRowOf('top', 2), {
      playerId: 101,
      nicknames: ['홍길동1'],
    })

    expect(next).toMatchObject({ resolution: 'EXISTING', playerId: 101, addAlias: true })
  })

  it('"새 미등록 선수로"는 NEW + playerId=null + addAlias=false', () => {
    const next = selectNewPlayer({ ...rowOf(fresh(), 'top', 2), addAlias: true })

    expect(next).toMatchObject({ resolution: 'NEW', playerId: null, addAlias: false })
  })

  it('후보를 거부하고 NEW를 고르면 명령에 NEW로 실린다', () => {
    const state = fresh()
    const changed = { ...state, top: { ...state.top, rows: state.top.rows.map((r) => (r.screenRow === 2 ? selectNewPlayer(r) : r)) } }

    expect(buildRequest(changed, null).top.rows[1]).toMatchObject({ resolution: 'NEW', playerId: null, nickname: '홍길' })
  })

  it('명령 만들기는 resolution과 어긋난 playerId·addAlias를 정리한다', () => {
    const state = fresh()
    const broken = {
      ...state,
      top: {
        ...state.top,
        rows: state.top.rows.map((r) =>
          r.screenRow === 1 ? { ...r, resolution: 'NEW' as const, playerId: 9, addAlias: true } : r,
        ),
      },
    }

    expect(buildRequest(broken, null).top.rows[0]).toMatchObject({ resolution: 'NEW', playerId: null, addAlias: false })
  })

  it('newDuel을 주면 명령에 담기고, 없으면 키 자체가 없다', () => {
    expect(buildRequest(fresh(), { fearlessMode: 'CONTINUE' }).newDuel).toEqual({ fearlessMode: 'CONTINUE' })
    expect('newDuel' in buildRequest(fresh(), null)).toBe(false)
  })

  it('상태 배지', () => {
    const state = fresh()

    expect(playerStatus(rowOf(state, 'top', 1), draftRowOf('top', 1)).label).toBe('정확')
    expect(playerStatus(rowOf(state, 'top', 2), draftRowOf('top', 2)).label).toBe('후보 2명')
    expect(playerStatus(selectNewPlayer(rowOf(state, 'top', 2)), draftRowOf('top', 2)).label).toBe('새 선수')
  })
})

describe('별칭 추가 체크박스 규칙', () => {
  const autoRow = (type: 'ONE_CHAR_AUTO' | 'TRUNCATED_AUTO'): { row: RowInput; draftRow: DraftRow } => {
    const base = draftRowOf('top', 1)
    return {
      draftRow: { ...base, match: { ...base.match, type } },
      row: { ...rowOf(fresh(), 'top', 1), playerId: base.match.playerId },
    }
  }

  it('정확 일치로 연결되면 숨긴다', () => {
    expect(aliasControl(rowOf(fresh(), 'top', 1), draftRowOf('top', 1)).visible).toBe(false)
  })

  it('자동 연결(한 글자 차이·잘림)이면 보이지만 해제·비활성', () => {
    for (const type of ['ONE_CHAR_AUTO', 'TRUNCATED_AUTO'] as const) {
      const { row, draftRow } = autoRow(type)
      expect(aliasControl({ ...row, addAlias: true }, draftRow)).toMatchObject({ visible: true, disabled: true, checked: false })
    }
  })

  it('자동 연결된 선수를 다시 고르면 체크하지 않는다', () => {
    const { row, draftRow } = autoRow('ONE_CHAR_AUTO')

    expect(selectExistingPlayer(row, draftRow, { playerId: row.playerId!, nicknames: [] }).addAlias).toBe(false)
  })

  it('이미 그 닉네임을 가진 선수를 직접 고르면 숨기고 체크하지 않는다', () => {
    const next = selectExistingPlayer(rowOf(fresh(), 'top', 2), draftRowOf('top', 2), { playerId: 101, nicknames: ['홍길'] })

    expect(next.addAlias).toBe(false)
    expect(aliasControl(next, draftRowOf('top', 2), ['홍길']).visible).toBe(false)
  })

  it('잘린 닉네임은 전체 닉네임을 입력해야 체크할 수 있다', () => {
    const base = draftRowOf('top', 2)
    const truncated: DraftRow = { ...base, truncated: true, readNickname: '2630005513…' }
    const row: RowInput = { ...rowOf(fresh(), 'top', 2), nickname: '2630005513…' }

    const picked = selectExistingPlayer(row, truncated, { playerId: 101, nicknames: [] })
    expect(picked.addAlias).toBe(false)
    expect(aliasControl(picked, truncated)).toMatchObject({ visible: true, disabled: true })

    const full = setRowNickname(picked, truncated, '26300055130530')
    expect(aliasControl(full, truncated)).toMatchObject({ visible: true, disabled: false })
  })

  it('별칭 추가가 켜진 채 닉네임이 다시 잘린 상태가 되면 끈다', () => {
    const base = draftRowOf('top', 2)
    const row: RowInput = { ...rowOf(fresh(), 'top', 2), resolution: 'EXISTING', playerId: 101, addAlias: true, nickname: '긴닉네임' }

    expect(setRowNickname(row, base, '긴닉…').addAlias).toBe(false)
  })
})

describe('slotIssues (화면 강조용)', () => {
  it('미확정 선수, 빈 챔피언, 세트 안 중복, 제한 챔피언, 빈 밴, 같은 선수 중복을 표시한다', () => {
    let state = fresh()
    state = {
      ...state,
      top: {
        ...state.top,
        rows: state.top.rows.map((r) => {
          if (r.screenRow === 3) return { ...r, championKey: null }
          if (r.screenRow === 4) return { ...r, championKey: championId(0) } // 1행과 중복
          if (r.screenRow === 5) return { ...r, playerId: 1 } // 1행과 같은 선수
          return r
        }),
      },
    }
    state = setBan(state, 'bottom', 2, null)

    const issues = slotIssues(state, new Set([championId(0)]))

    expect(issues['row:top:2']).toContain('선수를 골라 주세요')
    expect(issues['row:top:3']).toContain('챔피언을 골라 주세요')
    expect(issues['row:top:4']).toContain('이 세트에서 같은 챔피언이 두 번 쓰였습니다')
    expect(issues['row:top:1']).toContain('피어리스 제한 챔피언입니다')
    expect(issues['row:top:5']).toContain('같은 선수가 두 번 들어 있습니다')
    expect(issues['ban:bottom:2']).toContain('밴 챔피언을 골라 주세요')
  })

  it('문제가 없으면 비어 있다', () => {
    const withPlayer = fresh()
    withPlayer.top.rows[1] = selectNewPlayer(withPlayer.top.rows[1])

    expect(slotIssues(withPlayer, new Set())).toEqual({})
  })
})

describe('ReviewIssues 모델', () => {
  it('check 응답의 errors/warnings와 422의 violations/warnings를 같은 모양으로 바꾼다', () => {
    const fromCheck = issuesFromEvaluation({ ...baseEvaluation, errors: ['가'], warnings: ['나'] })
    const fromError = issuesFromError(
      new ApiError(422, { code: 'SET_RULES_VIOLATED', violations: ['가'], warnings: ['나'] }),
    )

    expect(fromError).toEqual(fromCheck)
  })

  it('SET_RULES_VIOLATED가 아닌 오류는 null', () => {
    expect(issuesFromError(new ApiError(409, { code: 'DUPLICATE_SET' }))).toBeNull()
    expect(issuesFromError(new Error('x'))).toBeNull()
  })

  it('canSave: 오류 없음 + 최신 검사 + 중복 아님일 때만', () => {
    expect(canSave(baseEvaluation, false, false)).toBe(true)
    expect(canSave({ ...baseEvaluation, errors: ['x'] }, false, false)).toBe(false)
    expect(canSave(baseEvaluation, true, false)).toBe(false)
    expect(canSave(baseEvaluation, false, true)).toBe(false)
    expect(canSave({ ...baseEvaluation, duplicateSet: { setId: 1, duelId: 1, setNo: 1 } }, false, false)).toBe(false)
    expect(canSave(null, false, false)).toBe(false)
  })
})
