# T220 실제 workspace 데이터 Blueprint

## 승인 상태
- 상태: UI polish 계획(`.superpowers/sdd/plan/task-2-brief.md`, global constraints)으로 승인됨. base `origin/main` `ecf24e3`.
- 사용자 요청: 운영 빌드 화면 점검에서 드러난 데모 데이터 노출, 메시지 순서, hydration 불일치, read marker 400을 고친다. 실제 로그인 세션에서는 데모 fixture(`vibe-coder`, `cto-bot`, `gateway-session-t05`, sequence 42, "Add qa-scout", "Privileged audit", "Provider LIVEKIT_SKELETON")가 보이지 않아야 한다.
- 근거: `C:\tmp\review-extract\ui-polish\ui-fix-map.md`의 B, D, I2, I3, H(VoicePanel provider 줄). 화면: `C:\tmp\review-extract\ui-review\shots-prod\04-app-home.png`, `12-explorer.png`, `13-bottom-panel.png`.
- 막힌 모호성: 없음. 판단이 필요한 항목은 아래 "설계 결정"에 기록한다.

## 목표
1. shell store 기본 state에 데모 fixture가 없다. `currentUser`는 `'vibe-coder'`가 아니다. `resetAccountState()`는 깨끗한 state를 만든다.
2. 해당 slice를 쓰는 컴포넌트는 실제 값을 보여 주거나, 비어 있는 section을 숨긴다.
3. 활성 채널 메시지는 위가 오래된 것, 아래가 최신이다. 처음 불러온 page, 나중에 불러온 이전 page, 보낸 메시지(optimistic·확정), Gateway 수신 메시지 모두 같다. 채널을 바꾸거나 메시지를 보내면 목록이 맨 아래로 scroll된다.
4. SSR과 첫 client render가 같다. 기본 state가 더 이상 `# general`을 그리지 않는다.
5. read marker(`PUT /api/presence/channels/{id}/read`)는 서버가 확정한 sequence로만 보낸다.

## 범위와 범위 밖
- 범위
  - `apps/web/stores/shell.ts`: 기본 state 비우기, `resetAccountState` 단순화, 메시지 정렬 getter, 서버 확정 sequence 표식, read marker 후보 getter.
  - 컴포넌트: `ChatViewport`, `MemberSidebar`, `GatewayStatusPanel`, `RolePermissionPanel`, `VoicePanel`, `DmSidebar`.
  - 데모 fixture를 test 전용 seed(`apps/web/tests/fixtures/shell-demo-state.ts`)로 옮기고, 기본값에 기대던 unit test가 명시적으로 seed하도록 바꾼다.
- 범위 밖
  - 메시지 작성자 표시 이름(T221). `shell.ts`의 `author:` 매핑 줄은 건드리지 않는다.
  - layout CSS와 `pages/app.vue`(T219). 숨겨진 legacy section 제거, status bar(`READY {lastSequence}`) 문구도 T219/T222 소관이다.
  - locale 파일(T222). 새 locale key를 추가하지 않는다. 빈 section은 숨긴다.
  - `InviteModal`, `ForumPanel`, `ModerationPanel`, `ExperiencePanel`의 표시 변경(`UserPanel`은 fix round 1에서 hydration 때문에 범위에 넣었다). 이 컴포넌트들은 store 기본값이 비면 빈 값을 그린다. `InviteModal`과 legacy section은 T219가 다룬다.
  - Gateway 실제 연결(`services/gateway-client.ts`는 UI에서 연결되지 않는다). 패널은 연결되지 않은 실제 상태를 보여 준다.

## 설계 결정
1. **기본 state는 빈 세션이다.**
   - `guild {id:'', name:''}`, `activeChannelId ''`, `channelGroups []`, `messages []`.
   - `members []`, `roles []`, `permissionOverwrites []`, `adminConsole {previewRoleId:'', permissionDraft:null}`.
   - `invitePreview`: 빈 문자열과 0, 빈 배열.
   - `gateway {status:'DISCONNECTED', sessionId:'', lastSequence:0, lastHeartbeatAckAt:'', resumed:false, events:[]}`, `latestGatewaySequence 0`, `processedEventIds []`.
   - `social`: 선택 없음(`{type:'DIRECT', id:''}`), friends·DM·group DM 없음.
   - `forum`: 선택 없음, forum·thread 없음. `postError`의 'Ready'는 UI 상태 문구라 유지한다.
   - `moderation`: 빈 onboarding 질문, AutoMod 규칙·audit log 없음. `assignedRoleName`, `decision`은 UI 상태 문구라 유지한다.
   - `voice`: 참가자·event 없음. `tokenProvider` 타입은 `'LIVEKIT_SKELETON'` literal이라 값은 그대로 두고 화면에서 숨긴다.
   - `experience`: catalog·quest 데모 항목(`Skeleton entitlement`)을 뺀다.
   - `presence {nowMs:0, users:{}, typingByChannel:{}, readMarkers:{}}`, `currentUser ''`.
   - `expressions`(wave, shipit, approved)는 메시지 반응에 쓰는 기본 이모지 catalog라 유지한다.
2. **`resetAccountState()`**는 `$reset()`과 `accountGeneration` 증가만 한다. 기본값이 이미 비어 있으므로 따로 지우던 4개 필드 대입은 필요 없다.
3. **데모 fixture는 test 전용이다.** `tests/fixtures/shell-demo-state.ts`가 `createShellDemoState()`(기존 기본값 그대로)와 `seedShellDemoState(shell?)`(`$reset()` 후 `$patch`)를 export한다. `app-shell.test.ts`, `shell-contracts.test.ts`는 `beforeEach`에서 seed하고, test 중간의 `shell.$reset()`도 `seedShellDemoState(shell)`로 바꾼다. 그래서 기존 단언은 약해지지 않는다. vitest `include`가 `tests/components/**/*.test.ts`라 fixture 파일은 test로 실행되지 않는다. story 파일은 store를 seed하지 않으므로 바꾸지 않는다.
4. **컴포넌트**
   - `MemberSidebar`: member 목록이 있으면 그대로 그린다. 없으면 로그인 사용자 한 줄(`auth.user.displayName`, 없으면 `username`)과 `Online` badge를 그린다. 이 줄은 mount 뒤에만 그린다. SSR에는 auth가 없어서 hydration 불일치가 생기기 때문이다.
   - `DmSidebar`: "Add qa-scout" 버튼과 관련 computed를 없앤다(`data-testid="add-group-member"` 제거). friend·group DM이 없으면 해당 section을 숨긴다. store action `addGroupDmMember`는 유지한다.
   - `GatewayStatusPanel`: 상태(`DISCONNECTED`)는 그대로 보여 준다. session id가 없으면 session·sequence·heartbeat·resume grid를 숨기고, event가 없으면 event log를 숨긴다.
   - `RolePermissionPanel`: role이 없으면 "Preview as role", "Roles" section을 숨긴다. member role, active channel overwrite, privileged audit section은 항목이 있을 때만 그린다.
   - `VoicePanel`: provider 줄(`data-testid="voice-token-provider"`)을 없앤다. voice event가 없으면 event section을 숨긴다.
5. **메시지 순서**
   - 서버 sequence 여부를 메시지에 남긴다. 서버가 sequence를 주지 않아 client가 번호를 붙인 메시지에는 `localSequence: true`를 붙인다(local 전송, optimistic, sequence 없는 REST·Gateway 응답). 서버 sequence가 있는 메시지에는 필드를 붙이지 않는다. 그래서 기존 `toEqual` 단언은 바뀌지 않는다. 이 필드는 `author:` 줄과 떨어진 위치(객체 끝)에 둔다(T221 병합 충돌 방지).
   - 비교 함수 `compareShellMessages`: 전송 중(`sending`)·실패(`failed`) 메시지는 맨 뒤. 둘 다 서버 sequence가 있으면 sequence 오름차순. 아니면 `createdAt` 오름차순, 같으면 sequence, id 순.
   - Gateway `MESSAGE_CREATE`에 `clientEventId`가 없을 때 기존 코드는 `candidate.clientEventId === clientEventId`가 `undefined === undefined`로 참이 되어 `clientEventId`가 없는 메시지(REST로 불러온 메시지 전부)를 지우고, 그중 첫 메시지를 `existing`으로 잘못 골랐다. 구현 중 test로 확인했다. echo 판정은 `clientEventId`가 있을 때만 하도록 고친다(`isEcho`). `author:` 줄은 건드리지 않는다.
   - `activeMessages` getter가 필터 결과를 이 함수로 정렬한다. 저장 배열 순서(REST page의 최신순, 이전 page 앞붙이기, 전송·Gateway 뒤붙이기)와 무관하게 화면 순서가 정해진다.
   - scroll: `ChatViewport`가 목록이 맨 아래 근처(48px)인지 추적한다. 채널을 바꾸거나 메시지를 보내면 맨 아래 고정을 켠다. 마지막 메시지가 바뀌었을 때 고정 상태면 맨 아래로 scroll한다. 이전 page를 불러오면 마지막 메시지가 그대로라 scroll되지 않는다. 사용자가 위로 올려 읽는 중에 새 메시지가 와도 끌어내리지 않는다.
6. **read marker**: `activeReadMarkerMessage` getter가 활성 채널에서 서버 sequence가 있는 메시지(전송 중·실패·`localSequence` 제외) 중 sequence가 가장 큰 것을 고른다. `ChatViewport`의 watcher는 이 getter만 쓴다. optimistic 메시지의 local sequence(서버 MAX보다 큼)로 PUT해 400이 나던 경로가 없어진다.
7. **hydration**: SSR과 client 모두 빈 기본 state에서 시작한다. client에서 `middleware/auth.global.ts`가 `restoreSession()` → `acceptSession()` → `resetAccountState()`를 불러도 결과가 같은 빈 state라 shell 영역의 첫 render가 같다. 실제 workspace는 mount 뒤 `bootstrapCurrentUserWorkspace`가 채운다. middleware는 바꾸지 않는다.
   - fix round 1: client middleware는 hydration 전에 session을 복원한다. 그래서 render 중 `auth.*`를 읽는 컴포넌트는 SSR(세션 없음)과 첫 client render가 달라진다. 이 task 범위의 컴포넌트는 auth 의존 출력을 mount 뒤로 미룬다.
     - `UserPanel`: 이름 줄(`auth.user?.displayName`)과 `session-panel`(`v-if="auth.accessToken"`)을 `isHydrated` 뒤에 그린다. mount 전에는 SSR과 같이 `shell.currentUser`(기본 `''`)를 그린다.
     - `ChatViewport`: "이전 메시지" 버튼(`auth.accessToken` 조건)은 mount 뒤에만 그린다(`composerReady`를 mounted flag로 쓴다).
     - `MemberSidebar`: 처음부터 mount 뒤에만 그린다.
     - test는 `vue/server-renderer`로 mount hook 없는 render를 두 번 한다(auth 비움, auth 채움). 두 결과의 HTML이 같은지 확인한다.
     - 남는 후보: `pages/app.vue`의 `real-api-panel`(`v-if="auth.accessToken"`)은 첫 화면에서 auth를 읽는다. T219가 legacy section과 함께 지우므로 이 task는 바꾸지 않는다. 같은 파일의 inbox section(`auth.accessToken`)은 inbox view에서만 그려져 첫 render에 들어가지 않는다.
8. **fix round 1 추가 결정**
   - 이전 page를 불러올 때 scroll 위치를 보존한다. load 전 `scrollHeight`와 `scrollTop`을 저장하고, load 뒤 `scrollTop = 이전 scrollTop + (새 scrollHeight - 이전 scrollHeight)`로 절대값을 대입한다. 절대값을 쓰므로 scroll anchoring을 지원하는 브라우저에서 이미 보정된 경우에도 두 번 더해지지 않는다(Safari는 `overflow-anchor`가 없다).
   - read marker watcher의 key는 선택된 `activeReadMarkerMessage`의 id, sequence, status, `localSequence`다. 같은 id·sequence의 메시지가 local에서 서버 확정으로 바뀌어도 다시 실행된다.
   - `DmSidebar`: 버튼을 지워 `h3` 하나만 남은 `.dm-section-heading` wrapper를 없앤다. CSS의 `.dm-section-heading`은 flex 정렬과 버튼 모양만 정의하므로 `h3` 단독에는 필요 없다.

## Expected Changed Files
- 문서
  - `docs/03-analysis/task_T220-real-workspace-data.blueprint.md`(이 문서)
  - merge 전 evidence `docs/03-analysis/task_T220-merge-review.md`(controller)
- 운영 코드
  - `apps/web/stores/shell.ts`
  - `apps/web/components/shell/ChatViewport.vue`
  - `apps/web/components/shell/MemberSidebar.vue`
  - `apps/web/components/shell/GatewayStatusPanel.vue`
  - `apps/web/components/shell/RolePermissionPanel.vue`
  - `apps/web/components/shell/VoicePanel.vue`
  - `apps/web/components/social/DmSidebar.vue`
  - `apps/web/components/shell/UserPanel.vue`(fix round 1, hydration)
  - `apps/web/middleware/auth.global.ts`(검증 결과 필요할 때만. 현재 계획은 변경 없음)
- test
  - `apps/web/tests/fixtures/shell-demo-state.ts`(신규)
  - `apps/web/tests/components/shell-real-workspace.test.ts`(신규)
  - `apps/web/tests/components/app-shell.test.ts`, `apps/web/tests/components/shell-contracts.test.ts`(명시적 seed, 바뀐 화면 단언)
  - `apps/web/tests/e2e/app-shell.spec.ts`, `apps/web/tests/e2e/real-backend.spec.ts`, `apps/web/tests/e2e/pwa-mobile.spec.ts`, `apps/web/tests/e2e/visual-smoke.spec.ts`, `apps/web/tests/e2e/t62-screenshot-report.spec.ts`(없어진 요소·데모 기본값 단언 정리. CI 미실행)

## 검증 계획
- implementer: `cd apps/web && npx vitest run tests/components/shell-real-workspace.test.ts tests/components/app-shell.test.ts tests/components/shell-contracts.test.ts tests/components/story-index.test.ts`
- controller
  - `npm run lint`
  - `cd apps/web && npx vitest run`
  - `npm run build --workspace @discord-clone/web`
  - `git diff --check origin/main HEAD`
  - 운영 build를 실제 backend에 붙인 Playwright 화면 점검(1440x900, 390x844): 로그인 뒤 데모 이름·session id·sequence 42·"Add qa-scout"·"Privileged audit"·provider 줄이 없는지, 메시지가 오래된 것부터 위에서 아래로 쌓이는지, 콘솔에 hydration 경고와 read marker 400이 없는지 확인한다.

## 위험
- **병합 충돌**: T221이 `shell.ts`의 `author:` 매핑 줄을 바꾼다. 이 task는 그 줄과 바로 붙은 줄을 바꾸지 않는다. `localSequence`는 객체 끝에 둔다. T219가 `pages/app.vue`를 바꾸므로 이 task는 `app.vue`를 바꾸지 않는다.
- **`app.vue`가 직접 읽는 값**: status bar가 `READY {gateway.lastSequence}`를 그리므로 기본값에서는 `READY 0`이 된다. 문구 정리는 T219/T222 소관이다.
- **legacy 컴포넌트**: `InviteModal`, `ForumPanel`, `ModerationPanel`, `ExperiencePanel`은 기본값이 비면 빈 값을 그린다. T219가 legacy section을 숨기기 전에는 빈 카드가 보일 수 있다.
- **남은 hydration 후보**: `pages/app.vue`의 `real-api-panel`(`v-if="auth.accessToken"`)은 T219가 지운다. T220 단독 branch에서는 이 노드 때문에 경고가 남을 수 있다.
- **비교 함수의 일관성**: 서버 sequence와 `createdAt`이 서로 어긋나는 데이터에서는 섞인 비교가 전순서가 아닐 수 있다. 서버는 sequence를 항상 주고(`MessageController.MessageResponse.from(message, sequence)`), sequence가 없는 메시지는 예외 경로라 실제 영향은 작다.
- **e2e spec**: 데모 기본값에 기대던 spec 15개(`app-shell.spec.ts` 13개, `visual-smoke`, `t62-screenshot-report`)는 `test.fixme`로 표시하고, 각 test 위에 한 줄 이유를 단다. 없어진 요소 단언은 정리했다.
  - 대부분은 인증 없이 `/`에 들어가므로 main에서도 auth middleware 때문에 `/login`으로 이동해 실패하던 spec이다.
  - 예외: "searches messages and resolves a moderation report"는 `/api/auth/refresh`를 mock해 인증 상태로 데모 데이터 위에서 돌던 spec이다. 그래서 "이미 실패하던 spec"이라는 설명은 인증 mock이 없는 spec에만 맞다.
  - **후속 작업**(별도 task): 이 spec들을 "opens notification inbox" spec처럼 `page.route`로 auth·guild·message API를 mock해 seed한 workspace에서 돌도록 다시 쓴다("seed via page.route auth mock"). 이 작업이 끝나면 `test.fixme`를 푼다. CI는 e2e를 돌리지 않는다.
