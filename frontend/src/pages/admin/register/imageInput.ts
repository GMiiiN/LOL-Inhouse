// 결과창 이미지 입력 처리의 순수 함수들(붙여넣기에서 파일 꺼내기, 형식·크기 사전 검사).
// jsdom 29에는 ClipboardEvent·DataTransfer가 없어서, 이벤트 대신 이 함수에 가짜 객체를 넣어 시험한다.
// 설계: docs/design/m4-design.md §6 UploadStep

export const MAX_IMAGE_BYTES = 10 * 1024 * 1024 // 서버 상한(10MB)과 같다
export const ACCEPTED_IMAGE_TYPES = ['image/png', 'image/jpeg'] as const

// DataTransferItem 중 쓰는 부분만
export type ClipboardItemLike = { kind: string; type: string; getAsFile(): File | null }
export type ClipboardDataLike = { items?: ArrayLike<ClipboardItemLike> | null }
export type PasteEventLike = { clipboardData?: ClipboardDataLike | null; preventDefault(): void }

// 클립보드 항목 중 첫 번째 image/* 파일을 꺼낸다. 이미지가 없으면 null(텍스트 붙여넣기는 건드리지 않는다).
export function pickImageFromClipboard(data: ClipboardDataLike | null | undefined): File | null {
  const items = data?.items
  if (!items) return null
  for (let i = 0; i < items.length; i += 1) {
    const item = items[i]
    if (item.kind === 'file' && item.type.startsWith('image/')) {
      const file = item.getAsFile()
      if (file) return file
    }
  }
  return null
}

// 형식·크기 사전 검사. 통과하면 null, 아니면 화면에 보여 줄 문구. 서버도 같은 검사를 하므로 편의일 뿐이다.
export function validateImage(file: { type: string; size: number }): string | null {
  if (!(ACCEPTED_IMAGE_TYPES as readonly string[]).includes(file.type)) {
    return 'PNG 또는 JPEG 이미지만 올릴 수 있습니다.'
  }
  if (file.size === 0) return '비어 있는 이미지입니다.'
  if (file.size > MAX_IMAGE_BYTES) return '이미지가 너무 큽니다(최대 10MB).'
  return null
}

// paste 이벤트 처리: 이미지가 있으면 기본 동작을 막고 onImage로 넘기고 true를 돌려준다.
export function handleImagePaste(event: PasteEventLike, onImage: (file: File) => void): boolean {
  const file = pickImageFromClipboard(event.clipboardData)
  if (!file) return false
  event.preventDefault()
  onImage(file)
  return true
}

export function formatBytes(bytes: number): string {
  if (bytes >= 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(1)}MB`
  return `${Math.ceil(bytes / 1024)}KB`
}
