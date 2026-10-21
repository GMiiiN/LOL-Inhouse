// 선수 편집기: 이름·라인 5개의 티어/우열·쉼표 닉네임·주력 포지션(선택). 삭제는 두 번 눌러 확인한다.
// 저장은 한 번의 요청(PlayerProfileRequest)으로 끝난다. profile이 null이면 새 사람 추가.
import { useState, type SyntheticEvent } from 'react'
import { ErrorNotice } from '../../../components/ErrorNotice'
import beeUrl from '../../../assets/bee.webp'
import '../../../styles/tier.css'
import { TIERS, TIER_SUB_LABEL, TIER_SUBS, type PlayerProfile, type PlayerProfileRequest, type Tier, type TierSub } from '../../../api/tierTypes'
import { POSITIONS, POSITION_LABEL, type Position } from '../../../api/types'
import {
  buildRequest,
  editorMode,
  hasEditorErrors,
  initialForm,
  validateEditor,
  type EditorErrors,
  type EditorForm,
} from './playerEditorModel'

type PlayerEditorProps = {
  profile: PlayerProfile | null
  otherRealNames: string[]
  pending: boolean
  serverError: unknown
  onSubmit: (request: PlayerProfileRequest) => void
  onCancel: () => void
  onDelete?: () => void // 기존 선수만. 없으면 삭제 버튼을 그리지 않는다
  deleting?: boolean
}

export function PlayerEditor({
  profile,
  otherRealNames,
  pending,
  serverError,
  onSubmit,
  onCancel,
  onDelete,
  deleting = false,
}: PlayerEditorProps) {
  const mode = editorMode(profile)
  const [form, setForm] = useState<EditorForm>(() => initialForm(profile))
  const [errors, setErrors] = useState<EditorErrors>({})
  const [confirmDelete, setConfirmDelete] = useState(false)

  const title =
    mode === 'create'
      ? '사람 추가'
      : mode === 'edit-registered'
        ? `${profile?.displayName} 수정`
        : `미등록 선수 ${profile?.displayName} 등록`

  function setLane(position: Position, patch: Partial<EditorForm['lanes'][Position]>) {
    setForm({ ...form, lanes: { ...form.lanes, [position]: { ...form.lanes[position], ...patch } } })
  }

  function handleSubmit(event: SyntheticEvent<HTMLFormElement>) {
    event.preventDefault()
    const found = validateEditor(form, mode, { otherRealNames, existingAliasCount: profile?.aliases.length ?? 0 })
    setErrors(found)
    if (hasEditorErrors(found)) return // 서버 호출 없이 막는다
    onSubmit(buildRequest(form))
  }

  return (
    <section className="panel tier-editor" aria-label="선수 편집기">
      <h2>{title}</h2>
      <form className="tier-form" onSubmit={handleSubmit} noValidate>
        <div className="tier-field">
          <label htmlFor="editor-name">이름 (실명)</label>
          <input
            id="editor-name"
            type="text"
            value={form.realName}
            placeholder={mode === 'edit-unregistered' ? '실명을 넣으면 등록 선수가 돼요' : '예: 김철수'}
            onChange={(e) => setForm({ ...form, realName: e.target.value })}
          />
          {errors.realName && <p className="field-error">{errors.realName}</p>}
        </div>

        <div className="tier-edgrid">
          {POSITIONS.map((position) => {
            const lane = form.lanes[position]
            const label = POSITION_LABEL[position]
            return (
              <div className="tier-edlane" key={position}>
                <span className="tier-edlane-name">{label}</span>
                <div className="tier-edlane-row">
                  <select
                    aria-label={`${label} 티어`}
                    value={lane.tier}
                    onChange={(e) => setLane(position, { tier: e.target.value === '' ? '' : (Number(e.target.value) as Tier) })}
                  >
                    <option value="">안 함</option>
                    {TIERS.map((tier) => (
                      <option key={tier} value={tier}>
                        {tier}티어
                      </option>
                    ))}
                  </select>
                  <select
                    aria-label={`${label} 티어 안 우열`}
                    value={lane.sub}
                    disabled={lane.tier === ''}
                    onChange={(e) => setLane(position, { sub: e.target.value as TierSub })}
                  >
                    {TIER_SUBS.map((sub) => (
                      <option key={sub} value={sub}>
                        {TIER_SUB_LABEL[sub]}
                      </option>
                    ))}
                  </select>
                </div>
              </div>
            )
          })}
        </div>
        <p className="tier-help">갈 수 있는 라인만 티어를 고르세요. &apos;안 함&apos;인 라인에는 자동 배정되지 않아요.</p>
        {errors.lanes && <p className="field-error">{errors.lanes}</p>}

        <div className="tier-field">
          <label htmlFor="editor-nicknames">게임 닉네임 (쉼표로 구분)</label>
          <input
            id="editor-nicknames"
            type="text"
            value={form.nicknames}
            placeholder="예: 철수, 철수부캐"
            onChange={(e) => setForm({ ...form, nicknames: e.target.value })}
          />
          <p className="tier-help">
            결과창 닉네임과 연결돼요. 대소문자와 비슷한 모양의 글자는 같은 닉네임으로 봐요. 최소 1개, 새로 만들 때는 최대 10개예요.
          </p>
          {errors.nicknames && <p className="field-error">{errors.nicknames}</p>}
        </div>

        <div className="tier-field">
          <label htmlFor="editor-main-position">주력 포지션 (선택)</label>
          <select
            id="editor-main-position"
            value={form.mainPosition}
            onChange={(e) => setForm({ ...form, mainPosition: e.target.value as Position | '' })}
          >
            <option value="">모름</option>
            {POSITIONS.map((position) => (
              <option key={position} value={position}>
                {POSITION_LABEL[position]}
              </option>
            ))}
          </select>
        </div>

        <label className="tier-beechk" htmlFor="editor-flagged">
          <input
            id="editor-flagged"
            type="checkbox"
            checked={form.flagged}
            onChange={(e) => setForm({ ...form, flagged: e.target.checked })}
          />
          <img className="bee" src={beeUrl} alt="" />
          <span>요주 인물로 표시</span>
        </label>

        <ErrorNotice error={serverError} />

        <div className="button-row">
          <button type="submit" className="primary-button" disabled={pending}>
            저장
          </button>
          <button type="button" className="header-button" onClick={onCancel}>
            취소
          </button>
          {onDelete &&
            (confirmDelete ? (
              <button type="button" className="header-button tier-danger" disabled={deleting} onClick={onDelete}>
                정말 삭제
              </button>
            ) : (
              <button type="button" className="header-button tier-danger" onClick={() => setConfirmDelete(true)}>
                삭제
              </button>
            ))}
        </div>
      </form>
    </section>
  )
}
