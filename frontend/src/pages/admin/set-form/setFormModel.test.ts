// 세트 수정 폼 상태 계산 테스트
import { describe, expect, it } from 'vitest'
import type { RowInput } from '../../../api/registrationTypes'
import { buildSetForm } from '../../../test/setFormFixtures'
import {
  buildEditRequest,
  cleanFormState,
  formFromRequest,
  isDurationValid,
  manualAliasControl,
  pickExistingPlayer,
  pickNewPlayer,
  setManualNickname,
} from './setFormModel'

const row: RowInput = {
  screenRow: 1,
  position: 'TOP',
  nickname: '손님',
  resolution: 'UNRESOLVED',
  playerId: null,
  addAlias: false,
  championKey: null,
  kills: 0,
  deaths: 0,
  assists: 0,
  cs: 0,
  gold: 0,
}
const player = { id: 3, displayName: '홍길동', aliases: [{ id: 1, nickname: '길동이' }] }

describe('setFormModel 폼과 요청', () => {
  it('폼 응답을 복사하고 행을 결과창 순서로 맞춘다', () => {
    const form = buildSetForm().form
    form.top.rows.reverse()
    const state = formFromRequest(form)

    expect(state.top.rows.map((r) => r.screenRow)).toEqual([1, 2, 3, 4, 5])
    expect(state.top).not.toBe(form.top)
    expect('expectedUpdatedAt' in state).toBe(false)
  })

  it('요청에는 expectedUpdatedAt이 붙고 사유·캡처한 사람 포지션·추출 작업이 없다', () => {
    const state = formFromRequest(buildSetForm().form)
    const request = buildEditRequest(state, '2026-10-20T01:02:03.456Z')

    expect(request.expectedUpdatedAt).toBe('2026-10-20T01:02:03.456Z')
    expect(Object.keys(request).sort()).toEqual(
      ['bottom', 'duration', 'expectedUpdatedAt', 'shotDate', 'top', 'topResult', 'topSide'].sort(),
    )
  })

  it('경기 시간을 비우면 모름으로 빈 문자열을 그대로 보낸다', () => {
    const state = formFromRequest(buildSetForm().form)
    expect(buildEditRequest({ ...state, duration: '  ' }, 'x').duration).toBe('')
    expect(cleanFormState({ ...state, duration: ' 25:42 ' }).duration).toBe('25:42')
  })

  it('정리 단계에서 닉네임 공백을 지우고 NEW는 playerId를 비운다', () => {
    const state = formFromRequest(buildSetForm().form)
    const dirty = {
      ...state,
      top: {
        ...state.top,
        rows: state.top.rows.map((r, i) =>
          i === 0 ? { ...r, resolution: 'NEW' as const, playerId: 9, addAlias: true, nickname: ' 새닉 ' } : r,
        ),
      },
    }
    const first = cleanFormState(dirty).top.rows[0]
    expect(first).toMatchObject({ nickname: '새닉', playerId: null, addAlias: false, resolution: 'NEW' })
  })

  it('경기 시간은 비어 있거나 분:초 모양이어야 한다', () => {
    expect(['', '  ', '25:42', '105:03'].every(isDurationValid)).toBe(true)
    expect(['2542', '25:7', '25:61', 'abc'].some(isDurationValid)).toBe(false)
  })
})

describe('setFormModel 선수 칸', () => {
  it('기존 선수를 고르면 대표 닉네임으로 맞추고 별칭 추가는 끈다', () => {
    const picked = pickExistingPlayer({ ...row, addAlias: true }, player)
    expect(picked).toMatchObject({ resolution: 'EXISTING', playerId: 3, nickname: '길동이', addAlias: false })
    expect(pickExistingPlayer(row, { ...player, aliases: [] }).nickname).toBe('홍길동')
  })

  it('새 미등록 선수는 playerId를 비운다', () => {
    const picked = pickNewPlayer({ ...row, resolution: 'EXISTING', playerId: 3, addAlias: true })
    expect(picked).toMatchObject({ resolution: 'NEW', playerId: null, addAlias: false, nickname: '손님' })
  })

  it('별칭 체크박스는 그 선수가 모르는 닉네임일 때만 보인다', () => {
    const existing = pickExistingPlayer(row, player)
    const known = ['홍길동', '길동이']
    expect(manualAliasControl(existing, known).visible).toBe(false)
    expect(manualAliasControl({ ...existing, nickname: '길동이 ' }, known).visible).toBe(false)
    expect(manualAliasControl({ ...existing, nickname: '새닉' }, known).visible).toBe(true)
    expect(manualAliasControl({ ...existing, nickname: '' }, known).visible).toBe(false)
    expect(manualAliasControl({ ...row, nickname: '새닉' }, known).visible).toBe(false)
  })

  it('닉네임을 아는 값으로 되돌리면 별칭 추가도 꺼진다', () => {
    const typed = setManualNickname(pickExistingPlayer(row, player), '새닉', ['길동이'])
    const checked = { ...typed, addAlias: true }
    expect(setManualNickname(checked, '새닉2', ['길동이']).addAlias).toBe(true)
    expect(setManualNickname(checked, '길동이', ['길동이'])).toMatchObject({ nickname: '길동이', addAlias: false })
  })
})
