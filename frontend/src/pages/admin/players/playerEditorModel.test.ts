// 선수 편집기 모델 테스트: 폼 초기화, 쉼표 분리, 검증 규칙, 요청 만들기
import { describe, expect, it } from 'vitest'
import { profile } from '../../../test/tierListFixtures'
import {
  buildRequest,
  editorMode,
  initialForm,
  parseNicknames,
  validateEditor,
  type EditorForm,
} from './playerEditorModel'

const context = { otherRealNames: ['김철수'], existingAliasCount: 0 }

function form(overrides: Partial<EditorForm> = {}): EditorForm {
  return { ...initialForm(null), realName: '홍길동', nicknames: '길동', ...overrides }
}

const withLane = (f: EditorForm): EditorForm => ({ ...f, lanes: { ...f.lanes, MID: { tier: 2, sub: 'HIGH' } } })

describe('parseNicknames', () => {
  it('쉼표(전각 포함)로 나누고 공백·빈 항목을 버린다', () => {
    expect(parseNicknames(' 철수 ,철수부캐，, ')).toEqual(['철수', '철수부캐'])
    expect(parseNicknames('  ')).toEqual([])
  })
})

describe('initialForm', () => {
  it('새 사람은 모든 라인이 안 함이고 우열 기본값은 중이다', () => {
    const initial = initialForm(null)

    expect(initial.lanes.TOP).toEqual({ tier: '', sub: 'MID' })
    expect(initial.nicknames).toBe('')
  })

  it('기존 선수는 실명·주력 포지션·라인·닉네임(쉼표 한 줄)을 채운다', () => {
    const initial = initialForm(
      profile({
        id: 1,
        displayName: '홍길동',
        mainPosition: 'ADC',
        aliases: [
          { id: 1, nickname: 'a' },
          { id: 2, nickname: 'b' },
        ],
        lanes: { TOP: null, JUNGLE: null, MID: { tier: 3, sub: 'LOW' }, ADC: null, SUPPORT: null },
      }),
    )

    expect(initial).toMatchObject({ realName: '홍길동', mainPosition: 'ADC', nicknames: 'a, b' })
    expect(initial.lanes.MID).toEqual({ tier: 3, sub: 'LOW' })
  })
})

describe('editorMode', () => {
  it('프로필 없음 / 등록 / 미등록으로 나눈다', () => {
    expect(editorMode(null)).toBe('create')
    expect(editorMode(profile({ id: 1, displayName: 'a' }))).toBe('edit-registered')
    expect(editorMode(profile({ id: 1, displayName: 'a', registered: false, realName: null }))).toBe('edit-unregistered')
  })
})

describe('validateEditor', () => {
  it('올바른 새 사람은 오류가 없다', () => {
    expect(validateEditor(withLane(form()), 'create', context)).toEqual({})
  })

  it('이름이 비면 막고, 50자 초과도 막는다', () => {
    expect(validateEditor(withLane(form({ realName: '  ' })), 'create', context).realName).toBe('이름을 입력해 주세요.')
    expect(validateEditor(withLane(form({ realName: '가'.repeat(51) })), 'create', context).realName).toBeDefined()
  })

  it('다른 선수와 같은 이름(앞뒤 공백 제외)은 막는다', () => {
    expect(validateEditor(withLane(form({ realName: ' 김철수 ' })), 'create', context).realName).toBe('같은 이름이 이미 있어요.')
  })

  it('새로 만들 때만 최소 한 라인을 요구한다', () => {
    expect(validateEditor(form(), 'create', context).lanes).toBe('최소 한 라인은 티어를 골라 주세요.')
    expect(validateEditor(form(), 'edit-registered', context).lanes).toBeUndefined()
  })

  it('미등록 선수는 이름을 비워 둘 수 있지만 등록 선수는 안 된다', () => {
    expect(validateEditor(form({ realName: '' }), 'edit-unregistered', context).realName).toBeUndefined()
    expect(validateEditor(form({ realName: '' }), 'edit-registered', context).realName).toBeDefined()
  })

  it('닉네임은 최소 1개, 대소문자만 다른 중복은 막는다', () => {
    expect(validateEditor(withLane(form({ nicknames: ' , ' })), 'create', context).nicknames).toBe('닉네임을 하나 이상 입력해 주세요.')
    expect(validateEditor(withLane(form({ nicknames: 'Abc, abc' })), 'create', context).nicknames).toBe('같은 닉네임이 두 번 들어 있어요.')
    expect(validateEditor(withLane(form({ nicknames: 'a'.repeat(51) })), 'create', context).nicknames).toBeDefined()
  })

  it('새로 만들 때는 닉네임 10개까지, 수정은 기존 개수가 더 많으면 그 개수까지 허용한다', () => {
    const names = (n: number) => Array.from({ length: n }, (_, i) => `n${i}`).join(',')

    expect(validateEditor(withLane(form({ nicknames: names(10) })), 'create', context).nicknames).toBeUndefined()
    expect(validateEditor(withLane(form({ nicknames: names(11) })), 'create', context).nicknames).toBeDefined()
    expect(validateEditor(form({ nicknames: names(11) }), 'edit-registered', { ...context, existingAliasCount: 11 }).nicknames).toBeUndefined()
    expect(validateEditor(form({ nicknames: names(12) }), 'edit-registered', { ...context, existingAliasCount: 11 }).nicknames).toBeDefined()
  })
})

describe('buildRequest', () => {
  it('안 함은 null로 명시하고, 실명은 다듬고, 닉네임은 목록으로 바꾼다', () => {
    const request = buildRequest(
      withLane(form({ realName: ' 홍길동 ', nicknames: '길동, 홍', mainPosition: 'MID' })),
    )

    expect(request).toEqual({
      realName: '홍길동',
      mainPosition: 'MID',
      lanes: { TOP: null, JUNGLE: null, MID: { tier: 2, sub: 'HIGH' }, ADC: null, SUPPORT: null },
      nicknames: ['길동', '홍'],
      flagged: false,
    })
    expect(request).not.toHaveProperty('recordChange')
  })

  it('실명이 비면 null, 주력 포지션을 고르지 않으면 null', () => {
    const request = buildRequest(form({ realName: ' ', mainPosition: '' }))

    expect(request.realName).toBeNull()
    expect(request.mainPosition).toBeNull()
  })
})

describe('flagged(요주 인물)', () => {
  it('프로필 값을 폼에 채우고, 새 사람은 false이며, 요청에 그대로 담는다', () => {
    expect(initialForm(null).flagged).toBe(false)
    expect(initialForm(profile({ id: 1, displayName: '홍길동', flagged: true })).flagged).toBe(true)
    expect(buildRequest(form({ flagged: true })).flagged).toBe(true)
  })
})
