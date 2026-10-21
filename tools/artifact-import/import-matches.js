#!/usr/bin/env node
// 아티팩트에만 남은 경기를 결과창 이미지 없이 서버로 가져오는 도구(경기 시간 모름, ADR 014). 사용법은 README.md.
'use strict'
const fs = require('node:fs')
const { Client } = require('./client')
const { matchesOfDate, resolvePlayers, convertMatch } = require('./convert-matches')

function parseArgs(argv) {
  const o = { server: 'http://localhost:8080', user: 'admin', file: null, date: null, sessionDate: null, dryRun: false, verbose: false }
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i]
    if (a === '--server') o.server = argv[++i]
    else if (a === '--user') o.user = argv[++i]
    else if (a === '--date') o.date = argv[++i]
    else if (a === '--session-date') o.sessionDate = argv[++i]
    else if (a === '--dry-run') o.dryRun = true
    else if (a === '--verbose') o.verbose = true
    else if (!a.startsWith('--') && !o.file) o.file = a
    else throw new Error(`알 수 없는 인자: ${a}`)
  }
  return o
}

const DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/

// 경기별 문제를 코드별 건수와 경기 순번으로 보고한다(이름·닉네임은 출력하지 않는다).
function reportProblems(results) {
  const codes = new Map()
  results.forEach((r, i) => r.problems.forEach((code) => codes.set(code, [...(codes.get(code) || []), i + 1])))
  for (const [code, indexes] of codes) console.log(`  ${code}: ${indexes.length}경기 (순번 ${indexes.join(', ')})`)
  return codes.size
}

async function login(o) {
  const password = process.env.IMPORT_ADMIN_PASSWORD
  if (!password) throw new Error('환경변수 IMPORT_ADMIN_PASSWORD가 필요합니다.')
  const c = new Client(o.server)
  await c.csrf()
  const res = await c.request('POST', '/api/admin/login', { form: { username: o.user, password } })
  if (res.status !== 200) throw new Error(`로그인 실패(HTTP ${res.status})`)
  await c.csrf() // 로그인 뒤 CSRF 토큰이 바뀐다
  return c
}

async function expect(c, method, path, okStatuses, json) {
  const res = await c.request(method, path, json === undefined ? {} : { json })
  if (!okStatuses.includes(res.status)) {
    const violations = res.data && res.data.violations ? ` ${JSON.stringify(res.data.violations)}` : ''
    throw new Error(`${method} ${path} -> HTTP ${res.status} ${(res.data && res.data.code) || ''}${violations}`)
  }
  return res.data
}

async function main() {
  const o = parseArgs(process.argv.slice(2))
  if (!o.file || !DATE_PATTERN.test(o.date || '') || !DATE_PATTERN.test(o.sessionDate || '')) {
    throw new Error('사용법: node import-matches.js <app-data.json> --date YYYY-MM-DD --session-date YYYY-MM-DD --server <url> --user <id> [--dry-run]')
  }
  const data = JSON.parse(fs.readFileSync(o.file, 'utf8'))
  const matches = matchesOfDate(data, o.date)
  console.log(`${o.date} 경기 ${matches.length}판`)
  if (matches.length === 0) throw new Error('가져올 경기가 없습니다.')

  // 선수 연결: 서버 호출 없이는 서버 id를 알 수 없으므로 dry-run은 아티팩트 선수 목록에 있는지만 본다
  const known = new Set((data.players || []).map((p) => String(p.id)))
  let c = null
  let resolveId
  if (o.dryRun) {
    resolveId = (pid) => (known.has(pid) ? pid : null)
  } else {
    c = await login(o)
    const profiles = await expect(c, 'GET', '/api/admin/player-profiles', [200])
    const { map, unresolved } = resolvePlayers(data.players, profiles)
    const used = new Set(matches.flatMap((m) => m.players.map((p) => String(p.pid))))
    const missing = unresolved.filter((pid) => used.has(pid))
    if (missing.length) {
      const names = o.verbose ? ` (${missing.map((pid) => (data.players.find((p) => String(p.id) === pid) || {}).name).join(', ')})` : ''
      throw new Error(`서버 선수와 연결되지 않은 아티팩트 선수 ${missing.length}명${names}. 선수 등록·별칭을 먼저 맞추세요.`)
    }
    resolveId = (pid) => (map.has(pid) ? map.get(pid) : null)
  }

  const results = matches.map((m) => convertMatch(m, resolveId))
  const invalid = results.filter((r) => r.problems.length > 0).length
  console.log(`변환 ${results.length - invalid}판 성공, 문제 ${invalid}판`)
  if (reportProblems(results) > 0) throw new Error('문제가 있는 경기가 있어 중단합니다.')
  const pids = new Set(matches.flatMap((m) => m.players.map((p) => String(p.pid))))
  console.log(`선수 ${pids.size}명 연결 확인, 경기마다 선수 10명·팀별 라인 5개·밴 10개 통과`)

  if (o.dryRun) {
    console.log('(dry-run: 서버 호출 없음. 서버 선수와의 실제 연결은 실행 때 확인합니다)')
    return
  }

  const sessions = await expect(c, 'GET', '/api/admin/inhouse-sessions', [200])
  let session = sessions.find((s) => s.startDate === o.sessionDate)
  if (session) {
    console.log(`내전 묶음 ${o.sessionDate} 있음: 대결 ${session.duelCount}개, 세트 ${session.setCount}개 (재실행이면 지문이 같아 409로 중단됩니다)`)
  } else {
    session = await expect(c, 'POST', '/api/admin/inhouse-sessions', [201], { startDate: o.sessionDate })
    console.log(`내전 묶음 ${o.sessionDate} 새로 만듦`)
  }
  const duel = await expect(c, 'POST', `/api/admin/inhouse-sessions/${session.id}/duels`, [201], { fearlessMode: 'RESET' })
  console.log(`새 대결 만듦: 대결 ${duel.seq} (id ${duel.id}, 피어리스 RESET)`)

  for (let i = 0; i < results.length; i++) {
    try {
      const saved = await expect(c, 'POST', `/api/admin/duels/${duel.id}/sets/import`, [201], results[i].body)
      console.log(`경기 ${i + 1}/${results.length} 저장: ${saved.setNo}세트 (setId ${saved.setId})`)
    } catch (e) {
      throw new Error(`경기 ${i + 1}/${results.length}에서 중단: ${e.message}. 앞 ${i}판은 저장되었습니다(대결 id ${duel.id}).`)
    }
  }
  console.log('완료')
}

main().catch((e) => {
  console.error(`오류: ${e.message}`)
  process.exit(1)
})
