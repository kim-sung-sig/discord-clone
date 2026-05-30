# T147 Docker Compose Resource Topology Alignment Analysis

Date: 2026-05-20
Slice: T147 Docker Compose Resource Topology Alignment

## Implementation Notes

- Added failing contract assertions before editing Compose.
- Renamed Compose services to match central resource naming.
- Updated Postgres, Redis, and Kafka host ports.
- Added Redis password requirement and password-aware healthcheck.
- Validated normalized Compose output with Docker.

## Feature Impact

- Developers can start local central resources from this repo using the same endpoints already used by backend and web configuration.
- Centralization tasks can now target one topology instead of switching between `5432/6379/9092` and `15432/16379/29092`.

## Remaining Gaps

- Compose does not yet include an automated two-node backend Kafka fanout smoke.
- Compose drift against the external reference compose is still tracked separately by T149.
