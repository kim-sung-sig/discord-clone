# T219 앱 셸 레이아웃과 모바일 정리 설계

## 승인 상태
- 상태: 승인됨. 사용자가 요청한 UI 마감 계획(5개 독립 작업 중 1번, 이 계획을 통해 승인)에 따른다. 요청의 근거는 운영 빌드 화면 점검 결과의 항목 A(셸 레이아웃), F(모바일), H(개발자 문구 중 `apps/web/pages/app.vue`에 있는 것), I1(앱 루트 인라인 스타일)이다.
- 작업 위치: 브랜치 `task_T219-shell-layout`, `origin/main` `ecf24e3`에서 분기.
- 막힌 모호성: 없음. 렌더링 확인은 컨트롤러가 운영 빌드 Playwright 화면 순회로 수행한다(구현자는 컴파일·빌드·린트·e2e를 돌리지 않는다).

## 목표
`/app` 화면이 개발용 패널과 레거시 계약 패널 없이 1440x900과 390x844에서 겹침·잘림 없이 보이게 한다.
1. 개발용 패널(`Real backend flow / Run backend smoke`)과 동작 없는 `Register account` 버튼을 제거한다.
2. `section.legacy-shell-contracts`를 `/app`에서 제거한다. 컴포넌트 계약은 단위 시험이 컴포넌트를 직접 mount해서 확인한다.
3. 셸 그리드의 모든 자식에 명시적 칸을 줘서 암시적 행으로 밀리는 요소를 없앤다. 보조 화면(검색, 알림, 통화, 설정)이 전체 내용을 보이고, 탐색기 머리말이 가려지지 않고, 상태 표시줄 항목 사이에 간격이 있다.
4. 활동 막대 버튼이 어두운 테마에 맞고(투명 배경, 테마 전경색, hover/active/focus 상태), 첫 항목(ServerRail)에 보이는 글자가 있다.
5. 하단 패널("Gateway & voice")의 카드가 1440x900에서 잘리지 않는다.
6. 사용자 패널의 표시 이름이 한 줄로 유지된다.
7. 너비 720px 이하에서 JS가 정하는 `data-platform-surface`에 의존하지 않고 CSS media query만으로 한 열 레이아웃이 되며, 메시지 글자가 한 글자씩 줄바꿈되지 않고 보내기 버튼이 완전히 보인다. 1024px 이상 데스크톱 배치는 위 수정 외에는 바꾸지 않는다.
8. CSP `style-src 'self'`에 막히는 SSR 인라인 `:style="shellThemeStyle"`을 제거한다. 테마 변수는 이미 preferences store가 CSSOM으로 적용하므로 그것에 의존한다.

## 범위와 범위 밖
- 범위: `apps/web/pages/app.vue`, 셸 CSS 3개 파일, 관련 단위 시험과 e2e 시험.
- 범위 밖
  - 데모 데이터(stores/shell.ts): T220.
  - 문구·로케일: T222. 이 작업은 로케일 키를 추가하지 않는다. 쓰이지 않게 되는 `banner.register` 키는 두 로케일에 그대로 둔다(키 집합 동일 유지).
  - CSP 헤더와 보안 페이지: T223.
  - 레거시 컴포넌트(DmSidebar, ForumPanel, ModerationPanel, ExperiencePanel, RolePermissionPanel, InviteModal)의 파일 자체는 다른 작업이 수정하므로 건드리지 않는다.
  - 내용이 제목뿐인 보조 화면이 있다면 기능을 새로 만들지 않고 보고서에 적는다.

## 설계 결정
1. **레거시 영역은 CSS로 숨기지 않고 app.vue에서 삭제한다.**
   - `.real-api-panel`, `runRealBackendSmoke`(사용처가 없어지므로 함께 삭제), `section.legacy-shell-contracts`, 사용하지 않게 된 import를 지운다.
   - `InviteModal`은 app에서 빠지므로 진입 시 자동으로 열리거나 포커스를 가져가지 않는다. 컴포넌트 파일은 그대로 둔다.
2. **UserPanel은 유지한다.** 세션 목록과 로그아웃이 이 컴포넌트에만 있어서 삭제하면 로그아웃할 방법이 없어진다. 탐색기 사이드바 맨 아래와 설정 화면에 한 번씩(보기가 배타적이라 동시에 둘이 렌더되지 않는다) 둔다.
3. **셸 그리드(데스크톱)**
   - 셸: 열 `50px minmax(0,1fr)`, 행 `32px 28px minmax(0,1fr)`. 제목 표시줄 1행 전체, 배너 2행 2열, 활동 막대 1열 2~3행, workspace 3행 2열.
   - workspace: 열 `264px minmax(0,1fr) 220px`, 행 `minmax(0,1fr) 22px`. 탐색기·편집기·사이드 패널·보조 화면은 1행, 상태 표시줄은 2행 전체, 하단 패널은 기존 절대 배치(행/열 지정은 그대로 두고 레거시 열 번호만 중화).
   - 기존 3겹 "polish" 블록은 병합하지 않고, 마지막에 읽히는 `workbench-density.css`와 `main.css` 끝 블록의 해당 규칙을 고친다. 데스크톱 그리드 규칙의 특이도가 (0,4,0)이므로 새 규칙도 `.vscode-shell.app-shell .workbench.workspace > .child`(0,5,0)로 쓴다.
   - 편집기(`.editor-workbench`)를 높이가 정해진 grid로 만들어 메시지 목록이 안쪽에서 스크롤되게 하고, `.chat-viewport`의 레거시 `grid-column: 3`을 중화한다(모바일에서 채팅이 둘째 열로 밀려 한 글자씩 줄바꿈되던 원인).
4. **활동 막대**
   - 버튼 아이콘 규칙이 영어 `title="Explorer"`에 묶여 있고 같은 규칙이 네 군데 중복돼 있다. 중복을 지우고 `data-testid`에 묶인 규칙 하나로 정리한다. `title`은 기존 로케일 키(`activity.*`)로 바꾸고, 시각적으로 숨겨진 영어 `<span>` 라벨은 삭제한다(접근성 이름은 `<strong>`이 맡는다).
   - 버튼은 `background: transparent`, `border: 0`, 기본 색 `var(--activity-fg)`(`#c5c5c5`), hover/active는 `var(--activity-fg-active)`(`#ffffff`)와 배경 혼합. 활동 막대는 세 테마 모두 어둡고 light 테마의 `--workbench-fg`는 어두운 색이라 테마 전경색 대신 이 두 토큰을 `main.css` `:root`에 정의한다.
   - focus-visible은 안쪽 2px outline이며 색은 `--activity-fg-active`(흰색)다(활동 막대 배경 대비 12.6:1 이상, WCAG 1.4.11의 3:1 충족).
   - ServerRail: `font-size: 0`을 풀고 `DC` 글자를 보이게 하며 34px 둥근 칩으로 그린다.
5. **하단 패널**: 레거시 `grid-column`(3/5, 5/7)을 `1`, `2`로 명시해서 2열 패널 안에 들어오게 하고, 패널 높이를 키우고(최대 360px) 안쪽 카드는 compact grid로 배치한다. 넘칠 때를 대비해 안쪽은 스크롤.
6. **상태 표시줄**: `display: flex`, `gap: 12px`, 항목은 줄임표.
7. **사용자 패널 이름**: `white-space: nowrap`, `overflow: hidden`, `text-overflow: ellipsis`, `min-width: 0`.
8. **모바일(720px 이하)**
   - `workbench-density.css`의 모바일 블록에서 `[data-platform-surface="pwa-mobile"]` 선택자를 제거하고 `.vscode-shell.app-shell .workbench.workspace ...`(특이도 0,5,0 이상, 데스크톱 규칙보다 뒤)로 바꾼다. `data-platform-surface` 속성은 그대로 두되 스타일은 이에 의존하지 않는다.
   - 보내기 열은 `auto`로 해서 한국어 라벨도 잘리지 않게 한다. 하단 패널의 음성 창은 `opacity: 0`, `pointer-events: none`, `transform` 때문에 안 보이던 것을 정적 배치로 되돌린다.
   - 721~1023px 구간은 별도 규칙을 두지 않는다(브리프는 720px 이하와 1024px 이상만 다룬다).
9. **인라인 스타일 제거**: `shellThemeStyle` computed와 `:style` 바인딩을 삭제한다. SSR 첫 페인트 색은 `main.css` `:root`에 vscode-dark 기본 토큰을 정의해 보장한다(`applyTheme`이 documentElement와 셸 요소에 CSSOM으로 덮어쓴다).
10. **시험**
   - 레거시 패널을 app 안에서 검사하던 시험(`app-shell.test.ts`의 role/invite/dm/forum/moderation/experience 항목)은 컴포넌트를 직접 mount하도록 옮기고, app이 레거시 영역·개발 패널·등록 버튼·초기 열린 대화상자를 렌더하지 않음을 새 시험으로 고정한다.
   - 셸 자식 구성(암시적 행을 만드는 자식이 없음)과 CSS 계약(모바일 블록이 속성 독립, 상태 표시줄 flex, 아이콘이 testid 기준)을 단위 시험으로 고정한다.
   - e2e(`app-shell`, `layout-compression`, `app-theme`, `t62-screenshot-report`, `pwa-mobile`)는 제거된 요소에 의존하는 단계를 고친다. 이 파일들은 CI에서 돌지 않는다.
   - `real-backend.spec.ts`는 CI(qa-runtime)가 `qa/real-backend-e2e.ps1`로 실행하고, `qa/real-backend-browser-smoke-default.contract.ps1`이 그 문구를 검사한다. 개발 패널이 없어졌으므로 spec은 API로 guild, 텍스트·음성 채널, 첫 메시지를 만들고(가입 응답의 토큰 사용) 브라우저에서 로그인한 뒤 탐색기의 guild 이름, 채널 항목, `chat-viewport`의 메시지, 작성기로 보낸 메시지를 확인한다. 계약 스크립트의 필수 문구도 이에 맞춘다.
   - 음성·스테이지 확인은 spec에서 뺀다. 이 둘은 제거된 개발 패널로만 만들 수 있었고, 화면의 음성 참여 버튼은 데모 채널 id(`channel-war-room`)에 고정돼 있어 실제 backend 채널로는 UI 검증이 안 된다. 계약 스크립트에서도 `voice-token-provider`, `stage-topic`, `real-backend-smoke`를 뺀다.
11. **탐색기 구역 제목**: 고정 라벨 둘(`Text Channels`, `Voice Channels`)은 채널을 한 목록으로 그리는 `ChannelSidebar` 앞뒤에 붙어 있어서 음성 채널이 텍스트 라벨 아래에 있고 음성 라벨 아래는 비어 있었다. app.vue의 고정 라벨을 지우고, `ChannelSidebar`의 그룹 제목(기존 로케일 키 `channels.text`, `channels.voice`로 현지화, 채널이 없는 그룹은 제목도 숨김)을 구역 제목으로 보이게 한다. 새 문구는 없다.
12. **CSS 정리**: 제거된 요소만 겨냥하는 규칙(`.real-api-panel`, 활동 막대 `button span`, 배너 버튼)과 그룹 제목 숨김 규칙을 삭제한다.

## Expected Changed Files
- `docs/03-analysis/task_T219-shell-layout.blueprint.md`(이 문서)
- `apps/web/pages/app.vue`
- `apps/web/assets/css/main.css`
- `apps/web/assets/css/workbench-density.css`
- `apps/web/assets/css/discord-vscode-theme.css`(필요한 경우에만)
- `apps/web/components/shell/ChannelSidebar.vue`(그룹 제목 현지화, 빈 그룹 숨김)
- `apps/web/components/shell/ServerRail.vue`, `apps/web/components/shell/UserPanel.vue`(필요한 경우에만)
- `qa/real-backend-browser-smoke-default.contract.ps1`(CI가 `real-backend.spec.ts` 문구를 검사)
- `apps/web/tests/components/app-shell.test.ts`
- `apps/web/tests/components/shell-css-contract.test.ts`(신규)
- `apps/web/tests/e2e/app-shell.spec.ts`, `real-backend.spec.ts`, `layout-compression.spec.ts`, `app-theme.spec.ts`, `t62-screenshot-report.spec.ts`, `pwa-mobile.spec.ts`

## 검증 계획
컨트롤러가 실행한다.
- `pwsh qa/real-backend-browser-smoke-default.contract.ps1`(spec 문구 계약, 파일만 읽는다)
- `npm run lint`
- `cd apps/web && npx vitest run`
- `npm run build --workspace @discord-clone/web`
- `git diff --check origin/main HEAD`
- 운영 빌드 + 실제 backend에 대한 Playwright 화면 순회(1440x900, 390x844): 앱 홈, 검색, 알림, 통화, 설정, 탐색기, 하단 패널, 모바일 앱 화면.

구현자가 돌린 것은 집중 시험뿐이다: `cd apps/web && npx vitest run tests/components/app-shell.test.ts tests/components/shell-css-contract.test.ts tests/components/platform-contracts.test.ts tests/components/shell-contracts.test.ts`

## 위험
- CSS 특이도: 겹친 규칙 3겹과 (0,4,0) 데스크톱 그리드 규칙 때문에 렌더 없이는 확신하기 어렵다. 새 규칙을 모두 마지막 파일에 같거나 더 높은 특이도로 두었고, 컨트롤러 화면 순회로 확인한다.
- 인라인 스타일 제거 후 SSR 첫 페인트는 vscode-dark 기본 토큰이다. 다른 테마를 저장한 사용자는 hydrate 직후 테마가 바뀐다(이전에도 SSR은 기본 테마였다).
- 레거시 패널이 화면에서 빠지므로 그 기능의 e2e 흐름은 단위 시험으로만 남는다. 패널 파일은 다른 작업이 계속 수정한다.
- 탐색기 안의 `UserPanel`은 모바일에서 "채널" 창에서만 보인다.
