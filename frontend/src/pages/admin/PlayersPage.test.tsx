// 선수 관리 화면 테스트: 등록 폼 검증, 충돌·LAST_ALIAS 표시, 미등록 → 등록 전환
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import type { Player } from '../../api/types'
import { jsonResponse, mockFetch, problemResponse } from '../../test/mockFetch'
import { renderApp } from '../../test/renderApp'
import { validatePlayerForm } from './playerFormValidation'

const registered: Player = {
  id: 1,
  displayName: '홍길동',
  realName: '홍길동',
  registered: true,
  mainPosition: 'MID',
  aliases: [{ id: 10, nickname: '길동이' }],
}
const unregistered: Player = {
  id: 2,
  displayName: '낯선닉',
  realName: null,
  registered: false,
  mainPosition: null,
  aliases: [{ id: 20, nickname: '낯선닉' }],
}

const baseRoutes = {
  'GET /api/admin/me': () => jsonResponse(200, { username: 'admin' }),
  'GET /api/admin/players': () => jsonResponse(200, [registered, unregistered]),
}

describe('validatePlayerForm', () => {
  const valid = { realName: '홍길동', mainPosition: 'MID' as const, nicknames: ['a'] }

  it('올바른 값은 오류가 없다', () => {
    expect(validatePlayerForm(valid, 'create')).toEqual({})
  })

  it('실명 50자 초과, 닉네임 0개·11개·51자·중복을 막는다', () => {
    expect(validatePlayerForm({ ...valid, realName: '가'.repeat(51) }, 'create').realName).toBeDefined()
    expect(validatePlayerForm({ ...valid, realName: '  ' }, 'create').realName).toBeDefined()
    expect(validatePlayerForm({ ...valid, nicknames: [] }, 'create').nicknames).toBeDefined()
    expect(validatePlayerForm({ ...valid, nicknames: Array(11).fill('x').map((n, i) => n + i) }, 'create').nicknames).toBeDefined()
    expect(validatePlayerForm({ ...valid, nicknames: ['a'.repeat(51)] }, 'create').nicknames).toBeDefined()
    expect(validatePlayerForm({ ...valid, nicknames: ['Abc', ' abc '] }, 'create').nicknames).toBeDefined()
    expect(validatePlayerForm({ ...valid, nicknames: [''] }, 'create').nicknames).toBeDefined()
  })

  it('10개까지는 허용하고, 수정 모드에서는 닉네임을 검사하지 않는다', () => {
    expect(validatePlayerForm({ ...valid, nicknames: Array.from({ length: 10 }, (_, i) => `n${i}`) }, 'create')).toEqual({})
    expect(validatePlayerForm({ ...valid, nicknames: [] }, 'edit-registered')).toEqual({})
  })

  it('포지션은 비워도(모름) 세 경우 모두 통과한다', () => {
    const noPosition = { ...valid, mainPosition: '' as const }
    expect(validatePlayerForm(noPosition, 'create')).toEqual({})
    expect(validatePlayerForm(noPosition, 'edit-registered')).toEqual({})
    expect(validatePlayerForm(noPosition, 'edit-unregistered')).toEqual({})
  })

  it('등록 선수 수정은 실명을 비울 수 없지만, 미등록 선수 수정은 비워도 된다', () => {
    const blank = { ...valid, realName: '  ', nicknames: [] }
    expect(validatePlayerForm(blank, 'edit-registered').realName).toBeDefined()
    expect(validatePlayerForm(blank, 'edit-unregistered')).toEqual({})
    expect(validatePlayerForm({ ...blank, realName: '가'.repeat(51) }, 'edit-unregistered').realName).toBeDefined()
  })
})

describe('PlayersPage', () => {
  it('등록 폼이 검증에 실패하면 서버를 호출하지 않고 메시지를 보여 준다', async () => {
    const user = userEvent.setup()
    const { calls } = mockFetch(baseRoutes)
    renderApp('/admin/players')

    await user.click(await screen.findByRole('button', { name: '새 선수 등록' }))
    await user.click(screen.getByRole('button', { name: '등록' }))

    expect(screen.getByText('실명을 입력해 주세요.')).toBeInTheDocument()
    expect(screen.getByText('닉네임을 입력해 주세요.')).toBeInTheDocument()
    expect(calls.some((c) => c.method === 'POST')).toBe(false)
  })

  it('올바른 값이면 POST /api/admin/players로 보낸다', async () => {
    const user = userEvent.setup()
    const { calls } = mockFetch({
      ...baseRoutes,
      'POST /api/admin/players': () => jsonResponse(201, registered),
    })
    renderApp('/admin/players')

    await user.click(await screen.findByRole('button', { name: '새 선수 등록' }))
    await user.type(screen.getByLabelText('실명'), ' 김철수 ')
    await user.selectOptions(screen.getByLabelText('주 포지션'), 'ADC')
    await user.type(screen.getByLabelText('게임 닉네임 1'), '철수')
    await user.click(screen.getByRole('button', { name: '닉네임 칸 추가' }))
    await user.type(screen.getByLabelText('게임 닉네임 2'), '철수부캐')
    await user.click(screen.getByRole('button', { name: '등록' }))

    const post = await waitForCall(() => calls.find((c) => c.method === 'POST'))
    expect(JSON.parse(post.body ?? '')).toEqual({
      realName: '김철수',
      mainPosition: 'ADC',
      nicknames: ['철수', '철수부캐'],
    })
  })

  it('주 포지션을 고르지 않고(모름) 등록하면 mainPosition null로 보낸다', async () => {
    const user = userEvent.setup()
    const { calls } = mockFetch({
      ...baseRoutes,
      'POST /api/admin/players': () => jsonResponse(201, registered),
    })
    renderApp('/admin/players')

    await user.click(await screen.findByRole('button', { name: '새 선수 등록' }))
    await user.type(screen.getByLabelText('실명'), '김철수')
    await user.type(screen.getByLabelText('게임 닉네임 1'), '철수')
    await user.click(screen.getByRole('button', { name: '등록' }))

    const post = await waitForCall(() => calls.find((c) => c.method === 'POST'))
    expect(JSON.parse(post.body ?? '')).toEqual({ realName: '김철수', mainPosition: null, nicknames: ['철수'] })
  })

  it('409 ALIAS_CONFLICT이면 충돌한 닉네임과 연결된 선수를 보여 준다', async () => {
    const user = userEvent.setup()
    mockFetch({
      ...baseRoutes,
      'POST /api/admin/players/1/aliases': () =>
        problemResponse(409, 'ALIAS_CONFLICT', {
          conflicts: [{ nickname: '낯선닉', playerId: 2, playerDisplayName: '낯선닉' }],
        }),
    })
    renderApp('/admin/players')

    await user.type(await screen.findByLabelText('홍길동 닉네임 추가'), '낯선닉')
    await user.click(screen.getAllByRole('button', { name: '추가' })[0])

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('이미 다른 선수에게 연결된 닉네임입니다')
    expect(alert).toHaveTextContent("'낯선닉' - 이미 낯선닉 선수에게 연결되어 있습니다.")
  })

  it('409 LAST_ALIAS이면 마지막 닉네임은 삭제할 수 없다는 메시지를 보여 준다', async () => {
    const user = userEvent.setup()
    mockFetch({
      ...baseRoutes,
      'DELETE /api/admin/players/1/aliases/10': () => problemResponse(409, 'LAST_ALIAS'),
    })
    renderApp('/admin/players')

    await user.click(await screen.findByRole('button', { name: '길동이 삭제' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('마지막 닉네임은 삭제할 수 없습니다')
  })

  it('미등록 선수에는 미등록 표시가 있고, 실명을 입력해 PUT으로 등록 전환한다', async () => {
    const user = userEvent.setup()
    const { calls } = mockFetch({
      ...baseRoutes,
      'PUT /api/admin/players/2': () => jsonResponse(200, { ...unregistered, realName: '이영희', registered: true }),
    })
    renderApp('/admin/players')

    const card = (await screen.findByRole('heading', { name: '낯선닉' })).closest('article') as HTMLElement
    expect(within(card).getByText('미등록')).toBeInTheDocument()

    await user.click(within(card).getByRole('button', { name: '실명 입력해 등록' }))
    await user.type(within(card).getByLabelText('실명'), '이영희')
    await user.selectOptions(within(card).getByLabelText('주 포지션'), 'TOP')
    await user.click(within(card).getByRole('button', { name: '등록' }))

    const put = await waitForCall(() => calls.find((c) => c.method === 'PUT'))
    expect(JSON.parse(put.body ?? '')).toEqual({ realName: '이영희', mainPosition: 'TOP' })
  })

  it('미등록 선수의 실명을 비운 채 포지션만 바꾸면 realName null로 보내 미등록을 유지한다', async () => {
    const user = userEvent.setup()
    const { calls } = mockFetch({
      ...baseRoutes,
      'PUT /api/admin/players/2': () => jsonResponse(200, { ...unregistered, mainPosition: 'JUNGLE' }),
    })
    renderApp('/admin/players')

    const card = (await screen.findByRole('heading', { name: '낯선닉' })).closest('article') as HTMLElement
    await user.click(within(card).getByRole('button', { name: '실명 입력해 등록' }))
    await user.selectOptions(within(card).getByLabelText('주 포지션'), 'JUNGLE')
    await user.click(within(card).getByRole('button', { name: '등록' }))

    const put = await waitForCall(() => calls.find((c) => c.method === 'PUT'))
    expect(JSON.parse(put.body ?? '')).toEqual({ realName: null, mainPosition: 'JUNGLE' })
  })

  it('등록 선수의 실명을 비우면 서버를 호출하지 않고 막는다', async () => {
    const user = userEvent.setup()
    const { calls } = mockFetch(baseRoutes)
    renderApp('/admin/players')

    const card = (await screen.findByRole('heading', { name: '홍길동' })).closest('article') as HTMLElement
    await user.click(within(card).getByRole('button', { name: '수정' }))
    await user.clear(within(card).getByLabelText('실명'))
    await user.click(within(card).getByRole('button', { name: '저장' }))

    expect(within(card).getByText('실명을 입력해 주세요.')).toBeInTheDocument()
    expect(calls.some((c) => c.method === 'PUT')).toBe(false)
  })
})

// 호출이 기록될 때까지 잠깐 기다린다(waitFor 래퍼)
async function waitForCall<T>(find: () => T | undefined): Promise<T> {
  return waitFor(() => {
    const value = find()
    if (!value) throw new Error('아직 호출되지 않음')
    return value
  })
}
