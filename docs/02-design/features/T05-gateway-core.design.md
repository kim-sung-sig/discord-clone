# T05 Gateway Core Design

작성일: 2026-05-13  
PDCA Phase: Design  
Slice: T05 gateway core

## Backend Design

### Module

- Add `backend/modules/gateway`.
- `GatewaySession` stores session id, user id, guild ids, last ack, closed flag, and last delivered sequence.
- `GatewayEvent` stores sequence, type, guild id, optional channel id, payload, createdAt.
- `InMemoryGatewayService` owns identify, heartbeat, timeout scan, publish, poll, and resume.
- Sequence is global monotonic in this slice; cross-node sequencing is deferred.

### Permission Boundary

- Gateway event delivery calls `InMemoryGuildService` before returning channel-scoped events.
- Channel events require `VIEW_CHANNEL`.
- Guild events require guild membership or owner.
- Message events reuse channel filtering so hidden channel messages are not delivered.

### Boot API Adapter

This slice uses HTTP endpoints as a testable gateway adapter before real WebSocket transport:

| Method | Path | Auth |
| --- | --- | --- |
| `POST` | `/api/gateway/identify` | bearer |
| `POST` | `/api/gateway/sessions/{sessionId}/heartbeat` | bearer/session owner |
| `POST` | `/api/gateway/sessions/{sessionId}/resume` | bearer/session owner |
| `GET` | `/api/gateway/sessions/{sessionId}/events?afterSeq=` | bearer/session owner |
| `POST` | `/api/gateway/events` | bearer, internal-test adapter for T05 |

## Frontend Design

- Add gateway state to `shell.ts` or a focused gateway store if the existing shell gets too large.
- Render a small gateway status panel in the workspace or user panel.
- Store handles `READY`, `HEARTBEAT_ACK`, `RESUMED`, and event append with duplicate sequence guard.

## QA Design

- Backend service tests cover heartbeat timeout, sequence monotonicity, duplicate resume filtering, unauthorized channel filtering.
- MockMvc tests cover identify/resume/heartbeat/session owner checks.
- Frontend component tests cover gateway status panel and duplicate event guard.
- E2E smoke verifies gateway status appears in shell.
