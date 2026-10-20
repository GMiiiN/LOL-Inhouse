# ADR 008. 챔피언은 Data Dragon `id` 문자열로 저장한다

- 상태: 확정 (M4, 2026-10-20)
- 관련: `docs/design/m4-design.md` D6·§2.2, [ADR 003 추출 방식](003-extraction-approach.md), 코드 `champion/*`

## 배경

세트 선수의 픽(`game_set_player.champion_key`)과 밴(`game_set_ban.champion_key`)에 무엇을 저장할지 정해야 한다. Data Dragon은 챔피언마다 `id`(예: `Ahri`, `MonkeyKing`)와 숫자 `key`(예: `103`)와 이름(언어별)을 준다. 결과창 아이콘 매칭(ADR 003)도 Data Dragon 아이콘을 쓴다.

## 결정

`champion_key`에는 Data Dragon `id` 문자열을 저장한다.

- 서버는 Data Dragon 카탈로그(`versions.json` → `champion.json`(ko_KR) → 아이콘)를 처음 쓸 때 받아 디스크(`data/ddragon/{version}`)에 캐시하고 12시간마다 버전을 확인한다. 네트워크가 안 되면 캐시된 최신 버전을 쓴다.
- 저장·검사 때 카탈로그에 없는 `id`는 규칙 위반(422)으로 막는다.
- 화면은 `id`로 Data Dragon CDN의 아이콘 URL을 바로 만든다.

## 대안

- **숫자 `key`**: 버전이 바뀌어도 안정적이지만 사람이 읽을 수 없고, 아이콘 URL을 만들려면 다시 `id`로 바꿔야 한다.
- **한글 이름**: 패치·번역 변경에 흔들리고 띄어쓰기 표기가 일정하지 않다.

## 결과

- DB를 바로 읽어도 어떤 챔피언인지 알 수 있고, 정답지(M0 `ground-truth.json`) 형식과도 같다.
- 위험: Data Dragon `id`가 바뀐 전례가 있다(대소문자 등). 바뀌면 마이그레이션 UPDATE로 옛 값을 새 값으로 고친다. 패치 직후 새 챔피언이 Data Dragon에 아직 없으면 고를 수 없다.
