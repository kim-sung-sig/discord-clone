# T223 병합 검토 기록

## 대상

- 작업 브랜치: `task_T223-csp-security-contrast`
- 기준 `origin/main` SHA: `ecf24e3b880e0c5cbd74a588be52c0f47f10a8ca`
- 검토한 내용 SHA: `068089d0cbcf7ef8dc894f6e9b224fea0904d323`
  - `b72986c`: 설계 문서
  - `f8f4601`: 개발 모드에서만 꾸밈 코드(`style-src 'unsafe-inline'`)를 허용한다. 배포 빌드는 컴포넌트 꾸밈을 `<style>` 태그로 끼워 넣지 않도록 바꿨다(`features.inlineStyles: false`).
  - `068089d`: 보안 화면 추세 막대의 인라인 꾸밈을 단계별 class로 바꾸고, 보안 화면 글자 대비를 고쳤다.
- 증거 기록: 이 문서만 바꾸는, 검토한 내용 commit의 바로 다음 commit이다.
- 설계 문서: `docs/03-analysis/task_T223-csp-security-contrast.blueprint.md`

## 배경

2026-10-24 화면 점검에서 두 가지가 나왔다.

- 개발 모드(`npm run dev`)로 띄우면 화면 꾸밈이 전부 빠졌다. 원인은 보안 정책 `style-src 'self'`가 개발 도구가 넣는 꾸밈 코드를 막는 것이었다.
- 배포 빌드에서도 꾸밈 차단 오류가 6건 났고, 보안 화면의 일부 글자는 거의 읽을 수 없었다.

사용자는 이 문제들을 독립 작업으로 나눠 고치라고 요청했다("각각 독립실행으로 개선"). 컴파일과 린트는 코디네이터만 실행했다.

## 진행 방식

- 오르카로 독립 작업 폴더(`origin/main` 기준)를 만들었다. 다른 화면 개선 작업 4개(T219~T222)도 같은 방식으로 나란히 진행했다.
- 구현 담당 1명이 설계 문서를 먼저 쓰고 구현했다. 테스트는 파일 단위로만 돌렸다.
- 코디네이터가 린트, 전체 단위 테스트, 배포 빌드, 실제 화면 캡처, 개발 서버 확인을 직접 실행했다.
- 새 검토자 2명(명세 준수, 코드 품질·보안)이 읽기 전용으로 검토했다.

## 독립 검토

| 차수 | 구분 | 대상 SHA | 점수 | P0/P1/P2/P3 | 판정 |
| --- | --- | --- | ---: | --- | --- |
| 1 | 명세 준수 | `068089d0cbcf7ef8dc894f6e9b224fea0904d323` | 95/100 | 0/0/0/3 | 통과 |
| 1 | 코드 품질·보안 | `068089d0cbcf7ef8dc894f6e9b224fea0904d323` | 93/100 | 0/0/0/4 | 통과 |

보안 검토의 결론:

- 배포 빌드가 `'unsafe-inline'`을 받는 경로는 없다. 판단은 빌드 시점의 `import.meta.dev` 하나로만 정해지고, 요청 헤더·환경 변수·요청 값은 영향을 주지 않는다.
- 스크립트 정책(`script-src`)과 nonce는 바뀌지 않았다.

## 검증 (`068089d`, Windows, 코디네이터 직접 실행)

| 명령 | 결과 |
| --- | --- |
| `npm run lint` | 성공 |
| `npx vitest run` (`apps/web`) | 166 통과, 8 건너뜀 |
| `npm run build --workspace @discord-clone/web` | 성공 |
| `git diff --check origin/main HEAD` | 성공 |
| 배포 빌드 응답 헤더 | `style-src 'self'`. `/login` HTML의 `<style>` 태그 0개 |
| 배포 번들 확인 | `styleSource((_a = options.development) != null ? _a : false)`. `import.meta.dev`가 `false`로 바뀌어 있다. |
| `nuxt dev` 응답 헤더 | `style-src 'self' 'unsafe-inline'`(개발 모드만) |
| 실제 화면 둘러보기(배포 빌드, 실제 백엔드) | 꾸밈 차단 오류가 6건에서 화면당 1건으로 줄었다. 남은 1건은 `pages/app.vue` 최상위 인라인 꾸밈이며 T219에서 고친다. 보안 화면의 "Memory ready", "Off", "Local dev open" 글자가 읽힌다. |

- 화면 캡처 위치: `C:\tmp\review-extract\ui-polish\shots-task_T223-csp-security-contrast\`
- 바뀐 글자·배경 쌍은 모두 WCAG AA(4.5:1) 이상이다. 가장 낮은 값은 4.65:1이다.

## 설계 문서와의 일치

- 일치 여부: 일치
- 어긋남: 없음

## 수용

- 설계 수용: 예
- 구현 수용: 예
- 검증 수용: 검토한 내용 기준으로 예
- 판단 기록: 구현 commit의 공동 작성자 표기는 실제 작성 모델(`Claude Sonnet 5.5`)이다. 계획 문서의 표기 예시와 다르지만 사실대로 둔다.

## 남은 위험·미룬 일 (모두 P3)

- 테마 CSS에서 같은 선택자의 색 지정이 두 번 있고, 앞의 것은 쓰이지 않는다(`discord-vscode-theme.css` 359~373행).
- `.storage-health-*` 규칙이 일부 겹친다(`main.css` 790~804행).
- "`import.meta.dev` 기본값" 테스트는 기대값을 같은 플래그에서 가져와서 어느 모드에서도 통과한다. 배포 보장은 `development: false` 테스트와 빌드 확인으로 대신한다.
- "Off" 표시의 바탕이 줄 배경과 대비가 낮아(약 1.5:1) 표시 테두리가 잘 보이지 않는다. 글자 자체는 7.24:1이다.
- 설계 문서의 승인 상태가 사용자 요청을 그대로 인용하지 않고 풀어 썼다.
- 새 색상은 테마 변수가 아니라 직접 쓴 색 값이다.
- 개발 서버를 외부에 열면 완화된 정책이 그대로 나간다. 개발 모드의 정의상 받아들인다.
