// 선수 등록(create)·수정(edit) 공용 폼. create에서만 게임 닉네임 목록을 받는다(수정 때 별칭은 카드에서 따로 관리).
import { useState, type SyntheticEvent } from 'react'
import { ErrorNotice } from '../../components/ErrorNotice'
import { POSITIONS, POSITION_LABEL, type Position } from '../../api/types'
import {
  NICKNAMES_MAX_COUNT,
  hasErrors,
  validatePlayerForm,
  type PlayerFormErrors,
  type PlayerFormMode,
  type PlayerFormValues,
} from './playerFormValidation'

// 검증을 통과한 제출 값. realName null = 미등록 선수의 실명 비움, mainPosition null = 모름
export type PlayerFormSubmit = { realName: string | null; mainPosition: Position | null; nicknames: string[] }

type PlayerFormProps = {
  mode: PlayerFormMode
  initial?: Partial<PlayerFormValues>
  submitLabel: string
  pending: boolean
  serverError: unknown // 서버가 돌려준 오류(409 충돌 등)
  onSubmit: (values: PlayerFormSubmit) => void // 수정 모드에서는 nicknames가 빈 배열
  onCancel?: () => void
}

export function PlayerForm({ mode, initial, submitLabel, pending, serverError, onSubmit, onCancel }: PlayerFormProps) {
  const [realName, setRealName] = useState(initial?.realName ?? '')
  const [mainPosition, setMainPosition] = useState<Position | ''>(initial?.mainPosition ?? '')
  const [nicknames, setNicknames] = useState<string[]>(initial?.nicknames ?? [''])
  const [errors, setErrors] = useState<PlayerFormErrors>({})

  function handleSubmit(event: SyntheticEvent<HTMLFormElement>) {
    event.preventDefault()
    const values: PlayerFormValues = { realName, mainPosition, nicknames }
    const found = validatePlayerForm(values, mode)
    setErrors(found)
    if (hasErrors(found)) return // 서버 호출 없이 폼에서 막는다
    onSubmit({
      realName: realName.trim() || null, // 비어 있어도 통과하는 경우는 미등록 선수 수정뿐
      mainPosition: mainPosition || null,
      nicknames: mode === 'create' ? nicknames.map((n) => n.trim()) : [],
    })
  }

  function changeNickname(index: number, value: string) {
    setNicknames(nicknames.map((n, i) => (i === index ? value : n)))
  }

  return (
    <form className="stack-form" onSubmit={handleSubmit} noValidate>
      <label>
        실명
        <input
          type="text"
          value={realName}
          onChange={(e) => setRealName(e.target.value)}
        />
      </label>
      {errors.realName && <p className="field-error">{errors.realName}</p>}

      <label>
        주 포지션
        <select value={mainPosition} onChange={(e) => setMainPosition(e.target.value as Position | '')}>
          <option value="">모름</option>
          {POSITIONS.map((p) => (
            <option key={p} value={p}>
              {POSITION_LABEL[p]}
            </option>
          ))}
        </select>
      </label>

      {mode === 'create' && (
        <fieldset className="nickname-fieldset">
          <legend>게임 닉네임 (1~{NICKNAMES_MAX_COUNT}개)</legend>
          {nicknames.map((nickname, index) => (
            <div className="nickname-row" key={index}>
              <input
                type="text"
                aria-label={`게임 닉네임 ${index + 1}`}
                value={nickname}
                onChange={(e) => changeNickname(index, e.target.value)}
              />
              {nicknames.length > 1 && (
                <button
                  type="button"
                  className="header-button"
                  aria-label={`게임 닉네임 ${index + 1} 지우기`}
                  onClick={() => setNicknames(nicknames.filter((_, i) => i !== index))}
                >
                  지우기
                </button>
              )}
            </div>
          ))}
          {nicknames.length < NICKNAMES_MAX_COUNT && (
            <button type="button" className="header-button" onClick={() => setNicknames([...nicknames, ''])}>
              닉네임 칸 추가
            </button>
          )}
          {errors.nicknames && <p className="field-error">{errors.nicknames}</p>}
        </fieldset>
      )}

      <ErrorNotice error={serverError} />
      <div className="button-row">
        <button type="submit" className="primary-button" disabled={pending}>
          {submitLabel}
        </button>
        {onCancel && (
          <button type="button" className="header-button" onClick={onCancel}>
            취소
          </button>
        )}
      </div>
    </form>
  )
}
