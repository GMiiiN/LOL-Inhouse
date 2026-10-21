// 아티팩트 데이터 -> 우리 API 요청으로 바꾸는 순수 함수. 서버·파일 접근 없음.
'use strict'

const LANE_MAP = { top: 'TOP', jg: 'JUNGLE', mid: 'MID', adc: 'ADC', sup: 'SUPPORT' }
const SUB_MAP = { 상: 'HIGH', 중: 'MID', 하: 'LOW' }
const MAX_NICKNAMES = 10

// 서버 NicknameNormalizer와 같은 규칙: NFKC, 내부 공백 하나로, 양끝 공백 제거. 비교 키는 여기에 소문자.
const normalizeNick = (s) =>
  String(s).normalize('NFKC').replace(/[\s\p{Z}]+/gu, ' ').replace(/^[\s\p{Z}]+|[\s\p{Z}]+$/gu, '')
const normNick = (s) => normalizeNick(s).toLowerCase()

// {top:{t,s},...} -> {TOP:{tier,sub},...}. 형식이 틀린 칸은 버리고 invalid 개수로 센다.
function convertLanes(lanes) {
  const out = {}
  let invalid = 0
  for (const [key, lane] of Object.entries(LANE_MAP)) {
    const v = lanes && lanes[key]
    if (!v) continue
    const tier = Number(v.t)
    const sub = SUB_MAP[v.s]
    if (Number.isInteger(tier) && tier >= 1 && tier <= 6 && sub) out[lane] = { tier, sub }
    else invalid++
  }
  return { lanes: out, invalid }
}

// 아티팩트 선수 한 명 -> {name, lanes, nicknames, flagged, invalidLanes}. bee: true만 요주 인물(flagged)
function convertPlayer(p) {
  const { lanes, invalid } = convertLanes(p.lanes)
  const seen = new Set()
  const nicknames = []
  for (const n of p.nicks || []) {
    const t = normalizeNick(n)
    if (t && !seen.has(normNick(t))) {
      seen.add(normNick(t))
      nicknames.push(t)
    }
  }
  return { name: String(p.name || '').trim(), lanes, nicknames, flagged: p.bee === true, invalidLanes: invalid }
}

// 점수표 -> PUT /api/admin/score-table 본문. 14칸 중 하나라도 없으면 null.
function convertScore(score) {
  if (!score || !score.base || !score.sub) return null
  const lane = score.lane || {}
  const body = { base: {}, sub: {}, lane: {} }
  for (let t = 1; t <= 6; t++) body.base[String(t)] = Number(score.base[t])
  for (const [ko, en] of Object.entries(SUB_MAP)) body.sub[en] = Number(score.sub[ko])
  for (const [key, laneName] of Object.entries(LANE_MAP)) body.lane[laneName] = Number(lane[key] ?? 0)
  const all = [...Object.values(body.base), ...Object.values(body.sub), ...Object.values(body.lane)]
  return all.length === 14 && all.every(Number.isInteger) ? body : null
}

// 기존 선수(없으면 null)와 합쳐 요청 본문을 만든다. flagged는 신규·갱신 모두 아티팩트 값으로 덮는다(bee 없으면 false). 기존 별칭은 앞에 두고 새 닉네임만 뒤에 붙인다.
// 새 닉네임이 한도(10, 기존이 더 많으면 기존 개수)를 넘으면 넘는 만큼 dropped로 센다.
function buildRequest(converted, existing) {
  if (!existing) {
    const nicknames = converted.nicknames.slice(0, MAX_NICKNAMES)
    return {
      body: { realName: converted.name, mainPosition: null, lanes: converted.lanes, nicknames, flagged: converted.flagged },
      dropped: converted.nicknames.length - nicknames.length,
    }
  }
  const nicknames = existing.aliases.map((a) => a.nickname)
  const known = new Set(nicknames.map(normNick))
  const limit = Math.max(MAX_NICKNAMES, nicknames.length)
  let dropped = 0
  for (const n of converted.nicknames) {
    if (known.has(normNick(n))) continue
    if (nicknames.length >= limit) {
      dropped++
      continue
    }
    known.add(normNick(n))
    nicknames.push(n)
  }
  const lanes = { ...(existing.lanes || {}), ...converted.lanes }
  return {
    body: { realName: existing.realName, mainPosition: existing.mainPosition ?? null, lanes, nicknames, flagged: converted.flagged },
    dropped,
  }
}

// 가져오기 계획: 선수마다 create/update/skip과 사유. existingProfiles는 GET player-profiles 결과(dry-run은 [])
function planImport(players, existingProfiles) {
  const byName = new Map()
  for (const e of existingProfiles) if (e.realName) byName.set(e.realName.trim(), e)
  return players.map((raw, index) => {
    const c = convertPlayer(raw)
    const base = { index: index + 1, name: c.name }
    if (!c.name) return { ...base, action: 'skip', reason: 'NO_NAME' }
    const existing = byName.get(c.name)
    if (!existing && Object.keys(c.lanes).length === 0) return { ...base, action: 'skip', reason: 'NO_LANE' }
    if (!existing && c.nicknames.length === 0) return { ...base, action: 'skip', reason: 'NO_NICKNAME' }
    const { body, dropped } = buildRequest(c, existing)
    return { ...base, action: existing ? 'update' : 'create', id: existing && existing.id, body, dropped, invalidLanes: c.invalidLanes }
  })
}

module.exports = { LANE_MAP, SUB_MAP, normNick, convertLanes, convertPlayer, convertScore, buildRequest, planImport }
