import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { jsonResponse, mockFetch, problemResponse } from '../../test/mockFetch'
import { OriginalImageButton } from './OriginalImageButton'

function renderButton() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <OriginalImageButton setId={5} />
    </QueryClientProvider>,
  )
}

const adminMe = { 'GET /api/admin/me': () => jsonResponse(200, { id: 1, username: 'admin' }) }
const guest = { 'GET /api/admin/me': () => jsonResponse(401, { status: 401, code: 'UNAUTHENTICATED' }) }

afterEach(() => vi.unstubAllGlobals())

describe('OriginalImageButton', () => {
  it('방문자에게는 버튼이 없다', async () => {
    const { calls } = mockFetch(guest)
    renderButton()
    await waitFor(() => expect(calls.length).toBeGreaterThan(0))
    await waitFor(() => expect(screen.queryByRole('button', { name: '원본 보기' })).toBeNull())
  })

  it('담당자에게는 버튼이 보이고 누르면 이미지를 받아 보여 준다', async () => {
    vi.stubGlobal('URL', Object.assign(URL, { createObjectURL: () => 'blob:test', revokeObjectURL: () => {} }))
    mockFetch({
      ...adminMe,
      'GET /api/admin/sets/5/image': () =>
        new Response('x', { status: 200, headers: { 'Content-Type': 'image/png' } }),
    })
    renderButton()
    await userEvent.click(await screen.findByRole('button', { name: '원본 보기' }))
    expect(await screen.findByRole('img', { name: '원본 결과창 이미지' })).toHaveAttribute('src', 'blob:test')
  })

  it('이미지가 없으면(404) 안내를 보여 준다', async () => {
    mockFetch({ ...adminMe, 'GET /api/admin/sets/5/image': () => problemResponse(404, 'SET_IMAGE_NOT_FOUND') })
    renderButton()
    await userEvent.click(await screen.findByRole('button', { name: '원본 보기' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('원본 결과창 이미지가 없어요.')
  })
})
