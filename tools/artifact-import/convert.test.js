'use strict'
const test = require('node:test')
const assert = require('node:assert')
const { convertLanes, convertPlayer, convertScore, buildRequest, planImport } = require('./convert')

test('라인 키·상중하 대응, 잘못된 칸은 invalid로 센다', () => {
  const r = convertLanes({ top: { t: 1, s: '상' }, jg: { t: 7, s: '중' }, sup: { t: 6, s: '하' }, mid: null })
  assert.deepStrictEqual(r.lanes, { TOP: { tier: 1, sub: 'HIGH' }, SUPPORT: { tier: 6, sub: 'LOW' } })
  assert.strictEqual(r.invalid, 1)
})

test('닉네임은 서버와 같은 규칙(NFKC·공백 축약·소문자)으로 중복을 거른다', () => {
  const c = convertPlayer({ name: ' 가상A ', lanes: {}, nicks: ['Foo  Bar', 'foo bar', 'FooBar', 'Foo', 'Ｆｏｏ', ' ', 'x'] })
  assert.strictEqual(c.name, '가상A')
  assert.deepStrictEqual(c.nicknames, ['Foo Bar', 'FooBar', 'Foo', 'x'])
})

test('점수표 변환과 누락 검출', () => {
  const s = { base: { 1: 60, 2: 52, 3: 44, 4: 36, 5: 28, 6: 20 }, sub: { 상: 3, 중: 0, 하: -3 }, lane: { adc: -4, sup: -3 } }
  const b = convertScore(s)
  assert.strictEqual(b.base['1'], 60)
  assert.deepStrictEqual(b.sub, { HIGH: 3, MID: 0, LOW: -3 })
  assert.deepStrictEqual(b.lane, { TOP: 0, JUNGLE: 0, MID: 0, ADC: -4, SUPPORT: -3 })
  delete s.base[3]
  assert.strictEqual(convertScore(s), null)
})

test('기존 선수: 별칭 유지 + 새 닉네임 추가, 기존 라인 보존 후 덮어쓰기', () => {
  const existing = { id: 5, realName: '가상A', mainPosition: 'TOP', aliases: [{ id: 1, nickname: 'Old' }], lanes: { TOP: { tier: 1, sub: 'LOW' }, MID: { tier: 4, sub: 'MID' } } }
  const c = convertPlayer({ name: '가상A', lanes: { top: { t: 2, s: '상' } }, nicks: ['old', 'New'] })
  const { body, dropped } = buildRequest(c, existing)
  assert.deepStrictEqual(body.nicknames, ['Old', 'New'])
  assert.deepStrictEqual(body.lanes, { TOP: { tier: 2, sub: 'HIGH' }, MID: { tier: 4, sub: 'MID' } })
  assert.strictEqual(body.mainPosition, 'TOP')
  assert.strictEqual(dropped, 0)
})

test('새 선수 닉네임은 10개까지, 넘으면 dropped', () => {
  const c = convertPlayer({ name: 'N', lanes: { top: { t: 1, s: '중' } }, nicks: Array.from({ length: 12 }, (_, i) => 'n' + i) })
  const { body, dropped } = buildRequest(c, null)
  assert.strictEqual(body.nicknames.length, 10)
  assert.strictEqual(dropped, 2)
})

test('계획: 이름 일치 갱신, 없으면 생성, 라인 0개 신규는 건너뜀, 기존은 라인 0개여도 갱신', () => {
  const existing = [{ id: 9, realName: '가상B', mainPosition: null, aliases: [{ id: 2, nickname: 'b1' }], lanes: {} }]
  const plan = planImport(
    [
      { name: '가상A', lanes: { top: { t: 1, s: '상' } }, nicks: ['a'] },
      { name: '가상B', lanes: {}, nicks: ['b2'] },
      { name: '가상C', lanes: {}, nicks: ['c'] },
      { name: '가상D', lanes: { mid: { t: 2, s: '중' } }, nicks: [] },
    ],
    existing,
  )
  assert.deepStrictEqual(plan.map((p) => p.action + (p.reason ? ':' + p.reason : '')), ['create', 'update', 'skip:NO_LANE', 'skip:NO_NICKNAME'])
  assert.strictEqual(plan[1].id, 9)
})

test('요주 인물: bee가 true일 때만 flagged, 신규와 갱신 모두 아티팩트 값으로 덮는다', () => {
  assert.strictEqual(convertPlayer({ name: 'A', nicks: ['a'] }).flagged, false)
  assert.strictEqual(convertPlayer({ name: 'A', nicks: ['a'], bee: 'yes' }).flagged, false)
  const flagged = convertPlayer({ name: 'A', lanes: { top: { t: 1, s: '상' } }, nicks: ['a'], bee: true })
  assert.strictEqual(buildRequest(flagged, null).body.flagged, true)
  const existing = { id: 1, realName: 'A', mainPosition: null, aliases: [{ id: 1, nickname: 'a' }], lanes: {}, flagged: true }
  assert.strictEqual(buildRequest(convertPlayer({ name: 'A', nicks: ['a'] }), existing).body.flagged, false)
  assert.strictEqual(buildRequest(flagged, { ...existing, flagged: false }).body.flagged, true)
})
