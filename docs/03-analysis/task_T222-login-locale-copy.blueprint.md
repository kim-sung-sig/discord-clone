# T222 로그인 리다이렉트, 한국어 로케일, 사용자 문구 정리 Blueprint

## 승인 상태
- 상태: 승인됨. 이 task는 UI 폴리시 계획(`.superpowers/sdd/plan`)의 Task 4로 승인되었다.
- 요청: UI 리뷰 finding E(로그인 후 이동 없음), G(기본 언어 영어), H(개발자용·placeholder 문구 노출)를 고친다. 근거는 UI fix map의 E/G/H절이다.
- 막힌 모호성: 없음. controller가 아래 결정을 확정했다.
  - 로케일 감지는 SSR이 아니라 mount 이후 `hydrate()`에서 한다(hydration mismatch 방지). 잠깐 영어가 보였다가 바뀌는 것은 허용한다.
  - `pages/app.vue`와 CSS는 T219, shell store와 `ChatViewport.vue` 대부분은 T220이 맡는다. 이 task는 `editor.channelDescription`/`editor.hint`를 렌더링하는 최소한의 줄만 건드린다.
  - `VoicePanel.vue`의 provider 줄은 T220 몫이다. 이 task는 `voice.sfuSkeleton` 값만 바꾼다.

## 목표
1. 로그인 성공 시 워크스페이스(`/`)로 이동한다. 이미 인증된 상태로 `/login`을 열어도 같은 곳으로 이동한다. "Signed in with backend session." 문구와 "Open workspace" 링크, 로그인 버튼이 함께 보이던 중복 상태를 없앤다.
2. 저장된 선호가 없으면 `navigator.languages`/`navigator.language`로 기본 로케일을 고른다(`ko*` -> `ko-KR`, 그 외 `en-US`). 저장된 선호가 있으면 그것이 우선한다. `<html lang>`이 활성 로케일을 따른다.
3. 로그인 화면 문구(제목, 라벨, 버튼, 오류, 안내 문구)를 locale key로 옮기고 자연스러운 한국어 번역을 제공한다. "Spring Boot API", "access token stays in memory", "httpOnly cookie" 같은 개발자 문구는 제거한다.
4. placeholder 성격의 locale 값(`editor.channelDescription`, `editor.hint`, `voice.sfuSkeleton`)을 정리한다.
5. `ko-KR.json` 전체 값을 자연스럽고 일관된 한국어로 검토한다(해요체 또는 명사형 라벨, 용어: 서버, 채널, 메시지, 멤버).

## 범위와 범위 밖
- 범위
  - `LoginForm.vue`/`login.vue` 리다이렉트와 문구 locale 전환, `auth.global.ts`의 `/login` 인증 상태 리다이렉트.
  - `preferences.ts`의 로케일 감지, `<html lang>` 반영.
  - `auth.ts`의 로그인 오류 문구를 locale key로 전환.
  - `en-US.json`/`ko-KR.json` 키 추가·삭제·값 수정(두 파일의 key set은 항상 동일).
  - 관련 단위 테스트와 e2e spec 갱신.
- 범위 밖
  - 다른 패널의 하드코딩 영어(security 페이지, 레거시 패널, 세션 오류 문구 등). 아래 후속 목록에 기록한다.
  - `banner.register` 버튼 제거(T219 소관). 이 branch에서는 버튼이 남아 있으므로 key를 유지하고 값만 다듬는다.
  - 셸 레이아웃/CSS(T219), shell store와 메시지 표시(T220), 백엔드(T221).

## 설계 결정
1. **리다이렉트**
   - `LoginForm.vue`는 `auth.login()` 이후 `auth.accessToken`이 있으면 `navigateTo('/', { replace: true })`로 이동한다. 로그인 페이지를 history에 남기지 않는다.
   - mount 시 `restoreSession()`이 성공하면(refresh cookie로 이미 인증됨) 같은 곳으로 이동한다.
   - `middleware/auth.global.ts`는 `/login` 경로에서 메모리 access token이 이미 있으면 `/`로 보낸다. token이 없을 때 `/login`에서 refresh를 호출하지 않는 기존 동작(테스트로 고정됨)은 유지한다.
   - `login-success` 문구와 `open-workspace` 링크, 관련 CSS를 삭제한다. `data-testid`가 제거되는 요소는 이 둘뿐이고 `login-submit`, `login-form`, `login-email`, `login-password`, `login-error`는 유지한다. `login-token-policy` 요소는 개발자 문구라 제거한다.
2. **로케일 감지**
   - `preferences.ts`에 순수 함수 `detectLocale(languages)`를 export한다. 언어 목록을 선호 순서대로 훑어 처음 만나는 `ko*`는 `ko-KR`, `en*`는 `en-US`로 정하고, 둘 다 없으면 `en-US`다.
   - `hydrate()`는 저장값이 유효하면 그것을 쓰고, 없으면 `detectLocale(navigator.languages ?? [navigator.language])`를 쓴다. 감지 결과는 저장하지 않는다(사용자가 직접 고를 때만 `persist`). 그래서 브라우저 언어를 바꾸면 따라간다.
   - `applyLocale()`이 `document.documentElement.lang`을 갱신한다. `hydrate()`와 `setLocale()`에서 호출한다. `applyTheme()`과 같은 직접 DOM 방식이라 `app.vue`를 건드리지 않는다.
   - `LoginForm.vue`도 mount 시 `preferences.hydrate()`를 호출한다(로그인 페이지는 `app.vue` 셸 밖이라 지금은 hydrate가 호출되지 않는다).
3. **로그인 문구**
   - 새 key: `login.pageLabel`, `login.kicker`, `login.title`, `login.description`, `login.email`, `login.password`, `login.submit`, `login.submitting`, `login.error.invalidCredentials`, `login.error.locked`, `login.error.forbidden`, `login.error.unreachable`.
   - 안내 문구는 한 줄(`login.description`: "계정으로 로그인해 시작해 보세요.")만 남기고 `login-token-policy` 문단은 삭제한다.
   - 오류 문구는 `stores/auth.ts`의 `loginErrorMessage`가 `usePreferencesStore().t(...)`로 만든다. 기본 로케일(en-US)의 영어 문구는 기존과 글자까지 같게 유지해 `auth-api-client.test.ts`가 그대로 통과한다. 세션 목록 오류(`sessionErrorMessage`)는 범위 밖이다.
4. **placeholder 값**
   - `editor.channelDescription`: 채널에 topic 데이터가 없다. `ChatViewport.vue`의 `<p>` 한 줄을 삭제하고 key를 제거한다.
   - `editor.hint`: `app.vue`의 `<small>`(editor titlebar)과 `<p data-testid="editor-empty-hint">` 두 줄을 삭제하고 key를 제거한다. 이 test id를 쓰는 단위 테스트는 요소가 없다는 단언으로 바꾼다.
   - `voice.sfuSkeleton`: en "Voice channel", ko "음성 채널". key 이름은 `VoicePanel.vue`(T220)와의 충돌을 피하려고 유지한다.
   - `banner.register`: 버튼이 남아 있으므로 key 유지. ko "회원가입".
5. **번역 검토 원칙**
   - 문장은 해요체, 라벨은 명사형으로 통일한다. 번역투(예: "~할 수 있습니다", "큐", "워크벤치", "셸 컨텍스트", "길드", "음성 룸")를 쓰지 않는다.
   - 용어: 서버(guild), 채널, 메시지, 멤버, 알림함(inbox), 음성 채널(voice room, 활동 막대의 "통화" 항목 포함). 앱 이름(brand)은 번역하지 않고 en/ko 모두 "Discord Clone"으로 통일한다(`app.title`, 로그인 제목, 페이지 title과 동일). "워크스페이스"는 앱 전체를 가리키는 일반 명사로만 쓰고, 서버는 항상 "서버"/"Server"다(`view.settings.guild`).
   - 값 변경은 task report에 전후 표로 남긴다.

## Expected Changed Files
- `docs/03-analysis/task_T222-login-locale-copy.blueprint.md` (이 문서)
- `apps/web/components/auth/LoginForm.vue`
- `apps/web/pages/login.vue` (`aria-label`을 locale key로)
- `apps/web/middleware/auth.global.ts`
- `apps/web/stores/preferences.ts`
- `apps/web/stores/auth.ts` (로그인 오류 문구만)
- `apps/web/locales/en-US.json`, `apps/web/locales/ko-KR.json`
- `apps/web/components/shell/ChatViewport.vue` (`editor.channelDescription` 줄 삭제)
- `apps/web/pages/app.vue` (`editor.hint` 두 줄 삭제)
- 테스트
  - `apps/web/tests/components/login-form.test.ts`
  - `apps/web/tests/components/auth-middleware.test.ts`
  - `apps/web/tests/components/preferences-locale.test.ts` (신규)
  - `apps/web/tests/components/app-shell.test.ts` (`editor-empty-hint` 단언 한 곳)
  - `apps/web/tests/e2e/login.spec.ts`, `apps/web/tests/e2e/real-backend.spec.ts`

## 검증 계획
controller가 실행한다.
- `npm run lint`
- `cd apps/web && npx vitest run`
- `npm run build --workspace @discord-clone/web`
- `git diff --check origin/main HEAD`
- production build 대상 Playwright 스크린샷 투어(1440x900, 390x844): 브라우저 언어 `ko-KR`/`en-US` 각각에서 로그인 화면 문구, 로그인 후 `/` 이동, 인증 상태에서 `/login` 재방문 시 이동, `<html lang>` 값을 확인한다.

implementer가 실행하는 focused 테스트:
`cd apps/web && npx vitest run tests/components/login-form.test.ts tests/components/auth-middleware.test.ts tests/components/preferences-locale.test.ts tests/components/auth-api-client.test.ts`

## 위험
- 로케일 감지가 mount 이후라 SSR HTML은 영어이고 한국어 브라우저에서는 짧게 영어가 깜박인다(허용됨).
- `app.vue`와 `ChatViewport.vue`의 삭제 줄이 T219/T220의 인접 변경과 merge 충돌할 수 있다. 한 줄 단위 삭제로 최소화했다.
- 로그인 오류 문구가 locale을 따르게 되면서, 오류가 표시된 뒤 언어를 바꿔도 이미 만든 문구는 바뀌지 않는다(저장된 문자열이므로). 로그인 화면에는 언어 선택이 없어 실사용 영향은 작다.
- `editor.hint` 삭제로 `editor-empty-hint` 요소가 사라진다. 이 요소를 참조하는 e2e/CSS가 더 있으면 controller 검증에서 드러난다(CSS 규칙은 T219 영역이라 남겨 둔다).

## 후속 목록 (범위 밖, 번역되지 않은 영어)
작업 중 확인한 내용은 task report의 "후속 목록"에 정리한다.
