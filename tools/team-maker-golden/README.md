# team-maker-golden

팀 짜기 계산(자동 126분할 / 수동 16가지, 교체 재계산)이 친구 아티팩트 "League of Legends Team Maker"와 같은 결과를 내는지 보증하는 골든 픽스처 생성기. 설계: `docs/design/m45-design.md` §7.1.

## 파일
- `reference.js`: 아티팩트 `app.js`의 해당 줄을 고치지 않고 옮긴 기준 구현. 머리말에 출처·SHA-256·줄 범위. 전역(`data`, `team`, `ui`)은 인자로 받고 `Math.random`은 0.99로 고정한다(좌우 교체 없음).
- `generate.js`: 시드 PRNG(mulberry32, 시드 20261020)로 **가상 선수**("가상01"~)만 만들어 `reference.js`로 계산한 결과를 JSON으로 쓴다. 친구 실명·명단·아이콘 데이터는 쓰지 않는다.

## 다시 만들기
```
cd tools/team-maker-golden
node generate.js
```
- 외부 패키지 없음(Node만). 출력: `backend/src/test/resources/team-maker/golden-v2.json`.
- 같은 시드·같은 `reference.js`면 바이트까지 같은 파일이 나온다. 달라지면 기준이나 시드가 바뀐 것이다.
- CI는 이 스크립트를 돌리지 않고 커밋된 JSON만 읽는다.

## JSON 모양 (`golden-v2.json`)
- `meta`: 형식, `app.js` SHA-256, `reference.js` SHA-256, 시드, 시나리오 수.
- `scenarios[]`: 350개(자동 200, 수동 100, 동점 30, 실패 20).
  - 입력: `players[{id, name, lanes{POSITION: {tier, sub}|null}}]`, `scoreTable{base, sub, lane}`, 자동은 `participantIds`(클릭 순서), 수동은 `picks{POSITION:[a,b]}`, 공통 `together[[a,b]]`.
  - `expected`: 성공이면 `{ok:true, options:[{blue[5], red[5], blueSum, redSum, diff, gap, cost, total}]}`(좌우 교체 전, 최대 4개), 실패면 `{ok:false, failure}`(`TEAM_NO_VALID_SPLIT` / `TEAM_PAIR_UNSATISFIABLE`). 슬롯은 `{playerId, position, tier|null, sub|null, score}`이고 라인 순서는 TOP, JUNGLE, MID, ADC, SUPPORT.
  - `scoreMatrix{playerId: {POSITION: {score, tier, sub, hasTier}}}`.
  - `swaps`(일부): `options[0]`에 순서대로 적용하는 교체 `steps[{a:{side,i}, b:{side,i}, result}]`. `result`에는 `total`이 없다. 원본 `recalcResult`가 `tot`를 갱신하지 않기 때문이고(설계 §11 P2-6) 우리 계약은 `total = blueSum + redSum`이다.

## 기준 파일 확인
`reference.js` 머리말의 SHA-256은 옮겨 온 원본 `app.js` 전체의 해시다. 원본이 없어도 `reference.js`와 JSON만으로 테스트는 돈다.
