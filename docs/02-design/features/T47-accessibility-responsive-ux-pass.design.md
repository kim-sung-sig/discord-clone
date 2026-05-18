# T47 Accessibility & Responsive UX Pass Design

Date: 2026-05-18
Slice: T47 Accessibility & Responsive UX Pass

## Loop Output

계획 검토 됨 > 구현 계획 수립 됨 (주요토픽 안내, 주요 변경 사항 안내) > 구현 진행 함 > 리뷰 진행 > 리뷰 검토 (6개의 지표를 통해 점수를 매김) > 기준점 통과 시 다음 계획 진행, 미통과 시 개선안을 정리하여 구현 사이클 반복

## Design

The app page gains a skip link before the shell. The workspace section becomes `id="workspace-content"` and `tabindex="-1"` so the link has a valid keyboard focus destination.

The invite modal keeps the existing `role="dialog"` and initial focus, then adds a `keydown` handler for Tab. The handler computes focusable controls inside the dialog and wraps focus from the last element to the first, and from the first to the last for Shift+Tab.

The first test slice covers behavior that can be asserted reliably in component tests:

- Skip link exists and points to the workspace target.
- Workspace is focusable.
- Invite modal wraps focus on Tab.
- Existing composer accessible labels remain present.

## Follow-Up

Mobile drawer focus management, visual screenshot regression, and color contrast automation should be added after this first keyboard foundation.
