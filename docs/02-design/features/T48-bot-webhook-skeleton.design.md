# T48 Bot & Webhook Skeleton Design

Date: 2026-05-18
Slice: T48 Bot & Webhook Skeleton

## Loop Output

계획 검토 됨 > 구현 계획 수립 됨 (주요토픽 안내, 주요 변경 사항 안내) > 구현 진행 함 > 리뷰 진행 > 리뷰 검토 (6개의 지표를 통해 점수를 매김) > 기준점 통과 시 다음 계획 진행, 미통과 시 개선안을 정리하여 구현 사이클 반복

## Design

The bot module is a standalone in-memory domain module. It owns:

- `Webhook`: public metadata without token.
- `CreatedWebhook`: metadata plus one-time plaintext token.
- `WebhookMessage`: send result with source marker.
- `WebhookAuditEvent`: audit candidate event.

`InMemoryWebhookService` hashes generated tokens with SHA-256 before storage. Later reads return `Webhook`, never `CreatedWebhook`. Sends require the plaintext token and a caller-provided channel permission boolean. This lets API-layer permission integration arrive later without weakening the domain contract.
