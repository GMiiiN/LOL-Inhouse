// 아티팩트 경기 -> 세트 가져오기 API 본문으로 바꾸는 순수 함수. 서버·파일 접근 없음. 사용 방법은 README.md.
'use strict'
const { LANE_MAP, normNick } = require('./convert')

const SIDES = { blue: 'BLUE', red: 'RED' }
const isCount = (v) => Number.isInteger(v) && v >= 0

// 날짜가 같은 경기를 아티팩트 순서대로
function matchesOfDate(data, date) {
  return ((data && data.matches) || []).filter((m) => m.date === date)
}

// 아티팩트 선수 id -> 서버 선수 id. 실명이 같으면 그 선수, 아니면 닉네임이 정확히 한 서버 선수의 별칭과 겹칠 때만.
// 못 찾았거나 모호한 아티팩트 id는 unresolved에 담는다.
function resolvePlayers(artifactPlayers, serverProfiles) {
  const byName = new Map()
  const byNick = new Map()
  for (const profile of serverProfiles) {
    if (profile.realName) byName.set(profile.realName.trim(), profile.id)
    for (const alias of profile.aliases || []) {
      const key = normNick(alias.nickname)
      if (!byNick.has(key)) byNick.set(key, new Set())
      byNick.get(key).add(profile.id)
    }
  }
  const map = new Map()
  const unresolved = []
  for (const p of artifactPlayers || []) {
    const pid = String(p.id)
    const named = byName.get(String(p.name || '').trim())
    if (named !== undefined) {
      map.set(pid, named)
      continue
    }
    const candidates = new Set()
    for (const nick of p.nicks || []) for (const id of byNick.get(normNick(nick)) || []) candidates.add(id)
    if (candidates.size === 1) map.set(pid, [...candidates][0])
    else unresolved.push(pid)
  }
  return { map, unresolved }
}

// 경기 하나 -> { body, problems }. problems는 코드 문자열 목록(이름·닉네임을 담지 않는다). resolveId(pid)는 서버 선수 id 또는 null.
// 밴은 배열 순서가 그 진영의 밴 순서이고, 경기 시간은 모르므로 durationSec은 null이다.
function convertMatch(match, resolveId) {
  const problems = new Set()
  const players = []
  const ids = new Set()
  const lanesBySide = { BLUE: new Set(), RED: new Set() }
  const sizeBySide = { BLUE: 0, RED: 0 }

  if (!Array.isArray(match.players) || match.players.length !== 10) problems.add('PLAYER_COUNT')
  for (const p of match.players || []) {
    const side = SIDES[p.team]
    const position = LANE_MAP[p.lane]
    if (!side || !position) {
      problems.add('SIDE_OR_LANE')
      continue
    }
    sizeBySide[side]++
    if (lanesBySide[side].has(position)) problems.add('LANE_DUPLICATE')
    lanesBySide[side].add(position)
    const playerId = resolveId(String(p.pid))
    if (playerId === null || playerId === undefined) problems.add('PLAYER_UNRESOLVED')
    else if (ids.has(playerId)) problems.add('PLAYER_DUPLICATE')
    else ids.add(playerId)
    if (typeof p.champ !== 'string' || !p.champ.trim()) problems.add('CHAMPION')
    const stats = [p.k, p.d, p.a, p.cs, p.gold]
    if (!stats.every(isCount)) problems.add('NUMBER')
    players.push({ playerId, side, position, championKey: p.champ, kills: p.k, deaths: p.d, assists: p.a, cs: p.cs, gold: p.gold })
  }
  if (sizeBySide.BLUE !== 5 || sizeBySide.RED !== 5) problems.add('TEAM_SIZE')

  const bans = {}
  for (const [key, side] of Object.entries(SIDES)) {
    const list = match.bans && match.bans[key]
    if (!Array.isArray(list) || list.length !== 5 || !list.every((b) => typeof b === 'string' && b.trim())) problems.add('BAN_COUNT')
    bans[side] = Array.isArray(list) ? list : []
  }
  const winnerSide = SIDES[match.winner]
  if (!winnerSide) problems.add('WINNER')
  if (!/^\d{4}-\d{2}-\d{2}$/.test(String(match.date))) problems.add('DATE')

  return {
    body: { shotDate: match.date, durationSec: null, winnerSide, bans, players },
    problems: [...problems],
  }
}

module.exports = { matchesOfDate, resolvePlayers, convertMatch }
