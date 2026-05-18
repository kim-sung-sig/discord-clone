# T49 Server Events & Scheduling Design

Date: 2026-05-18
Slice: T49 Server Events & Scheduling

## Loop Output

계획 검토 됨 > 구현 계획 수립 됨 (주요토픽 안내, 주요 변경 사항 안내) > 구현 진행 함 > 리뷰 진행 > 리뷰 검토 (6개의 지표를 통해 점수를 매김) > 기준점 통과 시 다음 계획 진행, 미통과 시 개선안을 정리하여 구현 사이클 반복

## Design

The event module owns immutable `ServerEvent` records and an in-memory service. Time is represented as `Instant`, and invalid start/end ordering is rejected.

Visibility is caller-context driven: `visibleEvents(guildId, visibleChannelIds)` returns only events linked to visible channels. This mirrors the permission boundary used by message search and keeps hidden stage/voice events out of general listings.

Lifecycle side effects are represented as `ServerEventSignal` candidates. Later integration can route these to notification and audit modules.
