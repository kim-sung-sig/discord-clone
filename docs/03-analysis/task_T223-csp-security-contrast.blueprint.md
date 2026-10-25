# T223 dev CSP와 보안 페이지 대비 Blueprint

## 승인 상태
- 상태: 승인됨. UI 폴리시 5개 task 계획(T219~T223)에 포함되어 이 계획으로 승인되었다.
- 사용자 요청(요지): Nuxt 웹앱(`apps/web`)의 UI 폴리시 중, `npm run dev`에서 페이지가 스타일 없이 렌더링되는 CSP 문제(I1)와 보안 페이지의 글자 대비 문제(J)를 고친다.
- 이 task 범위: 발견 I1(단, `pages/app.vue`의 root inline style은 T219 담당)과 J.
- 막힌 모호성: 없음. 아래 결정으로 해소했다.

## 목표
1. 개발 서버(`nuxt dev`)에서 Vite가 주입하는 `<style>` 태그가 CSP에 막히지 않아 페이지가 스타일을 갖고 렌더링된다. 운영 `style-src`는 계속 `'self'`다.
2. 운영 빌드에서 이 task 영역(보안 페이지 추세 막대, Nuxt가 인라인하는 컴포넌트 CSS)이 CSP inline-style 위반을 만들지 않는다.
3. 보안 페이지의 글자가 WCAG AA(일반 텍스트 4.5:1 이상)를 만족한다. 상태 간 시각적 차이는 유지한다.

## 범위와 범위 밖
- 범위
  - `server/utils/security-headers.ts`의 `style-src` 결정(한 곳).
  - `pages/security.vue` 추세 막대의 `:style` 높이 제거.
  - `nuxt.config.ts`의 `features.inlineStyles`.
  - 보안 페이지 영역 CSS와 해당 theme 규칙의 색 대비.
- 범위 밖
  - `pages/app.vue`의 root `:style`(T219), shell layout CSS 영역.
  - 보안 페이지 번역(문구 변경 없음, locale 키 추가 없음).
  - 호출부(`server/middleware/security-headers.ts`, `server/plugins/csp-nonce.ts`)는 기본값을 쓰므로 바꾸지 않는다.

## 설계 결정
1. **dev 판정은 build-time flag 한 곳에서 한다.**
   - `HtmlSecurityHeaderOptions`에 `development?: boolean`을 추가한다. 기본값은 `import.meta.dev`(Nuxt/Nitro가 빌드 시점에 치환하는 값, request header가 아님)다.
   - `styleSource(development)`가 dev면 `style-src 'self' 'unsafe-inline'`, 아니면 `style-src 'self'`를 돌려준다.
   - enforce와 report-only 두 정책이 같은 함수를 쓴다. dev에서 report-only에만 stricter 정책을 두면 Vite 주입 style마다 `csp-report-only` 위반 보고가 쌓여 노이즈가 되므로 dev는 둘 다 완화한다.
   - script-src(nonce)는 dev에서도 바꾸지 않는다.
   - 단위 테스트는 기본값(`import.meta.dev`)에 의존하지 않도록 두 모드를 `development: true/false`로 명시한다. 기존 운영 단언은 `development: false`로 고정한다.
2. **추세 막대는 class bucket으로 바꾼다.**
   - `trendBarHeight()`가 5% 단위 bucket class(`csp-trend-h-10` ~ `csp-trend-h-100`, 최소 10%)를 돌려주고, main.css에 대응 규칙을 둔다. SSR/CSSOM 모두 inline style 속성이 없다.
   - 기존 최소 높이 8%는 10%로 올라간다(5% 단위 올림).
3. **`features.inlineStyles: false`.**
   - Nuxt 4.6.2 schema(`@nuxt/schema`)에서 `features.inlineStyles`는 기본값이 `(id) => id.includes('.vue')`라서 운영 SSR이 `.vue` 컴포넌트 CSS를 `<style>` 태그로 HTML에 인라인한다(dev와 `ssr:false`는 이미 false). 이 `<style>`은 `style-src 'self'`에 막힌다.
   - `false`로 두면 컴포넌트 CSS가 `<link rel="stylesheet">`로 나가 CSP를 만족한다. 비용은 첫 렌더 전 CSS 요청 1회 증가다.
4. **대비 수정은 CSS 규칙의 우선순위 결함을 바로잡는 방식이다.** 원인 진단은 다음과 같다.
   - `Memory ready`: theme의 `.storage-health-ready { background: green }`(presence-dot 그룹에 잘못 묶임) 위에 theme의 `.security-summary-card strong`(파랑)이 main.css의 `.storage-health-ready` 색을 이겨서 초록 바탕에 파란 글자가 됐다. degraded도 같은 이유로 빨강이 아니라 파랑으로 나온다.
   - guard summary `strong`: theme의 `.guard-health-summary strong`(elevated 배경)이 main.css의 `.guard-status-*` 배경보다 specificity가 높아 어두운 글자(`#06131d`)가 어두운 배경 위에 남는다.
   - `Off` 배지: `.guard-method-disabled { opacity: .58 }`가 행 전체(라벨 포함)를 흐리게 만든다.
   - 수정: status 규칙을 `.guard-health-summary .guard-status-*`로 한정해 specificity를 높이고, summary strong 기본 글자색은 밝은 색으로 두며, `Off`는 opacity 대신 별도 색을 준다. `Memory ready/degraded`는 `.security-summary-card strong.storage-health-*`로 지정한다. 색상 값과 대비 비율은 구현 보고서에 기록한다.

## Expected Changed Files
- `docs/03-analysis/task_T223-csp-security-contrast.blueprint.md`(이 문서)
- `apps/web/server/utils/security-headers.ts`
- `apps/web/nuxt.config.ts`
- `apps/web/pages/security.vue`
- `apps/web/assets/css/main.css`(보안 페이지 영역만)
- `apps/web/assets/css/discord-vscode-theme.css`(보안 페이지 관련 규칙만)
- `apps/web/tests/components/security-headers.test.ts`
- `apps/web/tests/components/security-dashboard.test.ts`(추세 막대 class 단언)
- `apps/web/tests/e2e/security-headers.spec.ts`(운영 style-src 단언 일관성)

## 검증 계획
- 구현자: `cd apps/web && npx vitest run tests/components/security-headers.test.ts tests/components/security-dashboard.test.ts`.
- 컨트롤러:
  - `npm run lint`
  - `cd apps/web && npx vitest run`
  - `npm run build --workspace @discord-clone/web`
  - `git diff --check origin/main HEAD`
  - 운영 빌드를 Playwright로 열어 CSP inline-style 위반이 없는지, `/security` 화면 대비를 확인한다. `npm run dev` 화면이 스타일을 갖는지도 확인한다.

## 위험
- `import.meta.dev`가 Nitro server utils에서 빌드 시 치환되지 않으면 dev 완화가 적용되지 않는다. Nuxt server runtime이 같은 값을 쓰므로 가능성은 낮고, 컨트롤러의 dev 화면 확인으로 검증한다.
- `features.inlineStyles: false`는 첫 렌더 전에 CSS 요청이 늘어 FOUC 방지 이점이 줄어든다. CSP 준수가 우선이다.
- `pages/app.vue` root inline style은 T219가 제거할 때까지 운영에서 위반을 계속 만든다(범위 밖).
- 5% 단위 bucket은 추세 막대 높이를 근사한다(값 표시는 숫자 텍스트로 정확하다).
