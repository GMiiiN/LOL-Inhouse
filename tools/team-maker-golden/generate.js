// 골든 픽스처 생성기. 가상 선수("가상01"...)만 쓰고, 기준 구현(reference.js)의 결과를 JSON으로 적는다.
// 실행: node generate.js  (외부 패키지 없음)
'use strict';
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const { createReference } = require('./reference.js');

const SEED = 20261020;
const OUT = path.join(__dirname, '..', '..', 'backend', 'src', 'test', 'resources', 'team-maker', 'golden-v2.json');
const REFERENCE_SRC = path.join(__dirname, 'reference.js');

// 아티팩트 키 <-> 우리 enum. 순서가 계산의 라인 인덱스(0~4)다.
const LANE_KEYS = ['top', 'jg', 'mid', 'adc', 'sup'];
const POSITIONS = ['TOP', 'JUNGLE', 'MID', 'ADC', 'SUPPORT'];
const SUB_KO = ['상', '중', '하'];
const SUB_EN = { '상': 'HIGH', '중': 'MID', '하': 'LOW' };
const posOf = (key) => POSITIONS[LANE_KEYS.indexOf(key)];

function mulberry32(seed) {
  let a = seed >>> 0;
  return () => {
    a = (a + 0x6D2B79F5) >>> 0;
    let t = a;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}
const rnd = mulberry32(SEED);
const int = (lo, hi) => lo + Math.floor(rnd() * (hi - lo + 1));
const pick = (arr) => arr[Math.floor(rnd() * arr.length)];
function shuffle(arr) {
  const a = arr.slice();
  for (let i = a.length - 1; i > 0; i--) {
    const j = Math.floor(rnd() * (i + 1));
    [a[i], a[j]] = [a[j], a[i]];
  }
  return a;
}

const DEFAULT_SCORE = { base: { 1: 60, 2: 52, 3: 44, 4: 36, 5: 28, 6: 20 }, sub: { '상': 3, '중': 0, '하': -3 }, lane: { top: 0, jg: 0, mid: 0, adc: -4, sup: -3 } };
function randomScore() {
  const base = {};
  for (let t = 1; t <= 6; t++) base[t] = int(0, 100);
  const lane = {};
  LANE_KEYS.forEach((k) => { lane[k] = int(-10, 10); });
  return { base, sub: { '상': int(0, 10), '중': int(-3, 3), '하': int(-10, 0) }, lane };
}
const FLAT_SCORE = { base: { 1: 50, 2: 50, 3: 50, 4: 50, 5: 50, 6: 50 }, sub: { '상': 0, '중': 0, '하': 0 }, lane: { top: 0, jg: 0, mid: 0, adc: 0, sup: 0 } };

const name = (i) => '가상' + String(i).padStart(2, '0');

// 팀이 짜지도록 사람마다 "주력 라인"을 라인당 2명씩 보장하고 나머지 라인은 무작위로 더한다.
function makePlayers(opts) {
  const mains = shuffle([0, 0, 1, 1, 2, 2, 3, 3, 4, 4]);
  return mains.map((main, idx) => {
    const lanes = {};
    const set = new Set([main]);
    if (opts.allLanes) LANE_KEYS.forEach((_, i) => set.add(i));
    else if (!opts.onlyMain) { const extra = int(0, 3); for (let e = 0; e < extra; e++) set.add(int(0, 4)); }
    if (opts.dropLane !== undefined) { set.delete(opts.dropLane); if (set.size === 0) set.add((opts.dropLane + 1) % 5); }
    for (const i of set) {
      lanes[LANE_KEYS[i]] = opts.sameTier
        ? { t: opts.sameTier[0], s: opts.sameTier[1] }
        : { t: int(1, 6), s: pick(SUB_KO) };
    }
    return { id: idx + 1, name: name(idx + 1), lanes, main };
  });
}

function toSlot(s) {
  return { playerId: s.p.id, position: posOf(s.k), tier: s.v ? s.v.t : null, sub: s.v ? SUB_EN[s.v.s] : null, score: s.s };
}
function toOption(r, withTotal) {
  const o = { blue: r.blue.map(toSlot), red: r.red.map(toSlot), blueSum: r.bs, redSum: r.rs, diff: r.diff, gap: r.gap, cost: r.c };
  if (withTotal) o.total = r.tot;
  return o;
}
function scoreTableOut(score) {
  const base = {}; for (let t = 1; t <= 6; t++) base[String(t)] = score.base[t];
  const sub = {}; SUB_KO.forEach((s) => { sub[SUB_EN[s]] = score.sub[s]; });
  const lane = {}; LANE_KEYS.forEach((k) => { lane[posOf(k)] = score.lane[k]; });
  return { base, sub, lane };
}
function playersOut(players) {
  return players.map((p) => {
    const lanes = {};
    LANE_KEYS.forEach((k) => { lanes[posOf(k)] = p.lanes[k] ? { tier: p.lanes[k].t, sub: SUB_EN[p.lanes[k].s] } : null; });
    return { id: p.id, name: p.name, lanes };
  });
}
const togetherOut = (pairs) => pairs.map(([a, b]) => [a, b]);

function scoreMatrixOut(ref, players) {
  const m = {};
  for (const p of players) {
    m[String(p.id)] = {};
    LANE_KEYS.forEach((k) => {
      const s = ref.slotFor(p, k);
      m[String(p.id)][posOf(k)] = { score: s.s, tier: s.v ? s.v.t : null, sub: s.v ? SUB_EN[s.v.s] : null, hasTier: !!s.v };
    });
  }
  return m;
}

// 맨 앞 방안에 교체 작업을 적용한다(app.js res-swap 핸들러와 같은 동작). total은 갱신되지 않는 결함이 있어 비교에서 뺀다.
function applySwaps(ref, results) {
  const r = results[0];
  const steps = [];
  const n = int(1, 3);
  for (let s = 0; s < n; s++) {
    const a = { side: pick(['blue', 'red']), i: int(0, 4) };
    const b = { side: pick(['blue', 'red']), i: int(0, 4) };
    if (a.side === b.side && a.i === b.i) continue;
    const pa = r[a.side][a.i].p, pb = r[b.side][b.i].p;
    r[a.side][a.i] = ref.slotFor(pb, LANE_KEYS[a.i]);
    r[b.side][b.i] = ref.slotFor(pa, LANE_KEYS[b.i]);
    ref.recalcResult(r);
    steps.push({ a, b, result: toOption(r, false) });
  }
  return steps;
}

function run(mode, players, score, input, together, fix) {
  const data = { players, score };
  const team = mode === 'auto'
    ? { mode, chosen: input, pick: {}, together, fix: fix || {} }
    : { mode, chosen: [], pick: input, together };
  const ui = {};
  const ref = createReference({ data, team, ui });
  const err = ref.makeTeams();
  return { ref, err, ui };
}

const scenarios = [];
function push(category, mode, players, score, input, result, allowSwaps) {
  const { ref, err, ui } = result;
  const sc = { id: `${category}-${mode}-${String(scenarios.length + 1).padStart(3, '0')}`, category, mode, players: playersOut(players), scoreTable: scoreTableOut(score), ...input };
  if (err) {
    const fixed = sc.fixedLanes && Object.keys(sc.fixedLanes).length > 0;
    sc.expected = { ok: false, failure: mode === 'auto' ? (fixed ? 'TEAM_FIXED_LANE_BLOCKED' : 'TEAM_NO_VALID_SPLIT') : 'TEAM_PAIR_UNSATISFIABLE' };
  } else {
    sc.expected = { ok: true, options: ui.results.map((r) => toOption(r, true)) };
    sc.scoreMatrix = scoreMatrixOut(ref, players);
    if (allowSwaps && rnd() < 0.35) {
      sc.swaps = { optionIndex: 0, compareTotal: false, steps: applySwaps(ref, ui.results) };
    }
  }
  scenarios.push(sc);
}

function pairsFor(ids, count, forbid) {
  const pairs = [];
  for (let t = 0; t < count * 5 && pairs.length < count; t++) {
    const a = pick(ids), b = pick(ids);
    if (a === b) continue;
    if (forbid && forbid(a, b)) continue;
    if (pairs.some(([x, y]) => (x === a && y === b) || (x === b && y === a))) continue;
    pairs.push([a, b]);
  }
  return pairs;
}

// 라인마다 그 라인 티어가 있는 2명이 필요하다. 주력 라인이 라인당 2명이므로 항상 만들 수 있다.
function manualPicks(players) {
  const picks = {};
  LANE_KEYS.forEach((k, i) => { picks[k] = shuffle(players.filter((p) => p.main === i).map((p) => p.id)); });
  return picks;
}
const picksOut = (picks) => { const o = {}; LANE_KEYS.forEach((k) => { o[posOf(k)] = picks[k].slice(); }); return o; };

function autoScenario(category, players, score, togetherCount, allowSwaps) {
  const order = shuffle(players.map((p) => p.id));
  const together = pairsFor(order, togetherCount);
  push(category, 'auto', players, score, { participantIds: order, together: togetherOut(together) }, run('auto', players, score, order, together), allowSwaps);
}
function manualScenario(category, players, score, togetherCount, allowSwaps) {
  const picks = manualPicks(players);
  const laneOf = (id) => LANE_KEYS.find((k) => picks[k].includes(id));
  const together = pairsFor(players.map((p) => p.id), togetherCount, (a, b) => laneOf(a) === laneOf(b));
  push(category, 'manual', players, score, { picks: picksOut(picks), together: togetherOut(together) }, run('manual', players, score, picks, together), allowSwaps);
}

// 1) 자동 200
for (let n = 0; n < 200; n++) autoScenario('auto', makePlayers({}), n % 4 === 0 ? DEFAULT_SCORE : randomScore(), int(0, 2), true);
// 2) 수동 100
for (let n = 0; n < 100; n++) manualScenario('manual', makePlayers({}), n % 4 === 0 ? DEFAULT_SCORE : randomScore(), int(0, 2), true);
// 3) 동점 30 (자동 24 + 수동 6): 전원 같은 티어, 라인 구성만 다름
for (let n = 0; n < 30; n++) {
  const players = makePlayers({ sameTier: [int(1, 6), pick(SUB_KO)], allLanes: n % 2 === 0 });
  const score = n % 3 === 0 ? FLAT_SCORE : (n % 3 === 1 ? DEFAULT_SCORE : randomScore());
  if (n < 24) autoScenario('tie', players, score, int(0, 1), true);
  else manualScenario('tie', players, score, 0, true);
}
// 4) 실패 20: 라인 겹침 8, 같은 팀 조건 막힘 6(자동), 같은 라인 두 사람을 묶은 수동 6
for (let n = 0; n < 20; n++) {
  const score = randomScore();
  if (n < 8) {
    const players = makePlayers({ dropLane: n % 5 });
    const order = shuffle(players.map((p) => p.id));
    push('fail', 'auto', players, score, { participantIds: order, together: [] }, run('auto', players, score, order, []), false);
  } else if (n < 14) {
    const players = makePlayers({});
    const order = shuffle(players.map((p) => p.id));
    // 한 사람과 다섯 명을 같은 팀으로 묶으면 한 팀이 6명이 되어야 해서 불가능하다.
    const together = order.slice(1, 6).map((id) => [order[0], id]);
    push('fail', 'auto', players, score, { participantIds: order, together: togetherOut(together) }, run('auto', players, score, order, together), false);
  } else {
    const players = makePlayers({});
    const picks = manualPicks(players);
    const lane = LANE_KEYS[n % 5];
    const together = [[picks[lane][0], picks[lane][1]]];
    push('fail', 'manual', players, score, { picks: picksOut(picks), together: togetherOut(together) }, run('manual', players, score, picks, together), false);
  }
}

// 5) 라인 고정 60 (자동만): 성공 15 / 라인당 2명 고정 10 / 티어 없는 라인 고정 15 / 막힘 20
// 고정 라인 키 -> 입력용 fix 맵(아티팩트 team.fix)과 출력용 fixedLanes 맵
function fixScenario(players, score, fixPairs, togetherCount, onlyFail) {
  const order = shuffle(players.map((p) => p.id));
  const together = pairsFor(order, togetherCount);
  const fix = {}, fixedLanes = {};
  fixPairs.forEach(([id, key]) => { fix[id] = key; fixedLanes[String(id)] = posOf(key); });
  push('fix', 'auto', players, score, { participantIds: order, together: togetherOut(together), fixedLanes }, run('auto', players, score, order, together, fix), !onlyFail);
}
const mainsOf = (players, laneIdx) => players.filter((p) => p.main === laneIdx);
for (let n = 0; n < 60; n++) {
  const score = n % 4 === 0 ? DEFAULT_SCORE : randomScore();
  if (n < 15) {
    // 성공: 각자 자기 주력 라인에 1~3명 고정
    const players = makePlayers({});
    const chosen = shuffle(players).slice(0, int(1, 3));
    fixScenario(players, score, chosen.map((p) => [p.id, LANE_KEYS[p.main]]), int(0, 1));
  } else if (n < 25) {
    // 라인당 2명 고정: 한 라인의 주력 2명을 모두(두 사람은 다른 팀), 경우에 따라 다른 라인 한 곳 더
    const players = makePlayers({});
    const lanesPicked = shuffle([0, 1, 2, 3, 4]).slice(0, n % 2 === 0 ? 1 : 2);
    const pairs = lanesPicked.flatMap((li) => mainsOf(players, li).map((p) => [p.id, LANE_KEYS[li]]));
    fixScenario(players, score, pairs, int(0, 1));
  } else if (n < 40) {
    // 티어 없는 라인 고정: 그 라인 티어가 없는 사람을 그 라인에 1~2명 고정(라인당 최대 2명), 주력 라인 고정과 섞기도 한다
    const players = makePlayers({});
    const pairs = [];
    const used = {};
    const cands = shuffle(players);
    for (const p of cands) {
      if (pairs.length >= int(1, 2) + (n % 3 === 0 ? 1 : 0)) break;
      const missing = LANE_KEYS.filter((k, i) => !p.lanes[k] && (used[k] || 0) < 2);
      if (!missing.length) continue;
      const k = pick(missing);
      used[k] = (used[k] || 0) + 1;
      pairs.push([p.id, k]);
    }
    fixScenario(players, score, pairs, int(0, 1));
  } else {
    // 막힘: 어떤 라인 Y의 주력 2명(그 라인만 가능)이 있는데 다른 라인 주력 2명을 Y에 고정하면 Y 자리가 모자란다
    const players = makePlayers({ onlyMain: true });
    const ly = n % 5, lx = (ly + 1 + (n % 4)) % 5;
    const pairs = mainsOf(players, lx).map((p) => [p.id, LANE_KEYS[ly]]);
    if (n % 2 === 0) { const extra = pick(players.filter((p) => p.main !== lx && p.main !== ly)); pairs.push([extra.id, LANE_KEYS[extra.main]]); }
    fixScenario(players, score, pairs, 0, true);
  }
}

const sha = (file) => crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex');
const counts = {};
let okCount = 0, swapCount = 0;
scenarios.forEach((s) => {
  const k = `${s.category}/${s.mode}`; counts[k] = (counts[k] || 0) + 1;
  if (s.expected.ok) okCount++;
  if (s.swaps) swapCount++;
});
const meta = {
  format: 'team-maker-golden-v2',
  appJsSha256: 'f7194a4543a63ba01dbea92f9e41c4369c46f71c55aa5e2c87ffabfaf057dd94',
  referenceJsSha256: sha(REFERENCE_SRC),
  copiedLines: 'app.js 1, 22, 23, 25, 79, 261-336',
  seed: SEED,
  prng: 'mulberry32',
  mathRandomFixed: 0.99,
  scenarioCount: scenarios.length,
  okCount,
  swapScenarioCount: swapCount,
  counts,
  notes: [
    'players are fictional (gasang NN). ids are 1..10 and unique per scenario.',
    'auto: participantIds keeps the click order; blue side is team A.',
    'options are before the random left-right swap, top 4 at most.',
    'auto fix scenarios carry fixedLanes {playerId: POSITION}; failure with any fix is TEAM_FIXED_LANE_BLOCKED.',
    'swaps.steps apply to options[0] in order; their results omit total (original recalcResult does not update it).',
  ],
};

fs.mkdirSync(path.dirname(OUT), { recursive: true });
const lines = scenarios.map((s) => '    ' + JSON.stringify(s));
fs.writeFileSync(OUT, '{\n  "meta": ' + JSON.stringify(meta) + ',\n  "scenarios": [\n' + lines.join(',\n') + '\n  ]\n}\n');
console.log(`wrote ${scenarios.length} scenarios (ok ${okCount}, swaps ${swapCount}) to ${OUT}`, counts);
