'use strict'
const test = require('node:test')
const assert = require('node:assert')
const { matchesOfDate, resolvePlayers, convertMatch } = require('./convert-matches')

const LANES = ['top', 'jg', 'mid', 'adc', 'sup']

// 가상 데이터: 선수 pid 'p0'~'p9', 앞 5명 blue, 뒤 5명 red
function makeMatch(over = {}) {
  const players = []
  for (let i = 0; i < 10; i++) {
    players.push({ nick: 'n' + i, pid: 'p' + i, team: i < 5 ? 'blue' : 'red', lane: LANES[i % 5], champ: 'C' + i, k: i, d: 2, a: 3, cs: 100 + i, gold: 9000 + i })
  }
  return {
    id: 'm1',
    date: '2026-10-15',
    winner: 'red',
    bans: { blue: ['B0', 'B1', 'B2', 'B3', 'B4'], red: ['B5', 'B6', 'B7', 'B8', 'B9'] },
    players,
    ...over,
  }
}

const idOf = (pid) => Number(pid.slice(1)) + 100

test('날짜가 같은 경기만 아티팩트 순서대로 고른다', () => {
  const data = { matches: [makeMatch({ id: 'a' }), makeMatch({ id: 'b', date: '2026-10-14' }), makeMatch({ id: 'c' })] }
  assert.deepStrictEqual(matchesOfDate(data, '2026-10-15').map((m) => m.id), ['a', 'c'])
  assert.deepStrictEqual(matchesOfDate({}, '2026-10-15'), [])
})

test('정상 경기는 가져오기 본문으로 바뀐다(시간 null, 진영·밴·포지션 대응)', () => {
  const r = convertMatch(makeMatch(), idOf)
  assert.deepStrictEqual(r.problems, [])
  assert.strictEqual(r.body.shotDate, '2026-10-15')
  assert.strictEqual(r.body.durationSec, null)
  assert.strictEqual(r.body.winnerSide, 'RED')
  assert.deepStrictEqual(r.body.bans.BLUE, ['B0', 'B1', 'B2', 'B3', 'B4'])
  assert.deepStrictEqual(r.body.bans.RED, ['B5', 'B6', 'B7', 'B8', 'B9'])
  assert.strictEqual(r.body.players.length, 10)
  assert.deepStrictEqual(r.body.players[3], { playerId: 103, side: 'BLUE', position: 'ADC', championKey: 'C3', kills: 3, deaths: 2, assists: 3, cs: 103, gold: 9003 })
  assert.strictEqual(r.body.players[4].position, 'SUPPORT')
  assert.strictEqual(r.body.players[1].position, 'JUNGLE')
})

test('10명이 아니거나 팀별 5명이 아니면 문제로 보고한다', () => {
  const m = makeMatch()
  m.players.pop()
  assert.ok(convertMatch(m, idOf).problems.includes('PLAYER_COUNT'))
  const m2 = makeMatch()
  m2.players[0].team = 'red'
  assert.ok(convertMatch(m2, idOf).problems.includes('TEAM_SIZE'))
})

test('팀 안 라인 중복, 같은 선수 중복, 밴 개수, 승자 값을 검증한다', () => {
  const lane = makeMatch()
  lane.players[1].lane = 'top'
  assert.ok(convertMatch(lane, idOf).problems.includes('LANE_DUPLICATE'))

  const dup = makeMatch()
  dup.players[6].pid = 'p0'
  assert.ok(convertMatch(dup, idOf).problems.includes('PLAYER_DUPLICATE'))

  const ban = makeMatch()
  ban.bans.red = ['B5', 'B6', 'B7', 'B8']
  assert.ok(convertMatch(ban, idOf).problems.includes('BAN_COUNT'))
  const emptyBan = makeMatch()
  emptyBan.bans.blue[2] = ''
  assert.ok(convertMatch(emptyBan, idOf).problems.includes('BAN_COUNT'))

  assert.ok(convertMatch(makeMatch({ winner: 'draw' }), idOf).problems.includes('WINNER'))
})

test('선수 연결 실패, 챔피언·숫자 누락도 문제다', () => {
  assert.ok(convertMatch(makeMatch(), () => null).problems.includes('PLAYER_UNRESOLVED'))
  const noChamp = makeMatch()
  noChamp.players[0].champ = ''
  assert.ok(convertMatch(noChamp, idOf).problems.includes('CHAMPION'))
  const badNum = makeMatch()
  badNum.players[0].k = -1
  assert.ok(convertMatch(badNum, idOf).problems.includes('NUMBER'))
})

test('선수 연결: 실명 일치 우선, 없으면 닉네임이 정확히 한 선수와 겹칠 때만, 모호하면 미해결', () => {
  const artifact = [
    { id: 'p0', name: '가상A', nicks: ['aa'] },
    { id: 'p1', name: '가상B', nicks: ['Bb Nick'] },
    { id: 'p2', name: '가상C', nicks: ['shared'] },
    { id: 'p3', name: '가상D', nicks: ['zzz'] },
  ]
  const server = [
    { id: 1, realName: '가상A', aliases: [{ nickname: 'other' }] },
    { id: 2, realName: '다른이름', aliases: [{ nickname: 'bb  nick' }] },
    { id: 3, realName: 'X', aliases: [{ nickname: 'shared' }] },
    { id: 4, realName: 'Y', aliases: [{ nickname: 'Shared' }] },
  ]
  const { map, unresolved } = resolvePlayers(artifact, server)
  assert.strictEqual(map.get('p0'), 1)
  assert.strictEqual(map.get('p1'), 2)
  assert.deepStrictEqual(unresolved, ['p2', 'p3'])
})
