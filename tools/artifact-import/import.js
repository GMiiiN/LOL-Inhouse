#!/usr/bin/env node
// 아티팩트 데이터 JSON을 관리자 API(player-profiles, score-table)로 넣는 도구. 사용법은 README.md.
'use strict'
const fs = require('node:fs')
const { planImport, convertScore } = require('./convert')
const { Client } = require('./client')

function parseArgs(argv) {
  const o = { server: 'http://localhost:8080', user: 'admin', file: null, withScore: false, dryRun: false, verbose: false }
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i]
    if (a === '--server') o.server = argv[++i]
    else if (a === '--user') o.user = argv[++i]
    else if (a === '--with-score') o.withScore = true
    else if (a === '--dry-run') o.dryRun = true
    else if (a === '--verbose') o.verbose = true
    else if (!a.startsWith('--') && !o.file) o.file = a
    else throw new Error(`알 수 없는 인자: ${a}`)
  }
  return o
}

const count = (arr, f) => arr.filter(f).length

// 이름은 --verbose일 때만 출력한다. 기본은 순번·건수만.
function printPlan(plan, verbose) {
  const skips = plan.filter((p) => p.action === 'skip')
  console.log(`선수 ${plan.length}명: 생성 ${count(plan, (p) => p.action === 'create')}, 갱신 ${count(plan, (p) => p.action === 'update')}, 미등록 선수에 실명 연결 ${count(plan, (p) => p.action === 'adopt')}, 건너뜀 ${skips.length}`)
  for (const reason of ['NO_NAME', 'NO_LANE', 'NO_NICKNAME', 'MULTIPLE_UNREGISTERED']) {
    const list = skips.filter((p) => p.reason === reason)
    if (list.length) console.log(`  건너뜀 ${reason}: ${list.length}건 (순번 ${list.map((p) => p.index).join(', ')})`)
  }
  const warn = plan.filter((p) => p.dropped || p.invalidLanes)
  if (warn.length) {
    console.log(`  주의: 닉네임 한도 초과 ${count(plan, (p) => p.dropped)}명, 형식 오류 라인 ${count(plan, (p) => p.invalidLanes)}명 (순번 ${warn.map((p) => p.index).join(', ')})`)
  }
  if (verbose) for (const p of plan) console.log(`  #${p.index} ${p.name} -> ${p.action}${p.reason ? ' ' + p.reason : ''}`)
}

async function main() {
  const o = parseArgs(process.argv.slice(2))
  if (!o.file) throw new Error('데이터 JSON 경로가 필요합니다. 예: node import.js 데이터.json --server http://localhost:8080 --user admin')
  const data = JSON.parse(fs.readFileSync(o.file, 'utf8'))
  if (!data || !Array.isArray(data.players)) throw new Error('players 배열이 없는 파일입니다.')
  const scoreBody = o.withScore ? convertScore(data.score) : null
  if (o.withScore && !scoreBody) throw new Error('score 형식이 올바르지 않습니다(base 1~6, sub 상/중/하, lane 필요).')

  if (o.dryRun) {
    printPlan(planImport(data.players, []), o.verbose)
    if (o.withScore) console.log('점수표: PUT 대상 14칸 변환 성공')
    console.log('(dry-run: 서버 호출 없음. 기존 선수와의 이름 비교는 하지 않아 모두 생성으로 센다)')
    return
  }

  const password = process.env.IMPORT_ADMIN_PASSWORD
  if (!password) throw new Error('환경변수 IMPORT_ADMIN_PASSWORD가 필요합니다.')
  const c = new Client(o.server)
  await c.csrf()
  const login = await c.request('POST', '/api/admin/login', { form: { username: o.user, password } })
  if (login.status !== 200) throw new Error(`로그인 실패(HTTP ${login.status})`)
  await c.csrf() // 로그인 뒤 CSRF 토큰이 바뀐다
  const list = await c.request('GET', '/api/admin/player-profiles')
  if (list.status !== 200) throw new Error(`선수 조회 실패(HTTP ${list.status})`)

  const plan = planImport(data.players, list.data)
  printPlan(plan, o.verbose)
  const failures = []
  let done = 0
  for (const p of plan) {
    if (p.action === 'skip') continue
    const body = { ...p.body, recordChange: false } // 가져오기는 ▲▼ 변동으로 치지 않는다
    const res =
      p.action === 'create'
        ? await c.request('POST', '/api/admin/player-profiles', { json: body })
        : await c.request('PUT', `/api/admin/player-profiles/${p.id}`, { json: body })
    if (res.status === 200 || res.status === 201) done++
    else failures.push({ index: p.index, name: p.name, status: res.status, code: (res.data && res.data.code) || '?' })
  }
  console.log(`저장 성공 ${done}건, 실패 ${failures.length}건`)
  const codes = {}
  for (const f of failures) codes[f.code] = (codes[f.code] || 0) + 1
  for (const [code, n] of Object.entries(codes)) {
    console.log(`  ${code}: ${n}건 (순번 ${failures.filter((f) => f.code === code).map((f) => f.index).join(', ')})`)
  }
  if (o.verbose) for (const f of failures) console.log(`  #${f.index} ${f.name} -> HTTP ${f.status} ${f.code}`)

  if (scoreBody) {
    const res = await c.request('PUT', '/api/admin/score-table', { json: scoreBody })
    console.log(res.status === 200 ? '점수표 저장 성공' : `점수표 저장 실패(HTTP ${res.status} ${(res.data && res.data.code) || ''})`)
  }
}

main().catch((e) => {
  console.error(`오류: ${e.message}`)
  process.exit(1)
})
