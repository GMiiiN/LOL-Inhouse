// 업로드 단계 테스트: paste 처리 함수(jsdom에는 ClipboardEvent가 없어 가짜 객체 사용), 형식·크기 거절, 화면 동작
import { fireEvent, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import {
  handleImagePaste,
  MAX_IMAGE_BYTES,
  pickImageFromClipboard,
  validateImage,
  type ClipboardItemLike,
} from './imageInput'
import { UploadStep } from './UploadStep'

const png = () => new File([new Uint8Array(10)], 'image.png', { type: 'image/png' })
const item = (kind: string, type: string, file: File | null): ClipboardItemLike => ({ kind, type, getAsFile: () => file })

describe('pickImageFromClipboard / handleImagePaste', () => {
  it('항목 중 첫 image/* 파일을 꺼낸다', () => {
    const file = png()

    expect(pickImageFromClipboard({ items: [item('string', 'text/plain', null), item('file', 'image/png', file)] })).toBe(file)
  })

  it('이미지가 없거나 clipboardData가 없으면 null', () => {
    expect(pickImageFromClipboard({ items: [item('string', 'text/plain', null)] })).toBeNull()
    expect(pickImageFromClipboard(null)).toBeNull()
    expect(pickImageFromClipboard({ items: null })).toBeNull()
  })

  it('이미지가 있으면 기본 동작을 막고 콜백에 넘긴다. 없으면 건드리지 않는다', () => {
    const preventDefault = vi.fn()
    const onImage = vi.fn()
    const file = png()

    expect(handleImagePaste({ clipboardData: { items: [item('file', 'image/png', file)] }, preventDefault }, onImage)).toBe(true)
    expect(onImage).toHaveBeenCalledWith(file)
    expect(preventDefault).toHaveBeenCalledOnce()

    const other = vi.fn()
    expect(handleImagePaste({ clipboardData: { items: [item('string', 'text/plain', null)] }, preventDefault: other }, onImage)).toBe(false)
    expect(other).not.toHaveBeenCalled()
  })
})

describe('validateImage', () => {
  it('PNG·JPEG, 10MB 이하만 통과', () => {
    expect(validateImage({ type: 'image/png', size: 1000 })).toBeNull()
    expect(validateImage({ type: 'image/jpeg', size: MAX_IMAGE_BYTES })).toBeNull()
  })

  it('다른 형식·빈 파일·10MB 초과는 문구를 돌려준다', () => {
    expect(validateImage({ type: 'image/gif', size: 1000 })).toMatch(/PNG 또는 JPEG/)
    expect(validateImage({ type: 'image/png', size: 0 })).toMatch(/비어/)
    expect(validateImage({ type: 'image/png', size: MAX_IMAGE_BYTES + 1 })).toMatch(/10MB/)
  })
})

function pasteOnDocument(file: File) {
  const event = new Event('paste', { bubbles: true, cancelable: true })
  Object.defineProperty(event, 'clipboardData', { value: { items: [item('file', file.type, file)] } })
  document.dispatchEvent(event)
  return event
}

function renderStep(onStart = vi.fn(), defaultTopSide: 'BLUE' | 'RED' | null = 'BLUE') {
  render(
    <MemoryRouter>
      <UploadStep defaultTopSide={defaultTopSide} pending={false} error={null} onStart={onStart} />
    </MemoryRouter>,
  )
  return onStart
}

describe('UploadStep', () => {
  it('라디오는 실제 값(BLUE/RED, TOP…)과 라벨 이름을 갖고, 고른 항목만 checked다', async () => {
    const user = userEvent.setup()
    renderStep()

    expect(screen.getByRole('radio', { name: '블루' })).toHaveAttribute('value', 'BLUE')
    expect(screen.getByRole('radio', { name: '레드' })).toHaveAttribute('value', 'RED')
    expect(screen.getByRole('radio', { name: '탑' })).toHaveAttribute('value', 'TOP')
    await user.click(screen.getByRole('radio', { name: '서포터' }))
    expect(screen.getByRole('radio', { name: '서포터' })).toBeChecked()
    expect(screen.getByRole('radio', { name: '탑' })).not.toBeChecked()
  })

  it('진영·포지션이 모두 골라져 있으면 붙여넣는 즉시 시작한다', async () => {
    const user = userEvent.setup()
    const onStart = renderStep()
    await user.click(screen.getByRole('radio', { name: '원딜' }))
    const file = png()

    const event = pasteOnDocument(file)

    expect(event.defaultPrevented).toBe(true)
    expect(onStart).toHaveBeenCalledWith({ image: file, topSide: 'BLUE', capturerPosition: 'ADC' })
  })

  it('선택이 덜 됐으면 이미지를 들고 있다가 버튼을 켠다', async () => {
    const user = userEvent.setup()
    const onStart = renderStep(vi.fn(), null)
    const file = png()

    pasteOnDocument(file)
    expect(onStart).not.toHaveBeenCalled()
    expect(await screen.findByText(/image\.png/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '추출 시작' })).toBeDisabled()

    await user.click(screen.getByRole('radio', { name: '레드' }))
    await user.click(screen.getByRole('radio', { name: '미드' }))
    await user.click(screen.getByRole('button', { name: '추출 시작' }))

    expect(onStart).toHaveBeenCalledWith({ image: file, topSide: 'RED', capturerPosition: 'MID' })
  })

  it('형식이 맞지 않는 이미지는 거절 문구를 보여 주고 시작하지 않는다', async () => {
    const user = userEvent.setup()
    const onStart = renderStep()
    await user.click(screen.getByRole('radio', { name: '원딜' }))

    pasteOnDocument(new File([new Uint8Array(4)], 'x.gif', { type: 'image/gif' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('PNG 또는 JPEG')
    expect(onStart).not.toHaveBeenCalled()
  })

  it('10MB를 넘는 파일을 파일 선택으로 올려도 거절한다', async () => {
    const onStart = renderStep()
    const big = new File([new Uint8Array(1)], 'big.png', { type: 'image/png' })
    Object.defineProperty(big, 'size', { value: MAX_IMAGE_BYTES + 1 })

    fireEvent.change(document.querySelector('input[type=file]')!, { target: { files: [big] } })

    expect(await screen.findByRole('alert')).toHaveTextContent('10MB')
    expect(onStart).not.toHaveBeenCalled()
  })
})
