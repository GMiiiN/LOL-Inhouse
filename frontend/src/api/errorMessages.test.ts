// 새 오류 코드마다 한국어 문구가 있는지(코드가 기본 문구로 새는 것을 막는다)
import { describe, expect, it } from 'vitest'
import { ApiError } from './client'
import { describeError } from './errorMessages'
import { TIER_TEAM_ERROR_CODES } from './tierTypes'

describe('M4.5 오류 문구', () => {
  it.each(TIER_TEAM_ERROR_CODES)('%s에 전용 문구가 있다', (code) => {
    const fallback = 'FALLBACK_DETAIL'
    const view = describeError(new ApiError(400, { code, detail: fallback }))

    expect(view.message).not.toBe(fallback)
    expect(view.message.length).toBeGreaterThan(0)
  })

  it('라인당 3명 이상 고정이면 어느 라인에 몇 명인지 알린다', () => {
    const view = describeError(new ApiError(400, { code: 'TEAM_LANE_FIX_INVALID', position: 'MID', count: 3 }))

    expect(view.message).toBe('미드에 3명이 고정돼 있어요. 한 라인은 양 팀 합쳐 2명까지만 고정할 수 있어요.')
  })
})
