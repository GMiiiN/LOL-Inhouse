// 세트 등록 1단계(SETUP): 위쪽 팀 진영·캡처한 사람 포지션을 고르고 결과창 이미지를 붙여 넣는다(Ctrl+V).
// 선택이 다 돼 있으면 붙여넣는 즉시 추출을 시작하고, 아니면 이미지를 들고 있다가 "추출 시작" 버튼을 켠다.
// 설계: docs/design/m4-design.md §6
import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { ApiError } from '../../../api/client'
import type { Side } from '../../../api/registrationTypes'
import { POSITION_LABEL, POSITIONS, type Position } from '../../../api/types'
import { ErrorNotice } from '../../../components/ErrorNotice'
import { formatBytes, handleImagePaste, validateImage, type PasteEventLike } from './imageInput'
import { useObjectUrl } from './useObjectUrl'

export type UploadSelection = { image: File; topSide: Side; capturerPosition: Position }

type UploadStepProps = {
  defaultTopSide: Side | null
  pending: boolean
  error: unknown
  onStart: (selection: UploadSelection) => void
}

const SIDE_LABEL: Record<Side, string> = { BLUE: '블루', RED: '레드' }

export function UploadStep({ defaultTopSide, pending, error, onStart }: UploadStepProps) {
  const [topSide, setTopSide] = useState<Side | null>(defaultTopSide)
  const [position, setPosition] = useState<Position | null>(null)
  const [image, setImage] = useState<File | null>(null)
  const [imageError, setImageError] = useState<string | null>(null)
  const previewUrl = useObjectUrl(image)

  function acceptImage(file: File) {
    const problem = validateImage(file)
    setImageError(problem)
    if (problem) return
    setImage(file)
    if (topSide && position && !pending) onStart({ image: file, topSide, capturerPosition: position })
  }

  // document 전체에서 paste를 받는다(입력칸에 포커스가 없어도 Ctrl+V가 동작해야 한다).
  useEffect(() => {
    const listener = (event: Event) => handleImagePaste(event as Event & PasteEventLike, acceptImage)
    document.addEventListener('paste', listener)
    return () => document.removeEventListener('paste', listener)
  })

  const ready = image !== null && topSide !== null && position !== null && !pending
  const duplicate = error instanceof ApiError && error.code === 'DUPLICATE_IMAGE' ? error : null

  return (
    <div className="panel upload-step">
      <h2>결과창 올리기</h2>

      <fieldset className="choice-group">
        <legend>위쪽 팀 진영</legend>
        {(['BLUE', 'RED'] as const).map((side) => (
          <label key={side}>
            <input type="radio" name="top-side" value={side} checked={topSide === side} onChange={() => setTopSide(side)} />
            {SIDE_LABEL[side]}
          </label>
        ))}
      </fieldset>
      {defaultTopSide === null && <p className="muted-text">첫 세트라 기본값이 없습니다. 직접 골라 주세요.</p>}

      <fieldset className="choice-group">
        <legend>캡처한 사람의 포지션</legend>
        {POSITIONS.map((candidate) => (
          <label key={candidate}>
            <input
              type="radio"
              name="capturer-position"
              value={candidate}
              checked={position === candidate}
              onChange={() => setPosition(candidate)}
            />
            {POSITION_LABEL[candidate]}
          </label>
        ))}
      </fieldset>

      <div className="drop-zone">
        <p>
          <strong>Win+Shift+S</strong>로 결과창을 캡처한 뒤 이 화면에서 <strong>Ctrl+V</strong>로 붙여 넣으세요.
        </p>
        <label className="header-button file-button">
          파일 선택
          <input
            type="file"
            accept="image/png,image/jpeg"
            onChange={(event) => {
              const file = event.target.files?.[0]
              if (file) acceptImage(file)
              event.target.value = ''
            }}
          />
        </label>
      </div>

      {imageError && (
        <p className="field-error" role="alert">
          {imageError}
        </p>
      )}

      {image && (
        <div className="upload-preview">
          {previewUrl && <img src={previewUrl} alt="붙여 넣은 결과창 미리보기" />}
          <p className="muted-text">
            {image.name} · {formatBytes(image.size)}
          </p>
        </div>
      )}

      <button type="button" className="primary-button" disabled={!ready} onClick={() => ready && onStart({ image, topSide, capturerPosition: position })}>
        {pending ? '업로드 중...' : '추출 시작'}
      </button>

      <ErrorNotice error={error} />
      {duplicate?.existingSetId !== undefined && (
        <p>
          <Link to={`/sets/${duplicate.existingSetId}`}>이미 등록된 세트 보기</Link>
        </p>
      )}
    </div>
  )
}
