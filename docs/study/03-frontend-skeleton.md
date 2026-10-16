# 03. 프런트 골격: Vite + React + TypeScript, 라우팅, 테마, 서버 상태 캐시

> 마일스톤 M1. 저장소 골격은 [01](01-repository-skeleton.md), 백엔드 골격은 [02](02-backend-skeleton.md).

## 1. 무엇을 만들었나

`frontend/`에 **화면 뼈대**만 만들었다. 공개/담당자 두 가지 헤더, 12개 URL(모두 "어느 마일스톤에서 구현한다"만 적힌 자리표시자 페이지), 와이어프레임 색 토큰을 그대로 옮긴 다크/라이트 테마, TanStack Query 설정, 개발용 `/api` 프록시, 테스트 2종(테마 토글, 라우터 스모크).
실제 API 호출과 실제 화면 내용은 M3~M6에서 이 뼈대 위에 채운다.

사용한 버전(`npm ls` 기준, 2026-10-16 설치 시점):

| 패키지 | 버전 | 용도 |
|---|---|---|
| node / npm | 22.18.0 / 10.9.3 | 실행 환경 |
| vite | 8.3.4 | 개발 서버·번들러 |
| @vitejs/plugin-react | 6.1.2 | JSX 변환 |
| react / react-dom | 19.3.1 | UI |
| typescript | 6.0.3 | 타입 검사 |
| react-router-dom | 7.18.4 | URL → 화면 |
| @tanstack/react-query | 5.104.3 | 서버 상태 캐시 |
| vitest | 5.0.5 | 테스트 실행기 |
| @testing-library/react / jest-dom / user-event | 16.3.3 / 7.0.1 / 14.6.8 | 컴포넌트 테스트 |
| jsdom | 29.1.1 | 테스트용 가짜 브라우저 |
| oxlint | 1.88.0 | 린트(템플릿 기본 포함) |

`package.json`에는 `^`가 붙어 있고, 정확한 버전은 `package-lock.json`이 고정한다.

## 2. 왜 이 구조인가

### Vite (개발 서버 + 번들러)
- **이유**: 개발 서버가 바로 뜨고 저장하면 화면이 즉시 바뀐다(HMR). 설정이 거의 없다. `npm create vite`가 React+TS 템플릿을 제공한다.
- **버린 대안**: Create React App(유지보수 중단), Next.js(서버 렌더링·파일 라우팅이 필요 없다. 이 프로젝트는 Spring이 API를 맡는 SPA로 정했다 — implementation-plan 기술 스택 표).

### TypeScript
- API 응답(KDA, 포지션, 챔피언 키 등)을 나중에 타입으로 적어 두면 필드 이름 오타를 실행 전에 잡는다. `npm run build`가 `tsc -b`를 먼저 돌려서 타입 오류가 있으면 빌드가 실패한다.

### React Router의 "데이터 라우터" (`createBrowserRouter`)
- **선택**: 라우트를 JSX `<Route>`가 아니라 **객체 배열**(`routes.tsx`)로 정의하고 `createBrowserRouter(routes)` + `<RouterProvider>`로 실행한다.
- **이유**: (1) 라우트가 데이터라서 테스트에서 같은 배열을 `createMemoryRouter`에 넣어 URL별 화면을 쉽게 검증한다. (2) 나중에 라우트에 `loader`/`action`을 붙일 수 있는 공식 권장 방식이다.
- **버린 대안**: `<BrowserRouter>` + `<Routes>`(구방식). 동작은 하지만 테스트·확장 면에서 이점이 적다.
- 레이아웃은 **부모 라우트에 `element`만 있고 `path`가 없는 "레이아웃 라우트"**로 만들었다. 자식 URL은 그대로 `/ranking`처럼 유지되고, 자식 화면은 레이아웃의 `<Outlet />` 자리에 끼워진다.

### TanStack Query vs `useEffect` + `fetch`
- **useEffect 직접 호출의 문제**: 로딩/에러/성공 상태를 화면마다 손으로 관리, 같은 데이터를 두 화면이 각자 다시 받음, 언마운트 후 응답 도착 같은 경쟁 상태를 직접 막아야 한다.
- **TanStack Query**: `queryKey`로 응답을 캐시하고, 로딩·에러 상태를 제공하며, 중복 요청을 합친다. 이 프로젝트는 읽기 화면이 대부분(랭킹, 기록)이고 M4에서 추출 작업 상태 폴링(`refetchInterval`)이 필요해서 잘 맞는다.
- 이번 마일스톤에는 실제 쿼리가 없다. Provider와 기본값만 세팅했다.

기본값(`queryClient.ts`)과 이유:
- `staleTime: 30_000` — 30초 동안은 "신선"하다고 보고 같은 화면에 다시 들어와도 재요청하지 않는다. 기본값 0이면 화면을 오갈 때마다 요청이 나간다. 기록은 담당자가 등록할 때만 바뀌므로 30초면 충분하다.
- `retry: 1` — 기본 3회(지수 대기)면 서버가 죽었을 때 에러 화면이 한참 뒤에 나온다. 1회만 재시도한다.
- `refetchOnWindowFocus: false` — 탭을 다시 눌렀을 때 자동 재요청을 끈다. 친구 19명 규모에서 얻는 이점이 작고, 입력 중인 확인 화면이 갑자기 갱신되는 부작용을 피한다. 필요한 화면(폴링)은 개별 옵션으로 켠다.

### CSS 변수 vs Tailwind / CSS-in-JS
- **선택**: `theme.css`에 CSS 커스텀 프로퍼티(`--bg` 등)로 토큰을 정의하고 일반 CSS에서 `var(--bg)`로 쓴다.
- **이유**: 와이어프레임이 이미 확정된 색 토큰 집합을 갖고 있다. 변수 값만 바꾸면 모든 요소가 즉시 테마를 따라가므로(`data-theme` 한 속성), JS 재렌더가 필요 없다. 의존성도 0개다.
- **버린 대안**: Tailwind(클래스가 길어지고 빌드 설정이 늘며, 토큰은 결국 CSS 변수로 연결해야 함), styled-components/emotion(런타임 비용, 테마 전환 시 재계산, 요청 범위 밖).

### 개발 프록시 vs CORS
- 브라우저는 **출처(origin = 프로토콜+호스트+포트)**가 다른 서버로 요청할 때 CORS 검사를 한다. 화면은 `localhost:5173`, API는 `localhost:8080`이라 서로 다른 출처다.
- **선택**: Vite 개발 서버가 `/api`로 시작하는 요청을 8080으로 대신 전달(프록시)한다. 브라우저 입장에서는 같은 출처(5173)로만 요청하므로 CORS가 생기지 않는다.
- **운영과 일치**: implementation-plan에서 "SPA와 API를 같은 도메인(CloudFront 경로 라우팅)에 둔다"고 정했다. 로컬 프록시는 그 구조를 흉내 낸 것이라, 세션 쿠키(HttpOnly, SameSite) 인증도 로컬과 운영에서 같은 방식으로 동작한다. 백엔드에 CORS 설정을 넣을 필요가 없다.
- **버린 대안**: Spring에 `@CrossOrigin` 허용. 운영에서는 필요 없는 설정이 되고, 쿠키 인증에서 `credentials`/`SameSite` 설정이 복잡해진다.

## 3. 흐름

### 화면 하나가 뜨는 과정 (예: 브라우저에서 `http://localhost:5173/ranking`)
1. **브라우저 → Vite 개발 서버(5173)**: `/ranking` 요청. 이 경로는 실제 파일이 없으므로 Vite가 SPA 대체 규칙으로 `index.html`을 돌려준다(그래서 새로고침해도 404가 안 난다).
2. **index.html**: `<html lang="ko" data-theme="dark">`, Google Fonts 링크(IBM Plex Sans KR), `<div id="root">`, `<script type="module" src="/src/main.tsx">`를 담고 있다. 브라우저가 이 스크립트를 Vite에 다시 요청하면 Vite가 TS/JSX를 변환해 준다.
3. **main.tsx 실행**:
   1. `theme.css`, `global.css` 로드.
   2. `applyTheme(readStoredTheme())` — localStorage에 저장된 테마를 **첫 렌더 전에** `<html data-theme>`에 적용(깜빡임 방지).
   3. `createBrowserRouter(routes)`로 라우터 생성.
   4. `<QueryClientProvider>` → `<RouterProvider>` 순으로 감싸 렌더.
4. **라우터**: 현재 URL `/ranking`을 `routes`에서 찾는다 → 부모 `PublicLayout` + 자식 `PlaceholderPage(title="랭킹")`.
5. **PublicLayout**: 헤더(로고, `NavLink`들, 담당자 링크, `ThemeToggle`)를 그리고 `<Outlet />` 자리에 자식 페이지를 넣는다. `NavLink`는 현재 URL과 일치하면 자동으로 `active` 클래스를 붙인다.
6. **링크 클릭**: `<Link>`/`<NavLink>`는 브라우저의 페이지 이동 대신 History API로 주소만 바꾸고 라우터가 화면을 교체한다. 서버 요청이 없다(SPA).

### 테마 토글
1. 사용자가 버튼 클릭 → `useTheme`의 `toggleTheme()`.
2. 다음 값 계산 → `applyTheme`(`<html data-theme>` 변경) → `saveTheme`(localStorage, 실패해도 무시) → `setTheme`(버튼 글자 갱신).
3. CSS는 `[data-theme='light'] { --bg: ... }`가 `:root`의 값을 덮어쓰므로 모든 `var(--...)`가 즉시 새 값을 쓴다.
4. 다음 방문: `main.tsx`가 localStorage를 읽어 같은 테마로 시작.

### 나중에 API 호출이 붙는 흐름 (M3 이후)
1. 컴포넌트에서 `useQuery({ queryKey: [...], queryFn: () => fetch('/api/...') })`.
2. 브라우저 → `localhost:5173/api/...`(같은 출처) → Vite 프록시가 `localhost:8080/api/...`로 전달 → Spring 응답 → 프록시가 그대로 브라우저에 반환.
3. TanStack Query가 결과를 `queryKey`로 캐시하고 컴포넌트에 `data`/`isPending`/`error`로 제공. 30초 안에 같은 키를 다시 쓰면 캐시를 즉시 보여 준다.
4. Spring이 꺼져 있으면 프록시가 502를 돌려준다(아래 함정 참고).

## 4. 파일·클래스·메서드 지도

경로는 모두 `frontend/` 기준.

| 파일 | 역할 |
|---|---|
| `index.html` | 진입 HTML. 언어 ko, 기본 `data-theme="dark"`, 폰트 링크, `#root`, main.tsx 로드 |
| `vite.config.ts` | React 플러그인, `/api` 프록시, Vitest 설정(jsdom, setup 파일). `defineConfig`를 `vitest/config`에서 가져와 `test` 블록 타입을 인식시킴 |
| `src/main.tsx` | 진입점. 테마 선적용 → Provider 조립 → 렌더 |
| `src/queryClient.ts` | `QueryClient` 인스턴스와 기본 옵션 |
| `src/routes.tsx` | `routes` 배열(URL 지도). 브라우저·테스트가 공유 |
| `src/layouts/PublicLayout.tsx` | 공개 헤더 + Outlet |
| `src/layouts/AdminLayout.tsx` | 담당자 헤더 + Outlet |
| `src/components/ThemeToggle.tsx` | 테마 전환 버튼 |
| `src/theme/theme.ts` | `readStoredTheme` / `applyTheme` / `saveTheme` (DOM·localStorage 접근을 한곳에 모음) |
| `src/theme/useTheme.ts` | 테마 상태 훅 `useTheme()` → `{ theme, toggleTheme }` |
| `src/pages/PlaceholderPage.tsx` | 제목 + "M?에서 구현" 한 줄 |
| `src/styles/theme.css` | 색 토큰(다크 기본, 라이트 오버라이드) |
| `src/styles/global.css` | 전역 규칙, 헤더·본문 레이아웃 |
| `src/test/setup.ts` | Vitest 공통 준비(jest-dom 등록, 테스트 사이 정리) |
| `src/components/ThemeToggle.test.tsx` | 토글 시 `data-theme` 변경 + localStorage 저장 검증 |
| `src/routes.test.tsx` | `/ranking`, `/admin/players`가 올바른 레이아웃과 제목을 그리는지 |

URL 12개와 담당 마일스톤:

| URL | 레이아웃 | 채우는 마일스톤 |
|---|---|---|
| `/` `/ranking` `/players` `/players/:playerId` `/records` `/duels/:duelId` `/sets/:setId` | 공개 | M5 |
| `/admin/login` | 공개(헤더 단순화는 M3에서 재검토) | M3 |
| `/admin`, `/admin/players` | 담당자 | M3 |
| `/admin/duels/:duelId/sets/new` | 담당자 | M4 |
| `/admin/history` | 담당자 | M6 |

`/admin/login`을 공개 레이아웃에 둔 것은 로그인 전에는 담당자 메뉴를 보여 줄 이유가 없기 때문이다(가정이며, 와이어프레임 확인 후 M3에서 조정 가능).

## 5. 변수·토큰 사전

### CSS 토큰 (`theme.css`, 와이어프레임 값 그대로)
CSS 클래스·변수는 kebab-case 규칙에 따라 `errBg`는 `--err-bg`로 적었다.

| 토큰 | 다크 | 라이트 | 앞으로 쓰일 곳 |
|---|---|---|---|
| `--bg` | #11131A | #EEF0F5 | 페이지 배경 |
| `--panel` | #1B1E29 | #FFFFFF | 카드·표 배경 |
| `--panel2` | #232737 | #F6F7FA | 카드 안의 한 단계 다른 영역(펼친 세트 상세, 표 줄무늬) |
| `--line` | #2E3346 | #DCDFE8 | 테두리·구분선 |
| `--ink` | #E6E8EF | #1F2230 | 기본 글자색 |
| `--muted` | #9AA0B4 | #5B6072 | 보조 글자(날짜, 설명, 비활성 메뉴) |
| `--nav` | #0B0D13 | #FFFFFF | 헤더 배경 |
| `--win` | #1D2A48 | #E9ECFF | 세트 한 줄에서 **승리 팀** 행 배경 띠 |
| `--lose` | #3A2231 | #FBE9F0 | **패배 팀** 행 배경 띠 |
| `--blue` | #6C9BFF | #3550C8 | 블루 진영 표시(팀 이름, 밴 순서 B1~B5) |
| `--red` | #F07383 | #C23A5C | 레드 진영 표시(R1~R5) |
| `--k5` | #F0A050 | #B9520F | KDA 구간 색 상위(높은 KDA, 예: 5 이상) |
| `--k4` | #6C9BFF | #3550C8 | KDA 중간 구간 |
| `--k3` | #3CC4A4 | #16806A | KDA 하위 강조 구간 |
| `--chip` | #2A2F42 | #ECEEF4 | 작은 라벨(포지션 칩, 활성 메뉴 배경) |
| `--btn` | #4A6BDB | #3550C8 | 주요 버튼, 담당자 헤더 하단선 |
| `--err` | #FF8A7D | #B42318 | 오류 글자(추출 실패, 검증 오류) |
| `--err-bg` | #3A1F22 | #FDECEA | 오류 안내 상자 배경 |
| `--ok` | #6BD69A | #1E7A45 | 성공·저장됨 표시 |
| `--warn` | #F0B45A | #8A4B00 | 경고(매칭 불확실, 확인 필요) |

`--k4`가 `--blue`와 같은 색인 것은 와이어프레임 값 그대로다. k5/k4/k3가 각각 어떤 KDA 숫자 구간인지는 M5 화면 구현 때 와이어프레임에서 확인해 정한다(여기서는 색만 정의).

### 코드 안의 값
| 이름 | 위치 | 의미 |
|---|---|---|
| `STORAGE_KEY = 'theme'` | `theme/theme.ts` | localStorage 키. 값은 `'dark'` 또는 `'light'` |
| `Theme` | `theme/theme.ts` | `'dark' \| 'light'` 타입 |
| `theme` (state) | `useTheme.ts` | 현재 테마. 초기값은 `readStoredTheme()`(함수를 넘겨 첫 렌더에만 실행) |
| `routes` | `routes.tsx` | `RouteObject[]`. 부모(레이아웃, path 없음) → 자식(path 있음) |
| `PlaceholderPageProps` | `PlaceholderPage.tsx` | `title`(제목), `milestone`(예: "M5") |
| `:playerId` `:duelId` `:setId` | `routes.tsx` | URL 변수. 페이지에서 `useParams()`로 읽는다(M5부터) |
| `staleTime` / `retry` / `refetchOnWindowFocus` | `queryClient.ts` | 위 2절 설명 |
| `server.proxy['/api']` | `vite.config.ts` | 개발 서버가 `/api*`를 `http://localhost:8080`으로 전달 |
| `test.environment` / `test.setupFiles` | `vite.config.ts` | Vitest가 jsdom을 쓰고 시작 전에 setup.ts를 실행 |
| `font-variant-numeric: tabular-nums` | `global.css` body | 숫자 폭을 같게 해 KDA·골드 열이 세로로 정렬되도록 함 |

## 6. 개념 설명

- **SPA와 클라이언트 라우팅**: 서버가 HTML을 한 번만 주고, 이후 URL 변화는 브라우저의 History API(`pushState`)로 주소만 바꾸며 자바스크립트가 화면을 교체한다. 장점은 이동이 빠르다는 것, 주의점은 새로고침/직접 접속 시 서버가 어떤 경로든 `index.html`을 줘야 한다는 것(개발은 Vite가, 운영은 CloudFront 설정이 담당 — M7에서 다룸).
- **Provider와 Context**: `QueryClientProvider`는 React Context로 `queryClient`를 하위 모든 컴포넌트에 전달한다. `useQuery`는 가장 가까운 Provider에서 클라이언트를 찾는다. 그래서 Provider가 라우터보다 바깥에 있어야 모든 페이지에서 쓸 수 있다.
- **React Query 캐시**: 서버에서 받은 데이터를 `queryKey`(예: `['ranking']`)로 저장. `staleTime` 안이면 "신선"이라 재요청 없이 캐시를 쓰고, 지나면 "오래됨"이라 화면에 보여 주면서 뒤에서 다시 받아온다. `gcTime`(기본 5분) 동안 아무도 쓰지 않으면 캐시를 지운다.
- **data-theme 전환**: `<html data-theme="light">`라는 속성 하나로 CSS 선택자 `[data-theme='light']`가 켜진다. 그 안에서 `--bg` 같은 변수 값을 새로 정의하면, `var(--bg)`를 쓰는 모든 규칙이 새 값으로 다시 그려진다. React 상태는 버튼 글자만 바꾸고 색 적용은 CSS가 한다.
- **localStorage**: 브라우저에 문자열을 영구 저장(같은 출처 한정). 시크릿 모드·설정에 따라 접근 시 예외가 날 수 있어 `try/catch`로 감쌌다.
- **Vitest + jsdom + Testing Library**: Vitest는 Vite 설정을 그대로 쓰는 테스트 실행기. jsdom은 Node 안에서 흉내 내는 가짜 브라우저(DOM, localStorage). Testing Library는 "사용자가 보는 방식"(역할, 이름)으로 요소를 찾고 클릭하게 해 준다. `jest-dom`은 `toHaveAttribute` 같은 DOM 전용 단언을 추가한다.
- **MemoryRouter**: 실제 주소창 대신 메모리에 URL을 두는 라우터. 테스트에서 `initialEntries: ['/ranking']`로 특정 URL에서 시작할 수 있다.
- **oxlint**: Rust로 만든 빠른 린터. 이번 Vite 템플릿이 ESLint 대신 기본으로 넣어 줬고, 그대로 두었다.

## 7. 함정과 디버깅

실제로 겪은 것:
- **템플릿이 예상과 달랐다**: 최신 `create-vite` react-ts 템플릿은 ESLint가 아니라 **oxlint**를 `npm run lint`에 연결하고, TypeScript 6·Vite 8을 쓴다. 요구가 "lint가 통과해야 한다"였으므로 ESLint를 추가로 설치하지 않고(의존성 최소화) 그대로 썼다. oxlint는 문제가 없으면 아무것도 출력하지 않고 종료 코드 0으로 끝난다 — 출력이 없다고 실행이 안 된 게 아니다(`echo $?`로 확인).
- **템플릿 데모 코드 제거**: `App.tsx`, `App.css`, `index.css`, `assets/`, `public/icons.svg`는 데모용이라 지웠다. 남겨 두면 안 쓰는 CSS가 전역 스타일(`#root` 폭 제한 등)과 충돌한다. `public/favicon.svg`(Vite 기본 아이콘)는 남겼다 — 로고 확정 시 교체.
- **테마 버튼과 실제 색이 어긋날 수 있었음 (Codex 리뷰로 발견·수정)**: 처음 `useTheme`는 시작 상태를 **localStorage 저장값**에서 읽었다. 시크릿 모드처럼 저장이 막힌 환경에서 라이트로 바꾼 뒤 공개 화면 → 담당자 화면으로 이동하면, 레이아웃이 바뀌며 토글 컴포넌트가 새로 만들어지고 저장값이 없으니 "다크"로 시작한다. 그런데 `<html data-theme>`은 라이트 그대로라 버튼 표시와 화면 색이 어긋나고, 다음 클릭도 색을 바꾸지 못했다. 조치: 시작 상태를 **지금 화면에 적용된 값**(`readAppliedTheme`, `<html data-theme>`)에서 읽게 바꿨다. 앱 시작 때 `main.tsx`가 저장값을 화면에 먼저 적용하므로 평소에는 둘이 같다. 결과: 저장이 막혀도 일치. 회귀 테스트 "localStorage가 막혀 있어도 다시 그려질 때 화면 테마와 버튼 표시가 일치한다" 추가(`vi.spyOn(Storage.prototype, 'setItem')`으로 저장 실패를 흉내).
- **`/api` 프록시는 백엔드가 꺼져 있으면 502**: 개발 서버로 `/api/ping`을 호출해 보니 Spring이 없어서 `502`와 터미널의 `http proxy error ... ECONNREFUSED`가 나왔다. 프런트 버그가 아니라 8080이 안 떠 있다는 뜻이다. 반대로 `/ranking`은 200(SPA 대체 규칙).

테스트에서 미리 막아 둔 것(이번 구현에서는 증상을 직접 겪지는 않았고, 알려진 함정이라 처음부터 방지):
- **Testing Library 자동 정리가 안 됨**: Vitest는 기본적으로 `describe/afterEach`를 전역으로 노출하지 않아서 Testing Library의 자동 cleanup이 등록되지 않는다. 안 지우면 이전 테스트의 DOM이 남아 `getByRole`이 여러 개를 찾는다. `setup.ts`의 `afterEach(cleanup)`으로 해결.
- **localStorage·`data-theme`가 테스트 사이에 새어 나감**: 첫 테스트가 저장한 `light`가 다음 테스트의 초기 상태를 바꾼다. 같은 `afterEach`에서 `localStorage.clear()`와 속성 제거를 한다.
- **`vite.config.ts`에 `test` 블록 타입 오류**: `defineConfig`를 `vite`에서 가져오면 `test` 속성을 모른다. `vitest/config`에서 가져온다.
- **`--err-bg` 이름**: 요구된 토큰명 `errBg`를 CSS 변수에서 그대로 쓰면 프로젝트의 kebab-case 규칙과 어긋나 `--err-bg`로 바꿨다.

## 8. 스스로 점검할 질문

1. SPA에서 `/ranking`을 직접 주소창에 입력해도 화면이 뜨는 이유는? (개발 환경과 운영 환경에서 각각 누가 처리하나?) → 3절 흐름 1번, 6절 SPA 설명.
2. 부모 라우트에 `path` 없이 `element`만 둔 "레이아웃 라우트"의 장점은? `<Outlet />`은 무엇인가? → 2절 React Router, 4절 표.
3. `createBrowserRouter`와 `createMemoryRouter`를 각각 어디서 쓰고, 왜 `routes` 배열을 따로 export했나? → 2절, `routes.test.tsx`.
4. `useEffect` + `fetch` 대신 TanStack Query를 쓰는 이유 세 가지는? `staleTime: 30_000`은 무엇을 바꾸나? → 2절 TanStack Query.
5. `QueryClientProvider`가 `RouterProvider`보다 바깥에 있어야 하는 이유는? → 6절 Provider와 Context.
6. 테마 전환이 JS 재렌더 없이 모든 색을 바꾸는 원리는? 왜 `main.tsx`에서 첫 렌더 전에 `applyTheme`을 호출하나? → 3절 테마 토글, 6절 data-theme.
7. localStorage 접근을 왜 `try/catch`로 감쌌나? 실패하면 사용자에게 어떤 일이 생기나? → `theme.ts`, 6절.
8. 개발 프록시를 쓰면 왜 CORS 설정이 필요 없나? 운영 구조(CloudFront 경로 라우팅)와 어떻게 연결되나? → 2절 개발 프록시 vs CORS.
9. `win`/`lose`/`k5`/`k4`/`k3` 토큰은 각각 어느 화면에서 어떤 의미로 쓰나? → 5절 토큰 표.
10. Testing Library에서 `afterEach(cleanup)`을 직접 등록해야 하는 이유는? 빠뜨리면 어떤 증상이 나나? → 7절.
11. Tailwind나 styled-components 대신 CSS 변수를 고른 이유와 그 선택의 단점은? → 2절 (단점: 컴포넌트와 스타일 파일이 분리돼 클래스 이름 관리가 필요).
